package com.example.library.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular reading progress that animates as [progress] changes.
 * At 100% the ring closes and transitions into a checkmark with a subtle pulse.
 * [onCompleted] fires only when progress newly reaches 100% (not on first composition).
 */
@Composable
fun ReadingProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    checkColor: Color = progressColor,
    onCompleted: (() -> Unit)? = null
) {
    val target = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "readingProgress"
    )
    val isComplete = target >= 1f

    val completionScale = remember { Animatable(1f) }
    var wasComplete by remember { mutableStateOf(isComplete) }

    LaunchedEffect(isComplete) {
        if (isComplete && !wasComplete) {
            completionScale.snapTo(0.85f)
            completionScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            onCompleted?.invoke()
        } else if (!isComplete) {
            completionScale.snapTo(1f)
        }
        wasComplete = isComplete
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = completionScale.value
                scaleY = completionScale.value
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = stroke
            )
        }

        AnimatedContent(
            targetState = isComplete,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.6f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.8f))
            },
            label = "progressCheckmark"
        ) { complete ->
            if (complete) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = checkColor,
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        }
    }
}

/**
 * Compact chapter-level read control: empty ring, in-progress fill, or checkmark when read.
 */
@Composable
fun ChapterReadIndicator(
    isRead: Boolean,
    chapterProgress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 2.5.dp
) {
    val progress = when {
        isRead -> 1f
        else -> chapterProgress.coerceIn(0f, 1f)
    }
    ReadingProgressRing(
        progress = progress,
        modifier = modifier,
        size = size,
        strokeWidth = strokeWidth
    )
}
