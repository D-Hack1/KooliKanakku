package com.koolikanaku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.koolikanaku.domain.DetectedObject

/**
 * Draws detection boxes over a displayed photo. [imageWidth]/[imageHeight] are the size of the
 * bitmap that was analyzed (see [com.koolikanaku.domain.PhotoAnalysis]); boxes are scaled from
 * that space to whatever size this overlay is actually laid out at, so it works regardless of
 * how the photo underneath it is cropped or scaled on screen.
 */
@Composable
fun BoundingBoxOverlay(
    detections: List<DetectedObject>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    labelFor: (DetectedObject) -> String = { it.label },
    boxColor: Color = MaterialTheme.colorScheme.primary
) {
    if (imageWidth <= 0 || imageHeight <= 0) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val scaleX = containerWidthPx / imageWidth
        val scaleY = containerHeightPx / imageHeight

        detections.forEach { detection ->
            val box = detection.boundingBox
            val left = box.left * scaleX
            val top = box.top * scaleY
            val width = (box.right - box.left) * scaleX
            val height = (box.bottom - box.top) * scaleY
            val labelAbove = top > with(density) { 24.dp.toPx() }

            with(density) {
                Box(
                    modifier = Modifier
                        .offset(x = left.toDp(), y = top.toDp())
                        .size(width.toDp(), height.toDp())
                        .boxBorder(boxColor)
                ) {
                    Text(
                        text = labelFor(detection),
                        color = Color(0xFFF5EBE0),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .offset(y = if (labelAbove) (-17).dp else 0.dp)
                            .background(Color(0xDD171210), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun Modifier.boxBorder(color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
}
