package com.koolikanaku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.ui.components.RoomCard

/** One room's summary for the Job Hub list. */
data class RoomSummaryUi(
    val id: String,
    val name: String,
    val metaLine1: String,
    val metaLine2: String,
    val timeText: String,
    val payText: String,
    val analyzed: Boolean = true
)

/**
 * Job Hub: every room added so far with its subtotal, Add room / Finish, and the running
 * job total. First entry goes straight into Room Capture for room 1 (plan.md Section 3).
 */
@Composable
fun JobHubScreen(
    modeLabel: String,
    rateLabel: String,
    paceLabel: String,
    rooms: List<RoomSummaryUi>,
    totalText: String,
    onAddRoom: () -> Unit,
    onRoomClick: (String) -> Unit,
    onFinish: () -> Unit,
    canFinish: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasRooms = rooms.isNotEmpty()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.jobhub_total_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(totalText, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (hasRooms && !canFinish) {
                        Text(stringResource(R.string.jobhub_finish_blocked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onFinish,
                        enabled = hasRooms && canFinish,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text(stringResource(R.string.jobhub_finish_button), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.jobhub_title), style = MaterialTheme.typography.headlineLarge)
                    Text(pluralStringResource(R.plurals.jobhub_rooms_count_plural, rooms.size, rooms.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(modeLabel, filled = true)
                    Pill(rateLabel, filled = false)
                    Pill(paceLabel, filled = false)
                }
            }

            if (!hasRooms) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.MeetingRoom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(80.dp)
                    )
                    Text(stringResource(R.string.jobhub_empty_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        stringResource(R.string.jobhub_empty_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AddRoomButton(onAddRoom, firstRoom = true, modifier = Modifier.padding(top = 18.dp).fillMaxWidth())
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rooms, key = { it.id }) { room ->
                        RoomCard(
                            roomName = room.name,
                            metaLine1 = room.metaLine1,
                            metaLine2 = room.metaLine2,
                            timeText = room.timeText,
                            payText = room.payText,
                            onClick = { onRoomClick(room.id) },
                            analyzed = room.analyzed
                        )
                    }
                    item { AddRoomButton(onAddRoom, firstRoom = false, modifier = Modifier.fillMaxWidth()) }
                    item {
                        Text(
                            stringResource(R.string.jobhub_tap_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, filled: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (filled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun AddRoomButton(onClick: () -> Unit, firstRoom: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(
                stringResource(if (firstRoom) R.string.jobhub_add_first_room else R.string.jobhub_add_room),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
