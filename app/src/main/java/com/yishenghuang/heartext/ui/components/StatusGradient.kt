package com.yishenghuang.heartext.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.yishenghuang.heartext.ui.theme.AppColors

@Composable
fun StatusGradientOverlay(modifier: Modifier = Modifier) {
    val bgColor = AppColors.WindowBg
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to bgColor,
                        0.55f to bgColor,
                        1.0f to bgColor.copy(alpha = 0f)
                    )
                )
            )
    )
}

@Composable
fun NavigationGradientOverlay(modifier: Modifier = Modifier) {
    val bgColor = AppColors.WindowBg
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to bgColor.copy(alpha = 0f),
                        0.45f to bgColor.copy(alpha = 0.6f),
                        1.0f to bgColor
                    )
                )
            )
    )
}
