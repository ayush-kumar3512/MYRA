package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.AssistantStatus
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceVisualizerOrb(
    status: AssistantStatus,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val primaryColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> Color(0xFF818CF8) // Soft Indigo
            AssistantStatus.CONNECTING -> Color(0xFFF59E0B) // Amber
            AssistantStatus.LISTENING -> Color(0xFF06B6D4) // Electric Cyan
            AssistantStatus.SPEAKING -> Color(0xFFEC4899) // Hot Magenta
            AssistantStatus.PROCESSING -> Color(0xFFA855F7) // Radiant Violet
            AssistantStatus.ERROR -> Color(0xFFEF4444) // Coral Red
        },
        label = "primary_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> Color(0xFFC084FC) // Soft Purple
            AssistantStatus.CONNECTING -> Color(0xFFFB923C) // Warm Orange
            AssistantStatus.LISTENING -> Color(0xFF3B82F6) // Neon Blue
            AssistantStatus.SPEAKING -> Color(0xFF8B5CF6) // Deep Violet
            AssistantStatus.PROCESSING -> Color(0xFFD946EF) // Fuchsia
            AssistantStatus.ERROR -> Color(0xFFF87171) // Light Red
        },
        label = "secondary_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(260.dp)
            .testTag("voice_visualizer_orb")
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 4.2f) * breathingScale
            val dynamicRadius = baseRadius + (amplitude * 55f)

            // 1. Outer ambient glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f + (amplitude * 0.4f).coerceAtMost(0.5f)),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.8f
                ),
                radius = dynamicRadius * 1.8f,
                center = center
            )

            // 2. Expanding acoustic ripple wave (when active)
            if (status == AssistantStatus.LISTENING || status == AssistantStatus.SPEAKING) {
                val rippleRadius = dynamicRadius + (pulsePhase * 45f)
                val rippleAlpha = (1f - pulsePhase) * 0.4f
                drawCircle(
                    color = primaryColor.copy(alpha = rippleAlpha),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // 3. Rotating orbital sound wave arcs
            val numArcs = 8
            for (i in 0 until numArcs) {
                val angleRad = Math.toRadians((rotation + (i * (360f / numArcs))).toDouble())
                val arcRadius = dynamicRadius * 1.15f + (sin(angleRad * 3) * 6f).toFloat() * (1f + amplitude * 2f)
                val startX = center.x + (cos(angleRad) * (arcRadius - 12f)).toFloat()
                val startY = center.y + (sin(angleRad) * (arcRadius - 12f)).toFloat()
                val endX = center.x + (cos(angleRad) * (arcRadius + 12f + (amplitude * 35f))).toFloat()
                val endY = center.y + (sin(angleRad) * (arcRadius + 12f + (amplitude * 35f))).toFloat()

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = (3.5f + (amplitude * 6f)).dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Central high-energy luminous core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.95f)
                    ),
                    center = center,
                    radius = dynamicRadius
                ),
                radius = dynamicRadius,
                center = center
            )

            // 5. Specular highlight for 3D glassy orb feel
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - dynamicRadius * 0.35f, center.y - dynamicRadius * 0.35f),
                    radius = dynamicRadius * 0.45f
                ),
                radius = dynamicRadius * 0.45f,
                center = Offset(center.x - dynamicRadius * 0.35f, center.y - dynamicRadius * 0.35f)
            )
        }
    }
}
