package com.example.library.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.library.R
import com.example.library.model.PageAnimationType
import com.example.library.ui.theme.Spacing

/**
 * Page-turn effect picker for Settings.
 */
@Composable
fun PageTurnEffectSetting(
    selected: PageAnimationType,
    onSelected: (PageAnimationType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.page_turn_effect),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.Small)
        )
        Text(
            text = stringResource(R.string.page_turn_effect_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.Small)
        )

        Column(Modifier.selectableGroup()) {
            PageAnimationOptionRow(
                label = stringResource(R.string.page_anim_none),
                description = null,
                selected = selected == PageAnimationType.NONE,
                onClick = { onSelected(PageAnimationType.NONE) }
            )
            PageAnimationOptionRow(
                label = stringResource(R.string.page_anim_slide),
                description = null,
                selected = selected == PageAnimationType.SLIDE,
                onClick = { onSelected(PageAnimationType.SLIDE) }
            )
            PageAnimationOptionRow(
                label = stringResource(R.string.page_anim_curl),
                description = stringResource(R.string.page_anim_curl_description),
                selected = selected == PageAnimationType.CURL_FOLD,
                onClick = { onSelected(PageAnimationType.CURL_FOLD) }
            )
        }
    }
}

@Composable
private fun PageAnimationOptionRow(
    label: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = Spacing.SMedium),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
