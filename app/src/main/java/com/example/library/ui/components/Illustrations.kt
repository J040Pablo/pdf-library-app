package com.example.library.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ReadingIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.size(280.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2

        // Tablet background
        drawRoundRect(
            color = secondaryColor,
            topLeft = Offset(centerX - 80, centerY - 60 + floatOffset),
            size = Size(160f, 220f),
            cornerRadius = CornerRadius(16f, 16f)
        )
        
        // Tablet screen
        drawRoundRect(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = Offset(centerX - 70, centerY - 50 + floatOffset),
            size = Size(140f, 200f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Open Book
        val bookPath = Path().apply {
            moveTo(centerX - 100, centerY + 20)
            quadraticBezierTo(centerX - 50, centerY - 20, centerX, centerY + 20)
            quadraticBezierTo(centerX + 50, centerY - 20, centerX + 100, centerY + 20)
            lineTo(centerX + 100, centerY + 100)
            quadraticBezierTo(centerX + 50, centerY + 60, centerX, centerY + 100)
            quadraticBezierTo(centerX - 50, centerY + 60, centerX - 100, centerY + 100)
            close()
        }

        drawPath(
            path = bookPath,
            color = primaryColor
        )
        
        // Book lines (pages)
        drawPath(
            path = bookPath,
            color = Color.White.copy(alpha = 0.3f),
            style = Stroke(width = 2f)
        )
        
        // Floating elements (circles representing generic data/reading)
        drawCircle(
            color = primaryColor.copy(alpha = 0.6f),
            radius = 12f,
            center = Offset(centerX + 80, centerY - 80 - floatOffset)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.4f),
            radius = 8f,
            center = Offset(centerX - 90, centerY - 40 + floatOffset)
        )
    }
}
