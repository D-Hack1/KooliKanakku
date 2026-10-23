package com.koolikanaku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.ui.displayName

/**
 * Three-way severity picker (Light / Moderate / Heavy) with a filled-bar readout,
 * matching the Room Confirm "How dirty is it?" control. [selected] is always the
 * person's current choice; detection only supplies the starting value (plan.md Section 5).
 */
@Composable
fun SeverityChip(
    selected: SeverityLevel,
    onSelect: (SeverityLevel) -> Unit,
    multiplierFor: (SeverityLevel) -> Double,
    modifier: Modifier = Modifier
) {
    val levels = SeverityLevel.entries
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        levels.forEachIndexed { index, level ->
            val isOn = level == selected
            val filled = levels.indexOf(level) <= levels.indexOf(selected)
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 2.dp, bottomEnd = 2.dp)
                levels.lastIndex -> RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)
                else -> RoundedCornerShape(2.dp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(level) },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            color = if (filled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape
                        )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = level.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "×" + String.format("%.2f", multiplierFor(level)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
