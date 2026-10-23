package com.koolikanaku.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.domain.CleaningTask
import com.koolikanaku.domain.TaskSource
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.displayName
import com.koolikanaku.ui.titleFor

/**
 * One checklist row in Room Confirm / Bill: opt-in toggle, source badge
 * (Seen in photo / Suggested for this room / Added by you), and a quantity
 * stepper. Everything starts unchecked (plan.md Section 5).
 */
@Composable
fun TaskRow(
    task: CleaningTask,
    language: AppLanguage,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val on = task.isSelected
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = when (task.source) {
                                TaskSource.DETECTED -> Icons.Outlined.CameraAlt
                                TaskSource.TEMPLATE -> Icons.Outlined.Checklist
                                TaskSource.MANUAL -> Icons.Outlined.EditNote
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (on) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.titleFor(language),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = task.source.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.confirm_task_minutes,
                        task.minutesPerUnit,
                        task.quantity,
                        task.minutesPerUnit * task.quantity
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrement) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.confirm_decrease_quantity, task.titleFor(language)))
                    }
                    Text(
                        text = task.quantity.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.width(20.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = onIncrement) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.confirm_increase_quantity, task.titleFor(language)))
                    }
                }
            }
        }
    }
}
