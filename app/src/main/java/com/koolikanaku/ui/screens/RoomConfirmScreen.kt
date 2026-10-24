package com.koolikanaku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.domain.CleaningTask
import com.koolikanaku.domain.ClutterLevel
import com.koolikanaku.domain.DetectedObject
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.AddTaskSheet
import com.koolikanaku.ui.components.BoundingBoxOverlay
import com.koolikanaku.ui.components.CatalogEntry
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle
import com.koolikanaku.ui.components.PhotoImage
import com.koolikanaku.ui.components.SeverityChip
import com.koolikanaku.ui.components.TaskRow
import com.koolikanaku.ui.components.openPhotoExternally
import com.koolikanaku.ui.displayName
import androidx.compose.ui.platform.LocalContext

enum class ConfirmPhotoTab { WIDE, TOUGHEST }

/** One photo in the Room Confirm viewer: its own boxes (wide shots) or its own grime caption (close-ups). */
data class ConfirmPhoto(
    val uri: android.net.Uri,
    val detections: List<DetectedObject> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val caption: String = "",
    val isWorst: Boolean = false
)

data class CountRowUi(val label: String, val length: Int, val width: Int, val wide: Int, val count: Int)

/**
 * Room Confirm: boxes on the analyzed photos, an opt-in checklist with +/-, "+ Add task",
 * a changeable room type, an editable size, and a severity override. plan.md Section 3 step 5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomConfirmScreen(
    roomNumber: Int,
    photoTab: ConfirmPhotoTab,
    onPhotoTabChange: (ConfirmPhotoTab) -> Unit,
    wideDetections: List<DetectedObject>,
    wideImageWidth: Int,
    wideImageHeight: Int,
    widePhotoUri: android.net.Uri? = null,
    toughPhotoUri: android.net.Uri? = null,
    widePhotos: List<ConfirmPhoto> = emptyList(),
    toughPhotos: List<ConfirmPhoto> = emptyList(),
    toughSummaryText: String,
    roomType: RoomType,
    suggestedRoomType: RoomType?,
    onRoomTypeChange: (RoomType) -> Unit,
    sizeSqFt: String,
    onSizeChange: (String) -> Unit,
    sizeSourceLabel: String,
    dimsNote: String?,
    sizeProblem: String? = null,
    canSave: Boolean = true,
    grimeScoreText: String? = null,
    severity: SeverityLevel,
    severityOverridden: Boolean,
    suggestedSeverityLabel: String,
    onSeverityChange: (SeverityLevel) -> Unit,
    severityMultiplier: (SeverityLevel) -> Double,
    clutterLevel: ClutterLevel,
    looseItemCount: Int,
    tasks: List<CleaningTask>,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    allSelected: Boolean,
    onToggleTask: (String) -> Unit,
    onIncrementTask: (String) -> Unit,
    onDecrementTask: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    availableCatalog: List<CatalogEntry>,
    onAddCatalogTask: (CatalogEntry) -> Unit,
    onAddCustomTask: (name: String, minutes: Int) -> Unit = { _, _ -> },
    countRows: List<CountRowUi>,
    estimateHoursText: String,
    estimatePayText: String,
    mathLine1: String,
    mathLine2: String,
    onSaveRoom: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var addSheetOpen by remember { mutableStateOf(false) }
    var howCountedOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.confirm_estimate_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$estimateHoursText · $estimatePayText", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.wrapContentWidth()) {
                            Text(mathLine1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(mathLine2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = onSaveRoom, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.confirm_save_room), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.confirm_subtitle, roomNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        TabButton(stringResource(R.string.confirm_tab_wide), photoTab == ConfirmPhotoTab.WIDE, { onPhotoTabChange(ConfirmPhotoTab.WIDE) }, Modifier.weight(1f))
                        TabButton(stringResource(R.string.confirm_tab_tough), photoTab == ConfirmPhotoTab.TOUGHEST, { onPhotoTabChange(ConfirmPhotoTab.TOUGHEST) }, Modifier.weight(1f))
                    }
                    val confirmContext = LocalContext.current
                    val isWideTab = photoTab == ConfirmPhotoTab.WIDE
                    val wideList = widePhotos.ifEmpty {
                        if (widePhotoUri != null) listOf(ConfirmPhoto(widePhotoUri, wideDetections, wideImageWidth, wideImageHeight)) else emptyList()
                    }
                    val toughList = toughPhotos.ifEmpty {
                        if (toughPhotoUri != null) listOf(ConfirmPhoto(toughPhotoUri, caption = toughSummaryText)) else emptyList()
                    }
                    val photoList = if (isWideTab) wideList else toughList
                    var selectedWide by remember { mutableStateOf(0) }
                    var selectedTough by remember { mutableStateOf(toughList.indexOfFirst { it.isWorst }.coerceAtLeast(0)) }
                    val selectedIndex = (if (isWideTab) selectedWide else selectedTough).coerceIn(0, (photoList.size - 1).coerceAtLeast(0))
                    val shown = photoList.getOrNull(selectedIndex)
                    val shownPhotoUri = shown?.uri
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(358f / 250f)
                                .let { m -> if (shownPhotoUri != null) m.clickable { openPhotoExternally(confirmContext, shownPhotoUri) } else m }
                        ) {
                            PhotoImage(uri = shownPhotoUri, modifier = Modifier.fillMaxSize(), cornerRadiusDp = 0)
                            if (isWideTab) {
                                BoundingBoxOverlay(
                                    detections = shown?.detections.orEmpty(),
                                    imageWidth = shown?.imageWidth ?: 0,
                                    imageHeight = shown?.imageHeight ?: 0
                                )
                            } else {
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(0xD9171210),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                                ) {
                                    Text(
                                        shown?.caption?.takeIf { it.isNotBlank() } ?: toughSummaryText,
                                        color = androidx.compose.ui.graphics.Color(0xFFF5EBE0),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (photoList.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            photoList.forEachIndexed { index, photo ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(if (index == selectedIndex) 2.dp else 1.dp, if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.size(width = 72.dp, height = 54.dp).clickable { if (isWideTab) selectedWide = index else selectedTough = index }
                                ) {
                                    PhotoImage(uri = photo.uri, modifier = Modifier.fillMaxSize(), cornerRadiusDp = 0)
                                }
                            }
                        }
                        if (!isWideTab) Text(toughSummaryText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.confirm_box_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                SectionCard {
                    Text(stringResource(R.string.confirm_room_type_label), style = MaterialTheme.typography.titleSmall)
                    val scroll = rememberScrollState()
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoomType.entries.forEach { type ->
                            val on = type == roomType
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier.height(44.dp).clickable { onRoomTypeChange(type) }
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(type.displayName(), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    if (suggestedRoomType != null) {
                        Text(
                            stringResource(R.string.confirm_room_type_suggested, suggestedRoomType.displayName()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.confirm_size_label), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = sizeSqFt,
                            onValueChange = { onSizeChange(it.filter { ch -> ch.isDigit() }) },
                            trailingIcon = { Text(stringResource(R.string.confirm_sqft_unit), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(sizeSourceLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    if (sizeProblem != null) {
                        Text(sizeProblem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (dimsNote != null) {
                        Text(dimsNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.confirm_size_shape_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                SectionCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.confirm_dirt_label), style = MaterialTheme.typography.titleSmall)
                        if (severityOverridden) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(stringResource(R.string.confirm_edited_badge), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                                }
                                Text(stringResource(R.string.confirm_ai_said, suggestedSeverityLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    SeverityChip(selected = severity, onSelect = onSeverityChange, multiplierFor = severityMultiplier)
                    Text(stringResource(R.string.confirm_dirt_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (grimeScoreText != null) {
                        Text(grimeScoreText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.confirm_clutter_summary, clutterLevel.displayName(), looseItemCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.confirm_clutter_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.confirm_list_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onSelectAll(!allSelected) }) {
                        Text(stringResource(if (allSelected) R.string.confirm_clear_all else R.string.confirm_select_all))
                    }
                }
            }

            item {
                OutlinedButton(onClick = { howCountedOpen = true }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" " + stringResource(R.string.confirm_how_counted))
                }
            }

            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    language = language,
                    onToggle = { onToggleTask(task.id) },
                    onIncrement = { onIncrementTask(task.id) },
                    onDecrement = { onDecrementTask(task.id) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { addSheetOpen = true },
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(" " + stringResource(R.string.confirm_add_task))
                }
            }
        }
    }

    if (addSheetOpen) {
        AddTaskSheet(
            available = availableCatalog,
            language = language,
            onAddTask = {
                onAddCatalogTask(it)
                addSheetOpen = false
            },
            onAddCustomTask = { name, minutes ->
                onAddCustomTask(name, minutes)
                addSheetOpen = false
            },
            onDismissRequest = { addSheetOpen = false },
            sheetState = rememberModalBottomSheetState()
        )
    }

    if (howCountedOpen) {
        ModalBottomSheet(onDismissRequest = { howCountedOpen = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.confirm_how_counted), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.confirm_rule_a), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.confirm_rule_b), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.confirm_rule_c), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    HeaderCell(stringResource(R.string.confirm_col_item), Modifier.weight(1.6f))
                    HeaderCell(stringResource(R.string.confirm_col_length), Modifier.weight(1f))
                    HeaderCell(stringResource(R.string.confirm_col_width), Modifier.weight(1f))
                    HeaderCell(stringResource(R.string.confirm_col_wide), Modifier.weight(1f))
                    HeaderCell(stringResource(R.string.confirm_col_count), Modifier.weight(1f))
                }
                countRows.forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(row.label, modifier = Modifier.weight(1.6f))
                        Text(row.length.toString(), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text(row.width.toString(), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text(row.wide.toString(), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text(
                            "= ${row.count}",
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
                Text(stringResource(R.string.confirm_count_limit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { howCountedOpen = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_done))
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
