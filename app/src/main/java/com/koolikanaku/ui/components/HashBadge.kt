package com.koolikanaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koolikanaku.ui.theme.KooliKanakuMonoStyle

/**
 * Short SHA-256 prefix shown under an evidence photo (plan.md Section 7).
 * [hash] is expected pre-shortened (e.g. "7b21e0c4…a93f"); this badge only renders it.
 */
@Composable
fun HashBadge(hash: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = hash,
            style = KooliKanakuMonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
