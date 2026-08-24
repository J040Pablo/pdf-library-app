package com.example.library.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.library.R

/**
 * Animated icon that morphs smoothly between a 3-line hamburger menu and an X (close) icon.
 *
 * @param isOpen Whether the navigation drawer is open (X) or closed (hamburger).
 * @param onClick Action invoked when the icon is clicked.
 * @param modifier Modifier for positioning and styling.
 * @param tint Color of the icon lines. Defaults to [MaterialTheme.colorScheme.onBackground].
 * @param size Size of the icon button.
 */
@Composable
fun AnimatedHamburgerIcon(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    size: Dp = 48.dp
) {
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "HamburgerToX"
    )

    val contentDescription = if (isOpen) {
        stringResource(R.string.close)
    } else {
        stringResource(R.string.menu)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = size / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = 2.5.dp.toPx()

            rotate(degrees = progress * 180f, pivot = Offset(w / 2f, h / 2f)) {
                // Top line morphing into backslash (\)
                val topStartX = lerp(w * 0.2f, w * 0.25f, progress)
                val topStartY = lerp(h * 0.3f, h * 0.25f, progress)
                val topEndX = lerp(w * 0.8f, w * 0.75f, progress)
                val topEndY = lerp(h * 0.3f, h * 0.75f, progress)

                drawLine(
                    color = tint,
                    start = Offset(topStartX, topStartY),
                    end = Offset(topEndX, topEndY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Middle line shrinking and fading out
                val midAlpha = (1f - progress * 2.2f).coerceIn(0f, 1f)
                if (midAlpha > 0f) {
                    val midStartX = lerp(w * 0.2f, w * 0.5f, progress)
                    val midEndX = lerp(w * 0.8f, w * 0.5f, progress)

                    drawLine(
                        color = tint.copy(alpha = tint.alpha * midAlpha),
                        start = Offset(midStartX, h * 0.5f),
                        end = Offset(midEndX, h * 0.5f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Bottom line morphing into forward slash (/)
                val botStartX = lerp(w * 0.2f, w * 0.25f, progress)
                val botStartY = lerp(h * 0.7f, h * 0.75f, progress)
                val botEndX = lerp(w * 0.8f, w * 0.75f, progress)
                val botEndY = lerp(h * 0.7f, h * 0.25f, progress)

                drawLine(
                    color = tint,
                    start = Offset(botStartX, botStartY),
                    end = Offset(botEndX, botEndY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
