package com.yishenghuang.heartext.data

import java.io.File
import java.util.zip.ZipFile
import java.util.Locale

data class EpubChapter(
    val title: String,
    val href: String,
    val plainText: String
)

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
    val coverBytes: ByteArray? = null
)

object EpubParser {
    private val boilerplateTitle = Regex(
        """project\s+gutenberg|ebook\s+of\b|produced\s+by\b|start\s+of\s+(the\s+)?project|""" +
            """end\s+of\s+(the\s+)?project|transcriber|copyright|all\s+rights\s+reserved""",
        RegexOption.IGNORE_CASE
    )
    private val chapterLike = Regex(
        """^(chapter|part|book|canto|act|volume|contents|cover|titlepage)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(epubFile: File): EpubBook {
        ZipFile(epubFile).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: error("Invalid EPUB: missing container.xml")
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            val opfPath = Regex("""full-path="([^"]+)"""")
                .find(containerXml)
                ?.groupValues
                ?.get(1)
                ?: error("Invalid EPUB: missing OPF path")

            val opfEntry = zip.getEntry(opfPath) ?: error("Invalid EPUB: missing OPF")
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            val bookTitle = Regex("""<dc:title[^>]*>([^<]+)</dc:title>""", RegexOption.IGNORE_CASE)
                .find(opfXml)?.groupValues?.get(1)?.trim()
                ?: epubFile.nameWithoutExtension
            val author = Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""", RegexOption.IGNORE_CASE)
                .find(opfXml)?.groupValues?.get(1)?.trim()
                ?: "Unknown"

            val manifest = mutableMapOf<String, String>()
            Regex(
                """<item\b[^>]*id="([^"]+)"[^>]*href="([^"]+)"[^>]*/?>""",
                setOf(RegexOption.IGNORE_CASE)
            ).findAll(opfXml).forEach { match ->
                manifest[match.groupValues[1]] = match.groupValues[2]
            }
            Regex(
                """<item\b[^>]*href="([^"]+)"[^>]*id="([^"]+)"[^>]*/?>""",
                setOf(RegexOption.IGNORE_CASE)
            ).findAll(opfXml).forEach { match ->
                manifest[match.groupValues[2]] = match.groupValues[1]
            }

            val spineIds = Regex("""<itemref\b[^>]*idref="([^"]+)"""", RegexOption.IGNORE_CASE)
                .findAll(opfXml)
                .map { it.groupValues[1] }
                .toList()

            val ncxHref = Regex(
                """<item\b[^>]*(?:media-type="application/x-dtbncx\+xml"[^>]*href="([^"]+)"|href="([^"]+)"[^>]*media-type="application/x-dtbncx\+xml")[^>]*/?>""",
                RegexOption.IGNORE_CASE
            ).find(opfXml)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }
                ?: manifest.values.find { it.lowercase(Locale.US).endsWith(".ncx") }

            val ncxTitles = ncxHref?.let { href ->
                val fullPath = resolvePath(opfDir, href)
                val entry = findEntry(zip, fullPath, href) ?: return@let emptyMap()
                parseNcxTitles(
                    ncxXml = zip.getInputStream(entry).bufferedReader().readText(),
                    opfDir = opfDir
                )
            } ?: emptyMap()

            val chapters = spineIds.mapIndexedNotNull { index, id ->
                val href = manifest[id] ?: return@mapIndexedNotNull null
                val normalized = resolvePath(opfDir, href)
                val entry = findEntry(zip, normalized, href) ?: return@mapIndexedNotNull null
                val html = zip.getInputStream(entry).bufferedReader().readText()
                val plain = htmlToPlainText(html)
                if (plain.isBlank() || plain.length < 40) return@mapIndexedNotNull null

                val heading = firstHeading(html)
                val htmlTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.trim()
                val ncxTitle = lookupNcxTitle(ncxTitles, normalized, href)

                val chapterTitle = pickChapterTitle(
                    bookTitle = bookTitle,
                    ncxTitle = ncxTitle,
                    heading = heading,
                    htmlTitle = htmlTitle,
                    fallbackIndex = index
                )
                EpubChapter(title = chapterTitle, href = normalized, plainText = plain)
            }

            val coverHref = Regex(
                """<meta\b[^>]*name="cover"[^>]*content="([^"]+)"""",
                RegexOption.IGNORE_CASE
            ).find(opfXml)?.groupValues?.get(1)?.let { manifest[it] }
                ?: manifest.values.find { it.contains("cover", ignoreCase = true) }

            val coverBytes = coverHref?.let { href ->
                val fullPath = resolvePath(opfDir, href)
                val entry = findEntry(zip, fullPath, href)
                entry?.let { zip.getInputStream(it).readBytes() }
            }

            return EpubBook(
                title = bookTitle,
                author = author,
                chapters = chapters.ifEmpty {
                    listOf(
                        EpubChapter(
                            title = "Content",
                            href = "",
                            plainText = "Unable to extract chapters from this EPUB."
                        )
                    )
                },
                coverBytes = coverBytes
            )
        }
    }

    private fun resolvePath(opfDir: String, href: String): String {
        val cleaned = href.substringBefore('#').replace("\\", "/").replace("./", "")
        return if (opfDir.isEmpty()) cleaned else "$opfDir/$cleaned".replace("//", "/")
    }

    private fun findEntry(zip: ZipFile, normalized: String, href: String) =
        zip.getEntry(normalized)
            ?: zip.entries().asSequence().find {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.name.endsWith(href.substringBefore('#'))
            }

    /** Map normalized path (and filename) → NCX label. */
    private fun parseNcxTitles(ncxXml: String, opfDir: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val navPoints = Regex(
            """<navPoint\b[\s\S]*?</navPoint>""",
            RegexOption.IGNORE_CASE
        ).findAll(ncxXml)
        for (nav in navPoints) {
            val block = nav.value
            val label = Regex("""<text[^>]*>([^<]+)</text>""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim() ?: continue
            val src = Regex("""<content\b[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim() ?: continue
            val path = resolvePath(opfDir, src)
            val key = path.lowercase(Locale.US)
            val prev = out[key]
            if (prev == null || titleScore(label) > titleScore(prev)) {
                out[key] = label
            }
            val fileName = path.substringAfterLast('/').lowercase(Locale.US)
            if (fileName.isNotBlank()) {
                val prevFile = out[fileName]
                if (prevFile == null || titleScore(label) > titleScore(prevFile)) {
                    out[fileName] = label
                }
            }
        }
        return out
    }

    private fun titleScore(label: String): Int {
        val l = label.trim().lowercase(Locale.US)
        return when {
            boilerplateTitle.containsMatchIn(label) -> 0
            l.startsWith("chapter") -> 50
            l.startsWith("act") || l.startsWith("canto") -> 45
            l.startsWith("part") || l.startsWith("book ") || l == "book" -> 40
            l.startsWith("volume") -> 25
            chapterLike.containsMatchIn(label) -> 20
            label.length in 2..80 -> 10
            else -> 0
        }
    }

    private fun lookupNcxTitle(map: Map<String, String>, normalized: String, href: String): String? {
        val key = normalized.lowercase(Locale.US)
        map[key]?.let { return it }
        val file = normalized.substringAfterLast('/').lowercase(Locale.US)
        map[file]?.let { return it }
        val hrefFile = href.substringBefore('#').substringAfterLast('/').lowercase(Locale.US)
        return map[hrefFile]
    }

    private fun firstHeading(html: String): String? {
        val match = Regex(
            """<h([1-3])[^>]*>([\s\S]*?)</h\1>""",
            RegexOption.IGNORE_CASE
        ).find(html) ?: return null
        return stripTags(match.groupValues[2]).trim().takeIf { it.isNotBlank() }
    }

    private fun pickChapterTitle(
        bookTitle: String,
        ncxTitle: String?,
        heading: String?,
        htmlTitle: String?,
        fallbackIndex: Int
    ): String {
        val candidates = listOfNotNull(ncxTitle, heading, htmlTitle)
        for (cand in candidates) {
            val cleaned = cand.replace(Regex("""\s+"""), " ").trim()
            if (isUsefulTitle(cleaned, bookTitle)) return cleaned.take(120)
        }
        // Prefer CHAPTER-like line from nowhere — last resort numbered
        return "Chapter ${fallbackIndex + 1}"
    }

    private fun isUsefulTitle(title: String, bookTitle: String?): Boolean {
        if (title.length < 2 || title.length > 120) return false
        if (boilerplateTitle.containsMatchIn(title)) return false
        if (title.contains('|') && boilerplateTitle.containsMatchIn(title.substringAfter('|'))) {
            return false
        }
        val bt = bookTitle?.trim()?.lowercase(Locale.US)
        if (!bt.isNullOrBlank() && title.equals(bookTitle, ignoreCase = true)) return false
        // "Emma | Project Gutenberg" already caught; also "Title | Something"
        if (title.contains('|') && bt != null &&
            title.substringBefore('|').trim().equals(bookTitle, ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    fun htmlToPlainText(html: String): String {
        var text = html
            .replace(Regex("""<(script|style|nav)[^>]*>[\s\S]*?</\1>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<(br|BR)\s*/?>"""), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
        text = decodeEntities(text)
        return reflowSoftBreaks(text)
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun stripTags(s: String): String = s.replace(Regex("""<[^>]+>"""), "")

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""&#(\d+);""")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }

    /**
     * Join soft-wrapped lines inside a paragraph (common in Gutenberg HTML → text dumps).
     */
    private fun reflowSoftBreaks(text: String): String {
        val lines = text.split('\n').map { it.replace(Regex("""[ \t]+"""), " ").trim() }
        if (lines.isEmpty()) return ""
        val paras = ArrayList<String>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                paras += buf.toString().trim()
                buf.clear()
            }
        }
        for (line in lines) {
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (buf.isEmpty()) {
                buf.append(line)
                continue
            }
            val prev = buf.toString()
            val joinSoft = when {
                prev.endsWith('-') && line.firstOrNull()?.isLowerCase() == true -> {
                    buf.deleteCharAt(buf.lastIndex)
                    buf.append(line)
                    true
                }
                prev.lastOrNull()?.let { it.isLowerCase() || it == ',' || it == ';' || it == ':' } == true &&
                    line.firstOrNull()?.isLowerCase() == true -> {
                    buf.append(' ').append(line)
                    true
                }
                prev.length < 50 && prev.lastOrNull()?.let { it !in setOf('.', '!', '?', '…') } == true &&
                    line.firstOrNull()?.isLowerCase() == true -> {
                    buf.append(' ').append(line)
                    true
                }
                else -> false
            }
            if (!joinSoft) {
                flush()
                buf.append(line)
            }
        }
        flush()
        return paras.joinToString("\n\n")
    }
}
