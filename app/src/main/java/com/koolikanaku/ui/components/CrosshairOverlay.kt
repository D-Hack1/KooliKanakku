package com.koolikanaku.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.koolikanaku.R

/**
 * Full-screen guide for the measure shots: a fixed center crosshair (so only the sensor
 * angle matters, never a tap-to-point conversion - plan.md Section 4C), a live
 * tilt/distance/side readout, and a "hold steady" indicator tied to the gravity-sensor
 * steady flag. [tiltText]/[distanceText]/[sideText] are pre-formatted by the caller.
 */
@Composable
fun CrosshairOverlay(
    showReadout: Boolean,
    isSteady: Boolean,
    tiltText: String,
    distanceText: String,
    sideText: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Crosshair(modifier = Modifier.align(Alignment.Center))

        if (showReadout) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 170.dp)
                    .fillMaxWidth()
                    .background(Color(0xC7171210), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReadoutStat(stringResource(R.string.capture_tilt_label), tiltText)
                    ReadoutStat(stringResource(R.string.capture_distance_label), distanceText)
                    ReadoutStat(stringResource(R.string.capture_side_label), sideText)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteadyDot(isSteady)
                        Text(
                            text = stringResource(if (isSteady) R.string.capture_steady_ok else R.string.capture_hold_steady),
                            color = Color(0xFFF5EBE0),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadoutStat(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFFD9CBBB), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color(0xFFF5EBE0), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SteadyDot(isSteady: Boolean) {
    if (isSteady) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color.Transparent)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "steady-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "steady-pulse-alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
    )
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    val strokeColor = Color(0xFFFFF9F2)
    androidx.compose.foundation.Canvas(modifier = modifier.size(88.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * 0.23f
        drawCircle(color = strokeColor, radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        drawLine(strokeColor, androidx.compose.ui.geometry.Offset(cx, 0f), androidx.compose.ui.geometry.Offset(cx, size.height * 0.27f), strokeWidth = 2.dp.toPx())
        drawLine(strokeColor, androidx.compose.ui.geometry.Offset(cx, size.height * 0.73f), androidx.compose.ui.geometry.Offset(cx, size.height), strokeWidth = 2.dp.toPx())
        drawLine(strokeColor, androidx.compose.ui.geometry.Offset(0f, cy), androidx.compose.ui.geometry.Offset(size.width * 0.27f, cy), strokeWidth = 2.dp.toPx())
        drawLine(strokeColor, androidx.compose.ui.geometry.Offset(size.width * 0.73f, cy), androidx.compose.ui.geometry.Offset(size.width, cy), strokeWidth = 2.dp.toPx())
        drawCircle(color = Color(0xFFFF7A3D), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
    }
}
