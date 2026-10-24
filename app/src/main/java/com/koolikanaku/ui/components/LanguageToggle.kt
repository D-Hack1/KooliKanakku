package com.koolikanaku.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.koolikanaku.R
import com.koolikanaku.ui.AppLanguage

/** Shared EN/ML toggle (top-right, left of [DarkModeToggle], on every top-level screen). */
@Composable
fun LanguageToggle(language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = language == AppLanguage.EN,
            onClick = { onLanguageChange(AppLanguage.EN) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text(stringResource(R.string.language_english_short)) }
        SegmentedButton(
            selected = language == AppLanguage.ML,
            onClick = { onLanguageChange(AppLanguage.ML) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text(stringResource(R.string.language_malayalam_short)) }
    }
}
