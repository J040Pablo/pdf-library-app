package com.example.library.screens.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.library.R
import com.example.library.model.PageAnimationType
import com.example.library.ui.theme.Spacing

private data class PageAnimOption(
    val type: PageAnimationType,
    val labelRes: Int,
    val icon: ImageVector
)

/**
 * Horizontal Material 3 segmented control for page-turn effect.
 * Sliding primary indicator + icon/label emphasis on selection.
 */
@Composable
fun PageTurnEffectSetting(
    selected: PageAnimationType,
    onSelected: (PageAnimationType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        PageAnimOption(
            type = PageAnimationType.NONE,
            labelRes = R.string.page_anim_none_short,
            icon = Icons.Outlined.Block
        ),
        PageAnimOption(
            type = PageAnimationType.SLIDE,
            labelRes = R.string.page_anim_slide_short,
            icon = Icons.Outlined.Swipe
        ),
        PageAnimOption(
            type = PageAnimationType.CURL_FOLD,
            labelRes = R.string.page_anim_fold_short,
            icon = Icons.AutoMirrored.Outlined.MenuBook
        )
    )
    val selectedIndex = options.indexOfFirst { it.type == selected }.coerceAtLeast(0)

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
            modifier = Modifier.padding(bottom = Spacing.SMedium)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .selectableGroup()
                .padding(4.dp)
        ) {
            val segmentWidth = maxWidth / options.size
            val indicatorOffset = remember { Animatable(selectedIndex.toFloat()) }

            LaunchedEffect(selectedIndex) {
                indicatorOffset.animateTo(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }

            // Sliding primary highlight.
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * indicatorOffset.value)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )

            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "pageAnimIconScale"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "pageAnimContentColor"
                    )

                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { onSelected(option.type) }
                            )
                            .semantics { semanticsSelected = isSelected },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
