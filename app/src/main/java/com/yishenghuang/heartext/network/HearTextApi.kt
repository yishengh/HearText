package com.yishenghuang.heartext.network

import com.yishenghuang.heartext.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class HearTextApi(
    private val tokenProvider: SessionTokenProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
    endpoint: String = BuildConfig.API_BASE_URL
) {
    private val retryAfterGate = RetryAfterGate()

    private suspend fun <T> okhttp3.Call.consumeApiResponse(consume: (okhttp3.Response) -> T): T {
        currentCoroutineContext().ensureActive()
        retryAfterGate.check()
        return consumeCancellable { response ->
            val retryAfter = retryAfterGate.record(response.code, response.header("Retry-After"))
            if (response.code == 429 || response.code == 503) {
                throw ApiHttpException(response.code, "", retryAfter)
            }
            consume(response)
        }
    }

    private val baseUrl: String = endpoint.trimEnd('/')

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank()

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/health").get().build()
        client.newCall(request).consumeCancellable { it.isSuccessful }
    }

    suspend fun me(): ApiUser = withContext(Dispatchers.IO) {
        parseUser(authorizedJson("GET", "/v1/me"))
    }

    suspend fun updateMe(
        displayName: String? = null,
        avatarUrl: String? = null,
        username: String? = null
    ): ApiUser = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        if (displayName != null) payload.put("display_name", displayName)
        if (avatarUrl != null) payload.put("avatar_url", avatarUrl)
        if (username != null) payload.put("username", username)
        parseUser(authorizedJson("PATCH", "/v1/me", payload.toString()))
    }

    suspend fun deleteMe() = withContext(Dispatchers.IO) {
        authorizedRaw("DELETE", "/v1/me")
        Unit
    }

    suspend fun listBooks(): List<ApiBook> = withContext(Dispatchers.IO) {
        parseBookList(authorizedRaw("GET", "/v1/books"))
    }

    suspend fun createBook(
        clientBookId: String,
        title: String,
        author: String,
        format: String,
        coverUrl: String? = null,
        sortOrder: Int = 0
    ): ApiBook = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("client_book_id", clientBookId)
            .put("title", title)
            .put("author", author)
            .put("cover_url", coverUrl)
            .put("format", format.lowercase())
            .put("sort_order", sortOrder)
            .put("meta", JSONObject())
        parseBook(authorizedJson("POST", "/v1/books", payload.toString()))
    }

    suspend fun updateBook(bookId: String, patch: JSONObject): ApiBook = withContext(Dispatchers.IO) {
        parseBook(authorizedJson("PATCH", "/v1/books/$bookId", patch.toString()))
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        authorizedRaw("DELETE", "/v1/books/$bookId")
        Unit
    }

    suspend fun listProgress(): List<ApiProgress> = withContext(Dispatchers.IO) {
        parseProgressList(authorizedRaw("GET", "/v1/progress"))
    }

    suspend fun getProgress(bookId: String): ApiProgress? = withContext(Dispatchers.IO) {
        try {
            parseProgress(authorizedJson("GET", "/v1/books/$bookId/progress"), bookId)
        } catch (e: ApiHttpException) {
            if (e.code == 404) null else throw e
        }
    }

    suspend fun putProgress(
        bookId: String,
        chapterId: String?,
        chapterIndex: Int,
        position: Int,
        percentage: Double,
        clientUpdatedAtIso: String
    ): ApiProgress = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("chapter_id", chapterId)
            .put("chapter_index", chapterIndex)
            .put("position", position)
            .put("percentage", percentage)
            .put("extras", JSONObject())
            .put("client_updated_at", clientUpdatedAtIso)
        parseProgress(authorizedJson("PUT", "/v1/books/$bookId/progress", payload.toString()), bookId)
    }

    // --- Catalog ---

    suspend fun listCatalog(
        q: String? = null,
        language: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): ApiCatalogList = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/catalog".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", pageSize.coerceIn(1, 100).toString())
            .apply {
                if (!q.isNullOrBlank()) addQueryParameter("q", q)
                if (!language.isNullOrBlank()) addQueryParameter("language", language)
                if (!category.isNullOrBlank()) addQueryParameter("category", category)
            }
            .build()
        parseCatalogList(authorizedRawUrl("GET", url.toString()))
    }

    suspend fun listCatalogCategories(): List<ApiCatalogCategory> = withContext(Dispatchers.IO) {
        parseCategoryList(authorizedRaw("GET", "/v1/catalog/categories"))
    }

    suspend fun listFeaturedCatalog(): List<ApiCatalogBook> = withContext(Dispatchers.IO) {
        parseCatalogBookArray(authorizedRaw("GET", "/v1/catalog/featured"))
    }

    suspend fun listCatalogRankings(metric: String = "shelves"): List<ApiCatalogBook> =
        withContext(Dispatchers.IO) {
            val path = "/v1/catalog/rankings?metric=${metric.encodeUrl()}"
            parseCatalogBookArray(authorizedRaw("GET", path))
        }

    suspend fun getCatalogBook(catalogId: String): ApiCatalogBook = withContext(Dispatchers.IO) {
        parseCatalogBook(authorizedJson("GET", "/v1/catalog/$catalogId"))
    }

    suspend fun getCatalogToc(catalogId: String): ApiCatalogToc = withContext(Dispatchers.IO) {
        parseCatalogToc(authorizedJson("GET", "/v1/catalog/$catalogId/toc"))
    }

    suspend fun getCatalogPreview(catalogId: String): ApiCatalogPreview = withContext(Dispatchers.IO) {
        parseCatalogPreview(authorizedJson("GET", "/v1/catalog/$catalogId/preview"))
    }

    suspend fun getCatalogChapter(catalogId: String, chapterId: String): ApiCatalogChapter =
        withContext(Dispatchers.IO) {
            parseCatalogChapter(authorizedJson("GET", "/v1/catalog/$catalogId/chapters/$chapterId"))
        }

    suspend fun downloadCatalogBook(catalogId: String, destFile: File): File = withContext(Dispatchers.IO) {
        streamToFile("/v1/catalog/$catalogId/download?mode=stream", destFile) { read, size ->
            if (read > 64L * 1024 * 1024 || size > 64L * 1024 * 1024) throw IOException("Book exceeds size limit")
        }
    }

    suspend fun addCatalogToShelf(catalogId: String): ApiShelfFromCatalog = withContext(Dispatchers.IO) {
        val json = authorizedJson("POST", "/v1/catalog/$catalogId/shelf")
        ApiShelfFromCatalog(
            book = parseBook(json.getJSONObject("book")),
            created = json.optBoolean("created", true)
        )
    }

    // --- Annotations ---

    suspend fun listAnnotations(bookId: String): List<ApiAnnotation> = withContext(Dispatchers.IO) {
        val path = "/v1/annotations?book_id=${bookId.encodeUrl()}"
        parseAnnotationList(authorizedRaw("GET", path))
    }

    suspend fun createAnnotation(
        clientAnnotationId: String,
        bookId: String,
        type: String,
        clientUpdatedAtIso: String,
        chapterId: String? = null,
        chapterIndex: Int? = null,
        startOffset: Int? = null,
        endOffset: Int? = null,
        selectedText: String? = null,
        color: String? = null,
        note: String? = null
    ): ApiAnnotation = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("client_annotation_id", clientAnnotationId)
            .put("book_id", bookId)
            .put("type", type)
            .put("client_updated_at", clientUpdatedAtIso)
        chapterId?.let { payload.put("chapter_id", it) }
        chapterIndex?.let { payload.put("chapter_index", it) }
        startOffset?.let { payload.put("start_offset", it) }
        endOffset?.let { payload.put("end_offset", it) }
        selectedText?.let { payload.put("selected_text", it) }
        color?.let { payload.put("color", it) }
        note?.let { payload.put("note", it) }
        parseAnnotation(authorizedJson("POST", "/v1/annotations", payload.toString()))
    }

    suspend fun updateAnnotation(id: String, patch: JSONObject): ApiAnnotation =
        withContext(Dispatchers.IO) {
            parseAnnotation(authorizedJson("PATCH", "/v1/annotations/$id", patch.toString()))
        }

    suspend fun deleteAnnotation(id: String) = withContext(Dispatchers.IO) {
        authorizedRaw("DELETE", "/v1/annotations/$id")
        Unit
    }

    // --- Feedback ---

    /**
     * Submit in-app feedback. Backend: `POST /v1/feedback`
     * Body: message, contact?, app_version, app_version_code, device_info, platform.
     * Auth optional — Bearer attached when signed in.
     */
    suspend fun submitFeedback(
        message: String,
        contact: String? = null,
        appVersion: String,
        appVersionCode: Int,
        deviceInfo: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("message", message.trim())
            .put("app_version", appVersion)
            .put("app_version_code", appVersionCode)
            .put("device_info", deviceInfo)
            .put("platform", "android")
        if (!contact.isNullOrBlank()) payload.put("contact", contact.trim())
        optionalAuthRaw("POST", "/v1/feedback", payload.toString())
        Unit
    }

    // --- Offline voices ---

    suspend fun listOfflineVoices(
        language: String? = null,
        featured: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): ApiOfflineVoiceList = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/offline-voices".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", pageSize.coerceIn(1, 100).toString())
            .apply {
                if (!language.isNullOrBlank()) addQueryParameter("language", language)
                if (featured != null) addQueryParameter("featured", featured.toString())
            }
            .build()
        parseOfflineVoiceList(authorizedRawUrl("GET", url.toString()))
    }

    suspend fun listFeaturedOfflineVoices(language: String? = null, limit: Int = 10): List<ApiOfflineVoice> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/v1/offline-voices/featured".toHttpUrl().newBuilder()
                .addQueryParameter("limit", limit.toString())
                .apply { if (!language.isNullOrBlank()) addQueryParameter("language", language) }
                .build()
            parseOfflineVoiceArray(authorizedRawUrl("GET", url.toString()))
        }

    suspend fun getOfflineVoice(voiceId: String): ApiOfflineVoice = withContext(Dispatchers.IO) {
        parseOfflineVoice(authorizedJson("GET", "/v1/offline-voices/$voiceId"))
    }

    suspend fun downloadOfflineVoiceSample(voiceId: String, destFile: File): File =
        withContext(Dispatchers.IO) {
            streamToFile("/v1/offline-voices/$voiceId/sample?mode=stream", destFile)
        }

    suspend fun downloadOfflineVoice(
        voiceId: String,
        destFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        streamToFile("/v1/offline-voices/$voiceId/download?mode=stream", destFile, onProgress)
    }

    private suspend fun streamToFile(
        path: String,
        destFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)? = null
    ): File {
        val context = currentCoroutineContext()
        val session = tokenProvider.requestSession()
        suspend fun token(refresh: Boolean = false): String {
            tokenProvider.requireSession(session)
            return tokenProvider.getToken(refresh).also { tokenProvider.requireSession(session) }
        }
        suspend fun call(token: String): File {
            tokenProvider.requireSession(session)
            val request = Request.Builder()
                .url("$baseUrl$path")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "*/*")
                .get()
                .build()
            return client.newCall(request).consumeApiResponse { response ->
                if (!response.isSuccessful) {
                    throw ApiHttpException(response.code, response.body?.string().orEmpty())
                }
                val body = response.body ?: throw IOException("Empty download")
                body.byteStream().use { input ->
                    atomicDownload(input, destFile, body.contentLength(),
                        checkActive = { context.ensureActive(); tokenProvider.requireSession(session) }, onProgress = onProgress)
                }
            }
        }
        return try {
            call(token())
        } catch (e: ApiHttpException) {
            if (e.code == 401) call(token(true)) else throw e
        }
    }

    private suspend fun authorizedJson(method: String, path: String, jsonBody: String? = null): JSONObject {
        val raw = authorizedRaw(method, path, jsonBody)
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private suspend fun authorizedRaw(
        method: String,
        path: String,
        jsonBody: String? = null,
        accept: String = "application/json"
    ): String = authorizedRawUrl(method, "$baseUrl$path", jsonBody, accept)

    private suspend fun authorizedRawUrl(
        method: String,
        fullUrl: String,
        jsonBody: String? = null,
        accept: String = "application/json"
    ): String {
        val session = tokenProvider.requestSession()
        suspend fun token(refresh: Boolean = false): String {
            tokenProvider.requireSession(session)
            return tokenProvider.getToken(refresh).also { tokenProvider.requireSession(session) }
        }
        suspend fun call(token: String): Pair<Int, String> {
            tokenProvider.requireSession(session)
            val builder = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", accept)
            val body = jsonBody?.toRequestBody("application/json".toMediaType())
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
                "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody(null))
                "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
                else -> error("Unsupported method $method")
            }
            return client.newCall(builder.build()).consumeApiResponse { response ->
                val result = response.code to response.body?.string().orEmpty()
                tokenProvider.requireSession(session)
                result
            }
        }
        var (code, text) = call(token())
        if (code == 401) {
            val retry = call(token(true))
            code = retry.first
            text = retry.second
        }
        if (code !in 200..299) throw ApiHttpException(code, text)
        return text
    }

    /** Like [authorizedRaw], but Authorization is optional when the user is signed out. */
    private suspend fun optionalAuthRaw(
        method: String,
        path: String,
        jsonBody: String? = null,
        accept: String = "application/json"
    ): String {
        val session = tokenProvider.requestSession()
        suspend fun token(refresh: Boolean = false): String {
            tokenProvider.requireSession(session)
            return tokenProvider.getToken(refresh).also { tokenProvider.requireSession(session) }
        }
        val fullUrl = "$baseUrl$path"
        suspend fun call(token: String?): Pair<Int, String> {
            tokenProvider.requireSession(session)
            val builder = Request.Builder()
                .url(fullUrl)
                .addHeader("Accept", accept)
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            val body = jsonBody?.toRequestBody("application/json".toMediaType())
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
                "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody(null))
                "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
                else -> error("Unsupported method $method")
            }
            return client.newCall(builder.build()).consumeApiResponse { response ->
                val result = response.code to response.body?.string().orEmpty()
                tokenProvider.requireSession(session)
                result
            }
        }
        val initialToken = if (session != null) {
            token()
        } else null
        var (code, text) = call(initialToken)
        if (code == 401 && session != null) {
            val refreshed = token(true)
            val retry = call(refreshed)
            code = retry.first
            text = retry.second
        }
        if (code !in 200..299) throw ApiHttpException(code, text)
        return text
    }

    private fun parseUser(json: JSONObject) = ApiUser(
        id = json.optString("id").ifBlank { json.optString("clerk_user_id") },
        clerkUserId = json.optStringOrNull("clerk_user_id"),
        email = json.optStringOrNull("email"),
        username = json.optStringOrNull("username"),
        displayName = json.optStringOrNull("display_name") ?: json.optStringOrNull("username"),
        avatarUrl = json.optStringOrNull("avatar_url")
    )

    private fun parseBookList(raw: String): List<ApiBook> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                buildList { for (i in 0 until arr.length()) add(parseBook(arr.getJSONObject(i))) }
            }
            else -> {
                val obj = JSONObject(trimmed)
                val arr = obj.optJSONArray("items") ?: obj.optJSONArray("books") ?: obj.optJSONArray("data")
                if (arr != null) {
                    buildList { for (i in 0 until arr.length()) add(parseBook(arr.getJSONObject(i))) }
                } else listOf(parseBook(obj))
            }
        }
    }

    private fun parseBook(json: JSONObject) = ApiBook(
        id = json.getString("id"),
        clientBookId = json.optStringOrNull("client_book_id"),
        title = json.optString("title", "Untitled"),
        author = json.optStringOrNull("author"),
        coverUrl = json.optStringOrNull("cover_url"),
        format = json.optStringOrNull("format"),
        sortOrder = json.optInt("sort_order", 0)
    )

    private fun parseProgressList(raw: String): List<ApiProgress> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("items") ?: obj.optJSONArray("progress") ?: obj.optJSONArray("data")
                    ?: return emptyList()
            }
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(parseProgress(o, o.optStringOrNull("book_id") ?: ""))
            }
        }
    }

    private fun parseProgress(json: JSONObject, fallbackBookId: String) = ApiProgress(
        bookId = json.optStringOrNull("book_id") ?: fallbackBookId,
        chapterId = json.optStringOrNull("chapter_id"),
        chapterIndex = json.optInt("chapter_index", 0),
        position = json.optInt("position", 0),
        percentage = json.optDouble("percentage", 0.0),
        clientUpdatedAt = json.optStringOrNull("client_updated_at")
    )

    private fun parseCatalogList(raw: String): ApiCatalogList {
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("items") ?: JSONArray()
        return ApiCatalogList(
            items = buildList { for (i in 0 until arr.length()) add(parseCatalogBook(arr.getJSONObject(i))) },
            total = obj.optInt("total", arr.length()),
            page = obj.optInt("page", 1),
            pageSize = obj.optInt("page_size", arr.length())
        )
    }

    private fun parseCatalogBookArray(raw: String): List<ApiCatalogBook> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        }
        return buildList { for (i in 0 until arr.length()) add(parseCatalogBook(arr.getJSONObject(i))) }
    }

    private fun parseCatalogBook(json: JSONObject): ApiCatalogBook {
        val cats = json.optJSONArray("categories")
        val categories = if (cats == null) emptyList() else buildList {
            for (i in 0 until cats.length()) {
                val c = cats.optJSONObject(i) ?: continue
                add(
                    ApiCatalogCategory(
                        id = c.optString("id"),
                        slug = c.optString("slug"),
                        name = c.optString("name"),
                        description = c.optStringOrNull("description"),
                        sortOrder = c.optInt("sort_order", 0)
                    )
                )
            }
        }
        return ApiCatalogBook(
            id = json.getString("id"),
            source = json.optString("source", "catalog"),
            sourceId = json.optString("source_id", json.getString("id")),
            title = json.optString("title", "Untitled"),
            author = json.optStringOrNull("author"),
            language = json.optString("language", "zh"),
            coverUrl = json.optStringOrNull("cover_url"),
            description = json.optStringOrNull("description"),
            license = json.optString("license", ""),
            format = json.optString("format", "epub"),
            fileSizeBytes = json.optLong("file_size_bytes", 0L),
            sortOrder = json.optInt("sort_order", 0),
            isFeatured = json.optBoolean("is_featured", false),
            previewChapterLimit = json.optInt("preview_chapter_limit", 0),
            shelfCount = json.optInt("shelf_count", 0),
            downloadCount = json.optInt("download_count", 0),
            categories = categories
        )
    }

    private fun parseCategoryList(raw: String): List<ApiCatalogCategory> {
        val trimmed = raw.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                add(
                    ApiCatalogCategory(
                        id = c.optString("id"),
                        slug = c.optString("slug"),
                        name = c.optString("name"),
                        description = c.optStringOrNull("description"),
                        sortOrder = c.optInt("sort_order", 0)
                    )
                )
            }
        }
    }

    private fun parseCatalogToc(json: JSONObject) = ApiCatalogToc(
        catalogId = json.optString("catalog_id"),
        previewChapterLimit = json.optInt("preview_chapter_limit", 0),
        items = parseTocItems(json.optJSONArray("items"))
    )

    private fun parseTocItems(arr: JSONArray?): List<ApiCatalogTocItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ApiCatalogTocItem(
                        chapterId = o.optString("chapter_id"),
                        chapterKey = o.optString("chapter_key"),
                        title = o.optString("title"),
                        chapterIndex = o.optInt("chapter_index", i),
                        wordCount = o.optInt("word_count", 0),
                        isPreview = o.optBoolean("is_preview", false)
                    )
                )
            }
        }
    }

    private fun parseCatalogChapter(json: JSONObject) = ApiCatalogChapter(
        catalogId = json.optString("catalog_id"),
        chapterId = json.optString("chapter_id"),
        chapterKey = json.optString("chapter_key"),
        title = json.optString("title"),
        chapterIndex = json.optInt("chapter_index", 0),
        wordCount = json.optInt("word_count", 0),
        isPreview = json.optBoolean("is_preview", false),
        contentText = json.optString("content_text", "")
    )

    private fun parseCatalogPreview(json: JSONObject): ApiCatalogPreview {
        val chaptersArr = json.optJSONArray("chapters") ?: JSONArray()
        val tocArr = json.optJSONArray("toc")
        return ApiCatalogPreview(
            book = parseCatalogBook(json.getJSONObject("book")),
            toc = parseTocItems(tocArr),
            chapters = buildList {
                for (i in 0 until chaptersArr.length()) {
                    add(parseCatalogChapter(chaptersArr.getJSONObject(i)))
                }
            }
        )
    }

    private fun parseAnnotationList(raw: String): List<ApiAnnotation> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        }
        return buildList { for (i in 0 until arr.length()) add(parseAnnotation(arr.getJSONObject(i))) }
    }

    private fun parseAnnotation(json: JSONObject) = ApiAnnotation(
        id = json.getString("id"),
        bookId = json.optString("book_id"),
        clientAnnotationId = json.optString("client_annotation_id"),
        type = json.optString("type"),
        chapterId = json.optStringOrNull("chapter_id"),
        chapterIndex = if (json.has("chapter_index") && !json.isNull("chapter_index")) json.optInt("chapter_index") else null,
        startOffset = if (json.has("start_offset") && !json.isNull("start_offset")) json.optInt("start_offset") else null,
        endOffset = if (json.has("end_offset") && !json.isNull("end_offset")) json.optInt("end_offset") else null,
        selectedText = json.optStringOrNull("selected_text"),
        color = json.optStringOrNull("color"),
        note = json.optStringOrNull("note"),
        clientUpdatedAt = json.optString("client_updated_at")
    )

    private fun parseOfflineVoiceList(raw: String): ApiOfflineVoiceList {
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("items") ?: JSONArray()
        return ApiOfflineVoiceList(
            items = buildList { for (i in 0 until arr.length()) add(parseOfflineVoice(arr.getJSONObject(i))) },
            total = obj.optInt("total", arr.length()),
            page = obj.optInt("page", 1),
            pageSize = obj.optInt("page_size", arr.length())
        )
    }

    private fun parseOfflineVoiceArray(raw: String): List<ApiOfflineVoice> {
        val trimmed = raw.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        }
        return buildList { for (i in 0 until arr.length()) add(parseOfflineVoice(arr.getJSONObject(i))) }
    }

    private fun parseOfflineVoice(json: JSONObject) = ApiOfflineVoice(
        id = json.getString("id"),
        slug = json.optString("slug"),
        name = json.optString("name"),
        engine = json.optString("engine"),
        modelType = json.optString("model_type"),
        language = json.optString("language"),
        gender = json.optStringOrNull("gender"),
        sampleRate = json.optInt("sample_rate", 22050),
        version = json.optString("version"),
        license = json.optString("license"),
        description = json.optStringOrNull("description"),
        fileSizeBytes = json.optLong("file_size_bytes", 0L),
        downloadCount = json.optInt("download_count", 0),
        isFeatured = json.optBoolean("is_featured", false),
        hasSample = json.optBoolean("has_sample", false),
        sampleUrl = json.optStringOrNull("sample_url"),
        checksumSha256 = json.optStringOrNull("checksum_sha256")
    )

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).ifBlank { null }
    }
}

// Server bodies may contain user content or internal diagnostics. Never expose them in logs/UI.
class ApiHttpException(val code: Int, val body: String, val retryAfterMillis: Long? = null) : IOException("HTTP $code")
