package com.example.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NeonFaceState {
    NEUTRAL,
    SUCCESS,
    FAILURE
}

val NeonGreen = Color(0xFF00FF66)
val NeonGreenGlow = Color(0x3300FF66)
val NeonRed = Color(0xFFFF3344)
val NeonRedGlow = Color(0x33FF3344)
val NeonNeutral = Color(0xCCFFD700)

/**
 * Custom neon vector face illustration matching the design specification:
 * - Circular face outline with subtle neon glow.
 * - Two simple vertical pill eyes.
 * - A minimal curved nose.
 * - Dynamic mouth curving smoothly upward for SUCCESS (neon green) and downward for FAILURE (neon red).
 * - Identical dimensions, eye/nose positions, and stroke widths across states.
 */
@Composable
fun NeonFaceFeedback(
    state: NeonFaceState,
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    // Expression progress: 0f = neutral, 1f = smile, -1f = sad
    val mouthCurvature = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(state) {
        when (state) {
            NeonFaceState.NEUTRAL -> {
                mouthCurvature.animateTo(0f, animationSpec = tween(250, easing = FastOutSlowInEasing))
                glowAlpha.animateTo(0f, animationSpec = tween(200))
                scale.snapTo(1f)
            }
            NeonFaceState.SUCCESS -> {
                glowAlpha.animateTo(1f, animationSpec = tween(200))
                scale.animateTo(1.08f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                mouthCurvature.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
                scale.animateTo(1.0f, animationSpec = tween(150))
            }
            NeonFaceState.FAILURE -> {
                glowAlpha.animateTo(1f, animationSpec = tween(200))
                scale.animateTo(0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                mouthCurvature.animateTo(-1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
                scale.animateTo(1.0f, animationSpec = tween(150))
            }
        }
    }

    val strokeColor = when (state) {
        NeonFaceState.SUCCESS -> NeonGreen
        NeonFaceState.FAILURE -> NeonRed
        NeonFaceState.NEUTRAL -> NeonNeutral
    }

    val glowColor = when (state) {
        NeonFaceState.SUCCESS -> NeonGreenGlow
        NeonFaceState.FAILURE -> NeonRedGlow
        NeonFaceState.NEUTRAL -> Color.Transparent
    }

    val currentCurvature = mouthCurvature.value

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0F0F12))
            .border(
                width = 2.dp,
                brush = Brush.radialGradient(
                    colors = listOf(strokeColor.copy(alpha = 0.8f), strokeColor.copy(alpha = 0.2f))
                ),
                shape = CircleShape
            )
            .testTag(
                when (state) {
                    NeonFaceState.SUCCESS -> "neon_face_success"
                    NeonFaceState.FAILURE -> "neon_face_failure"
                    NeonFaceState.NEUTRAL -> "neon_face_neutral"
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = this.size.width
            val height = this.size.height
            val strokeWidth = width * 0.045f

            // Outer Neon Glow circle
            if (glowAlpha.value > 0f) {
                drawCircle(
                    color = glowColor.copy(alpha = glowAlpha.value * 0.4f),
                    radius = (width / 2f) - (strokeWidth * 0.5f)
                )
            }

            // Outer Face Outline Ring
            drawCircle(
                color = strokeColor,
                radius = (width / 2f) - (strokeWidth * 1.5f),
                style = Stroke(width = strokeWidth)
            )

            // Two simple vertical eyes
            val eyeWidth = strokeWidth * 1.1f
            val eyeHeight = height * 0.16f
            val eyeY = height * 0.30f
            val eyeLeftX = width * 0.35f
            val eyeRightX = width * 0.65f

            // Left eye
            drawLine(
                color = strokeColor,
                start = Offset(eyeLeftX, eyeY),
                end = Offset(eyeLeftX, eyeY + eyeHeight),
                strokeWidth = eyeWidth,
                cap = StrokeCap.Round
            )

            // Right eye
            drawLine(
                color = strokeColor,
                start = Offset(eyeRightX, eyeY),
                end = Offset(eyeRightX, eyeY + eyeHeight),
                strokeWidth = eyeWidth,
                cap = StrokeCap.Round
            )

            // Minimal curved nose (gentle arc or hook in the center)
            val nosePath = Path().apply {
                val noseStartX = width * 0.49f
                val noseStartY = height * 0.48f
                val noseEndY = height * 0.57f
                val noseCurveX = width * 0.54f

                moveTo(noseStartX, noseStartY)
                quadraticTo(
                    noseCurveX, (noseStartY + noseEndY) / 2f,
                    width * 0.48f, noseEndY
                )
            }
            drawPath(
                path = nosePath,
                color = strokeColor,
                style = Stroke(
                    width = strokeWidth * 0.85f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Curved Mouth:
            // Left corner at 0.32w, Right corner at 0.68w, baseline at 0.72h
            // Control point varies from baseline + arc (for smile) to baseline - arc (for sad)
            val mouthLeft = Offset(width * 0.33f, height * 0.71f)
            val mouthRight = Offset(width * 0.67f, height * 0.71f)
            val maxArcOffset = height * 0.14f
            val controlPointY = height * 0.71f + (currentCurvature * maxArcOffset)

            val mouthPath = Path().apply {
                moveTo(mouthLeft.x, mouthLeft.y)
                quadraticTo(
                    width * 0.50f, controlPointY,
                    mouthRight.x, mouthRight.y
                )
            }

            drawPath(
                path = mouthPath,
                color = strokeColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
