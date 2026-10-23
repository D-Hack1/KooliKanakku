package com.koolikanaku.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.ui.AppLanguage

/** A catalog entry the person can still add to a room's checklist ("+ Add task"). */
data class CatalogEntry(
    val key: String,
    val titleEn: String,
    val titleMl: String,
    val minutesPerUnit: Int
)

fun CatalogEntry.titleFor(language: AppLanguage) = if (language == AppLanguage.ML) titleMl else titleEn

/** Bottom sheet for Room Confirm's "+ Add task": every catalog entry not already on the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    available: List<CatalogEntry>,
    language: AppLanguage,
    onAddTask: (CatalogEntry) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.confirm_add_task_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (available.isEmpty()) {
                Text(
                    text = stringResource(R.string.confirm_no_more_tasks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            available.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onAddTask(entry) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = entry.titleFor(language),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.confirm_minutes_unit).let { "${entry.minutesPerUnit} $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
