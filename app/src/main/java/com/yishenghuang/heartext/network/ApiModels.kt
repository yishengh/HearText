package com.yishenghuang.heartext.network

data class ApiUser(
    val id: String,
    val clerkUserId: String? = null,
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

data class ApiBook(
    val id: String,
    val clientBookId: String?,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val format: String?,
    val sortOrder: Int = 0
)

data class ApiProgress(
    val bookId: String,
    val chapterId: String?,
    val chapterIndex: Int,
    val position: Int,
    val percentage: Double,
    val clientUpdatedAt: String?,
    val locatorJson: String? = null
)

data class ApiCatalogCategory(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val sortOrder: Int = 0
)

data class ApiCatalogBook(
    val id: String,
    val source: String,
    val sourceId: String,
    val title: String,
    val author: String?,
    val language: String,
    val coverUrl: String?,
    val description: String?,
    val license: String,
    val format: String,
    val fileSizeBytes: Long,
    val sortOrder: Int,
    val isFeatured: Boolean,
    val previewChapterLimit: Int,
    val shelfCount: Int,
    val downloadCount: Int,
    val categories: List<ApiCatalogCategory> = emptyList()
)

data class ApiCatalogList(
    val items: List<ApiCatalogBook>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

data class ApiCatalogTocItem(
    val chapterId: String,
    val chapterKey: String,
    val title: String,
    val chapterIndex: Int,
    val wordCount: Int,
    val isPreview: Boolean
)

data class ApiCatalogToc(
    val catalogId: String,
    val previewChapterLimit: Int,
    val items: List<ApiCatalogTocItem>
)

data class ApiCatalogChapter(
    val catalogId: String,
    val chapterId: String,
    val chapterKey: String,
    val title: String,
    val chapterIndex: Int,
    val wordCount: Int,
    val isPreview: Boolean,
    val contentText: String
)

data class ApiCatalogPreview(
    val book: ApiCatalogBook,
    val toc: List<ApiCatalogTocItem>,
    val chapters: List<ApiCatalogChapter>
)

data class ApiShelfFromCatalog(
    val book: ApiBook,
    val created: Boolean
)

data class ApiAnnotation(
    val id: String,
    val bookId: String,
    val clientAnnotationId: String,
    val type: String,
    val chapterId: String?,
    val chapterIndex: Int?,
    val startOffset: Int?,
    val endOffset: Int?,
    val selectedText: String?,
    val color: String?,
    val note: String?,
    val clientUpdatedAt: String,
    val locatorJson: String? = null
)

data class ApiOfflineVoice(
    val id: String,
    val slug: String,
    val name: String,
    val engine: String,
    val modelType: String,
    val language: String,
    val gender: String?,
    val sampleRate: Int,
    val version: String,
    val license: String,
    val description: String?,
    val fileSizeBytes: Long,
    val downloadCount: Int,
    val isFeatured: Boolean,
    val hasSample: Boolean,
    val sampleUrl: String?,
    val checksumSha256: String? = null
)

data class ApiOfflineVoiceList(
    val items: List<ApiOfflineVoice>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)
