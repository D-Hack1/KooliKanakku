package com.koolikanaku.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.koolikanaku.R
import com.koolikanaku.domain.ClutterLevel
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.domain.TaskSource

/** App display language. Independent of system locale so Setup can switch it live. */
enum class AppLanguage { EN, ML }

/** How the worker is choosing a room's size in Room Capture. UI-only; not part of the domain contract. */
enum class SizeMethod { MEASURE, ENTER, PRESET }

/** Which guided shot the full-screen camera overlay is currently framing. */
enum class CaptureTarget { LENGTH, WIDTH, WIDE, TOUGHEST }

@Composable
fun RoomType.displayName(): String = stringResource(
    when (this) {
        RoomType.KITCHEN -> R.string.room_type_kitchen
        RoomType.BATHROOM -> R.string.room_type_bathroom
        RoomType.BEDROOM -> R.string.room_type_bedroom
        RoomType.LIVING_ROOM -> R.string.room_type_living_room
        RoomType.OTHER -> R.string.room_type_other
    }
)

@Composable
fun SeverityLevel.displayName(): String = stringResource(
    when (this) {
        SeverityLevel.LIGHT -> R.string.severity_light
        SeverityLevel.MODERATE -> R.string.severity_moderate
        SeverityLevel.HEAVY -> R.string.severity_heavy
    }
)

@Composable
fun ClutterLevel.displayName(): String = stringResource(
    when (this) {
        ClutterLevel.LOW -> R.string.clutter_low
        ClutterLevel.MEDIUM -> R.string.clutter_medium
        ClutterLevel.HIGH -> R.string.clutter_high
    }
)

@Composable
fun TaskSource.displayName(): String = stringResource(
    when (this) {
        TaskSource.DETECTED -> R.string.task_source_detected
        TaskSource.TEMPLATE -> R.string.task_source_template
        TaskSource.MANUAL -> R.string.task_source_manual
    }
)

/** Picks a [CleaningTask]'s title for the currently selected app language. */
fun com.koolikanaku.domain.CleaningTask.titleFor(language: AppLanguage): String =
    if (language == AppLanguage.ML) titleMl else titleEn

/** Simple bilingual selector based on [language]. */
fun t(language: AppLanguage, en: String, ml: String): String =
    if (language == AppLanguage.ML) ml else en

fun RoomType.displayName(language: AppLanguage): String = when (this) {
    RoomType.KITCHEN -> if (language == AppLanguage.ML) "അടുക്കള" else "Kitchen"
    RoomType.BATHROOM -> if (language == AppLanguage.ML) "കുളിമുറി" else "Bathroom"
    RoomType.BEDROOM -> if (language == AppLanguage.ML) "കിടപ്പുമുറി" else "Bedroom"
    RoomType.LIVING_ROOM -> if (language == AppLanguage.ML) "സ്വീകരണമുറി" else "Living Room"
    RoomType.OTHER -> if (language == AppLanguage.ML) "മറ്റുള്ളവ" else "Other"
}
