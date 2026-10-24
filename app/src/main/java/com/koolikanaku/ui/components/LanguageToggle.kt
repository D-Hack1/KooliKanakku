package com.koolikanaku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koolikanaku.R
import com.koolikanaku.ui.AppLanguage

/**
 * Shared EN/ML toggle designed to blend harmoniously with KooliKanaku's warm artisan theme.
 * Replaces the heavy default M3 segmented button with an elegant, animated capsule pill
 * that sits neatly beside [DarkModeToggle] on top-level headers.
 */
@Composable
fun LanguageToggle(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMalayalam = language == AppLanguage.ML
    val pillShape = RoundedCornerShape(999.dp)

    val enContentDesc = stringResource(R.string.language_english)
    val mlContentDesc = stringResource(R.string.language_malayalam)

    BoxWithConstraints(
        modifier = modifier
            .height(34.dp)
            .width(84.dp)
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = pillShape
            )
            .padding(3.dp)
            .semantics {
                role = Role.Switch
                contentDescription = if (isMalayalam) mlContentDesc else enContentDesc
            }
    ) {
        val segmentWidth = maxWidth / 2
        val targetOffset = if (isMalayalam) segmentWidth else 0.dp

        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "language_pill_offset"
        )

        // Animated sliding indicator pill with subtle elevation
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(elevation = 1.dp, shape = pillShape, clip = false)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = pillShape
                )
        )

        // Interactive labels overlay
        Row(modifier = Modifier.fillMaxSize()) {
            // EN segment
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { if (isMalayalam) onLanguageChange(AppLanguage.EN) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val textColor by animateColorAsState(
                    targetValue = if (!isMalayalam) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "en_text_color"
                )
                Text(
                    text = stringResource(R.string.language_english_short),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp
                    ),
                    fontWeight = if (!isMalayalam) FontWeight.Bold else FontWeight.Medium,
                    color = textColor
                )
            }

            // ML (മല) segment
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { if (!isMalayalam) onLanguageChange(AppLanguage.ML) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val textColor by animateColorAsState(
                    targetValue = if (isMalayalam) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "ml_text_color"
                )
                Text(
                    text = stringResource(R.string.language_malayalam_short),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 0.2.sp
                    ),
                    fontWeight = if (isMalayalam) FontWeight.Bold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}
