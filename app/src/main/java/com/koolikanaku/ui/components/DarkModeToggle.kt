package com.koolikanaku.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.koolikanaku.R

/** Shared light/dark toggle (top-right on every top-level screen, offline and connect alike). */
@Composable
fun DarkModeToggle(darkTheme: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = if (darkTheme) stringResource(R.string.setup_light_mode) else stringResource(R.string.setup_dark_mode),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
