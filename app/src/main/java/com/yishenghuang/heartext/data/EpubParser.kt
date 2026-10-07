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
            val containerXml = readEntry(zip, containerEntry).toString(Charsets.UTF_8)
            val opfPath = attributes(containerXml)["full-path"]
                ?: error("Invalid EPUB: missing OPF path")

            val opfEntry = zip.getEntry(opfPath) ?: error("Invalid EPUB: missing OPF")
            val opfXml = readEntry(zip, opfEntry).toString(Charsets.UTF_8)
            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            val bookTitle = Regex("""<dc:title[^>]*>([^<]+)</dc:title>""", RegexOption.IGNORE_CASE)
                .find(opfXml)?.groupValues?.get(1)?.trim()
                ?: epubFile.nameWithoutExtension
            val author = Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""", RegexOption.IGNORE_CASE)
                .find(opfXml)?.groupValues?.get(1)?.trim()
                ?: "Unknown"

            val manifest = mutableMapOf<String, String>()
            Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opfXml).forEach { match ->
                val attrs = attributes(match.value)
                val id = attrs["id"]
                val href = attrs["href"]
                if (id != null && href != null) manifest[id] = href
            }

            val spineIds = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE)
                .findAll(opfXml)
                .mapNotNull { attributes(it.value)["idref"] }
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
                    ncxXml = readEntry(zip, entry).toString(Charsets.UTF_8),
                    opfDir = opfDir
                )
            } ?: emptyMap()

            require(spineIds.size <= 10_000) { "EPUB contains too many chapters" }
            var totalTextBytes = 0L
            val legacyChapterHrefs = mutableSetOf<String>()
            val chapters = spineIds.mapIndexedNotNull { index, id ->
                val href = manifest[id] ?: return@mapIndexedNotNull null
                val normalized = resolvePath(opfDir, href)
                val entry = findEntry(zip, normalized, href) ?: return@mapIndexedNotNull null
                val bytes = readEntry(zip, entry)
                totalTextBytes += bytes.size
                require(totalTextBytes <= 64L * 1024 * 1024) { "EPUB text is too large" }
                val html = bytes.toString(Charsets.UTF_8)
                if (htmlToPlainText(html, includeHead = true).length >= 40) legacyChapterHrefs += normalized
                val plain = htmlToPlainText(html)
                if (plain.isBlank()) return@mapIndexedNotNull null

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
                entry?.let { readEntry(zip, it) }
            }

            require(chapters.isNotEmpty()) { "Invalid EPUB: no readable chapters" }
            return EpubBook(
                title = bookTitle,
                author = author,
                chapters = preserveLegacyChapterIndices(chapters, legacyChapterHrefs),
                coverBytes = coverBytes
            )
        }
    }

    /** Retain short spine text without renumbering chapters saved by previous app versions. */
    private fun preserveLegacyChapterIndices(chapters: List<EpubChapter>, legacyHrefs: Set<String>): List<EpubChapter> {
        if (chapters.none { it.href in legacyHrefs }) return chapters
        val retained = ArrayList<EpubChapter>()
        val pending = StringBuilder()
        for (chapter in chapters) {
            if (chapter.href !in legacyHrefs) {
                pending.append(chapter.plainText).append("\n\n")
            } else {
                retained += chapter.copy(plainText = pending.toString() + chapter.plainText)
                pending.clear()
            }
        }
        if (pending.isNotBlank()) {
            val last = retained.last()
            retained[retained.lastIndex] = last.copy(plainText = last.plainText + "\n\n" + pending.toString().trim())
        }
        return retained
    }

    private fun attributes(tag: String): Map<String, String> =
        Regex("""([\w:-]+)\s*=\s*(['"])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
            .findAll(tag).associate { it.groupValues[1].lowercase(Locale.ROOT) to decodeEntities(it.groupValues[3]) }

    private fun readEntry(zip: ZipFile, entry: java.util.zip.ZipEntry): ByteArray {
        val limit = 8 * 1024 * 1024
        require(entry.size <= limit) { "EPUB entry is too large" }
        return zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "EPUB entry is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
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

    fun htmlToPlainText(html: String): String = htmlToPlainText(html, includeHead = false)

    private fun htmlToPlainText(html: String, includeHead: Boolean): String {
        val body = if (includeHead) html else html.replace(Regex("""<head\b[^>]*>[\s\S]*?</head>""", RegexOption.IGNORE_CASE), "")
        var text = body
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
        .replace(Regex("""&#(x[0-9a-f]+|\d+);""", RegexOption.IGNORE_CASE)) { m ->
            val raw = m.groupValues[1]
            val code = if (raw.startsWith("x", ignoreCase = true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
            if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF) {
                String(Character.toChars(code))
            } else m.value
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
