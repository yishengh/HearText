package com.yishenghuang.heartext.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppRadius
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.HearPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BookTransitionOverlay(
    title: String,
    coverPath: String? = null,
    isReady: Boolean,
    onBack: () -> Unit,
    onTransitionComplete: () -> Unit
) {
    val scrimAlpha = remember { Animatable(0f) }
    val sheetAlpha = remember { Animatable(0f) }
    val sheetScale = remember { Animatable(0.9f) }
    val isClosing = remember { mutableStateOf(false) }
    val requestBack = {
        if (!isClosing.value) {
            isClosing.value = true
            onBack()
        }
    }

    BackHandler(enabled = !isClosing.value, onBack = requestBack)

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, tween(300)) }
        launch { sheetAlpha.animateTo(1f, tween(300)) }
        launch { sheetScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    }

    LaunchedEffect(isReady) {
        if (!isReady || isClosing.value) return@LaunchedEffect
        // Wait until enter animation has progressed (avoids missing the dismiss race).
        var waits = 0
        while (sheetAlpha.value < 0.5f && !isClosing.value && waits < 40) {
            delay(16)
            waits++
        }
        if (isClosing.value) return@LaunchedEffect
        isClosing.value = true
        delay(120)
        launch { sheetAlpha.animateTo(0f, tween(200)) }
        launch { sheetScale.animateTo(0.95f, tween(250)) }
        launch { scrimAlpha.animateTo(0f, tween(300)) }
        delay(300)
        onTransitionComplete()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value)
                .background(Color.Black.copy(alpha = 0.55f))
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = sheetScale.value
                        scaleY = sheetScale.value
                        alpha = sheetAlpha.value
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(AppColors.CardBg)
            ) {
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

                IconButton(
                    onClick = requestBack,
                    enabled = !isClosing.value,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = AppSpace.lg, top = AppSpace.sm)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AppColors.BgGray)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = AppColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = AppSpace.xl,
                            end = AppSpace.xl,
                            bottom = navBarPadding.calculateBottomPadding()
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(270.dp)
                            .clip(RoundedCornerShape(AppRadius.md))
                            .background(AppColors.BgGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverPath != null && File(coverPath).exists()) {
                            AsyncImage(
                                model = File(coverPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(AppRadius.md)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.common_opening),
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = HearPurple,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}
