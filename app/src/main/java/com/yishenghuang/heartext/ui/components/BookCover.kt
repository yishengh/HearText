package com.yishenghuang.heartext.ui.components

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yishenghuang.heartext.data.BookEntity
import com.yishenghuang.heartext.ui.theme.AppRadius
import com.yishenghuang.heartext.ui.theme.HearCardShadow
import com.yishenghuang.heartext.ui.theme.HearPurple
import java.io.File

@Composable
fun BookCover(
    book: BookEntity,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 3f / 4f
) {
    val local = book.coverPath?.takeIf { File(it).exists() }
    val remote = book.coverUrl?.takeIf { it.isNotBlank() }
    val model: Any? = local?.let { File(it) } ?: remote
    val shape = RoundedCornerShape(AppRadius.cover)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(aspectRatio)
                .clip(shape)
                .softBookShadow(AppRadius.cover)
        )
    } else {
        Box(
            modifier = modifier
                .aspectRatio(aspectRatio)
                .clip(shape)
                .softBookShadow(AppRadius.cover)
                .background(HearPurple.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = HearPurple)
        }
    }
}

fun Modifier.softBookShadow(cornerRadius: Dp): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    val shadowRadius = 16.dp.toPx()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.copy(alpha = 0.01f).toArgb()
        setShadowLayer(shadowRadius, 0f, 4.dp.toPx(), HearCardShadow.toArgb())
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            RectF(0f, 0f, size.width, size.height),
            radius,
            radius,
            paint
        )
    }
}
