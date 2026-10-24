package com.koolikanaku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.koolikanaku.R

/**
 * Branded launch animation: the logo is revealed with a diagonal wipe (bottom-left to
 * top-right) so it reads like the mark is being drawn in one stroke, then holds briefly
 * before [onFinished]. Background follows the current theme (dark/light both covered by
 * the caller choosing the background colour).
 */
@Composable
fun AnimatedSplash(backgroundColor: androidx.compose.ui.graphics.Color, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
        kotlinx.coroutines.delay(150)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground_raw),
            contentDescription = null,
            modifier = Modifier
                .size(180.dp)
                .scale(scale.value)
                .drawWithContent {
                    val p = progress.value
                    // Diagonal wipe: reveal grows from bottom-left corner toward top-right,
                    // tracing roughly the same direction the logo's own stroke travels.
                    val revealWidth = size.width * (0.35f + p * 1.3f)
                    val revealHeight = size.height * (0.35f + p * 1.3f)
                    clipRect(
                        left = 0f,
                        top = size.height - revealHeight,
                        right = revealWidth,
                        bottom = size.height
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
    }
}
