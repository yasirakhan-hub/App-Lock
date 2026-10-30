package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable PIN Indicator dots matching Panel 2 & Panel 10 of the reference design:
 * Filled dots glow with warm gold; empty dots are recessed dark circles.
 */
@Composable
fun PinDots(
    pinLength: Int,
    enteredLength: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pinLength) { index ->
            val isFilled = index < enteredLength
            val dotBrush = when {
                isError -> Brush.radialGradient(
                    listOf(NeoPalette.ErrorRed, NeoPalette.ErrorRed.copy(alpha = 0.8f))
                )
                isFilled -> Brush.radialGradient(
                    listOf(NeoPalette.PrimaryGoldBright, NeoPalette.PrimaryGold, NeoPalette.SecondaryAmber)
                )
                else -> Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0E), Color(0xFF1A1A22))
                )
            }
            val borderColor = when {
                isError -> NeoPalette.ErrorRed
                isFilled -> Color(0xCCFFE066)
                else -> Color(0x38FFFFFF)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
                    .shadow(
                        elevation = if (isFilled) 6.dp else 1.dp,
                        shape = CircleShape,
                        ambientColor = if (isFilled) NeoPalette.PrimaryGold else Color.Black,
                        spotColor = if (isFilled) NeoPalette.PrimaryGoldBright else Color.Black
                    )
                    .clip(CircleShape)
                    .background(dotBrush)
                    .border(1.5.dp, borderColor, CircleShape)
            )
        }
    }
}

/**
 * Clean, tactile 3x4 circular Neomorphic numeric keypad for PIN entry (matching Panel 2 & Panel 10).
 */
@Composable
fun PinKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 70.dp,
    lockoutSeconds: Int = 0,
    extraLeftAction: @Composable (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isLockedOut = lockoutSeconds > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnimatedVisibility(
            visible = isLockedOut,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = NeoPalette.ErrorRed.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeoPalette.ErrorRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = "Lockout",
                        tint = NeoPalette.ErrorRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Too many attempts. Try again in ${lockoutSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = NeoPalette.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (digit in row) {
                    KeypadButton(
                        text = digit,
                        size = buttonSize,
                        enabled = !isLockedOut,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigitClick(digit)
                        },
                        modifier = Modifier.testTag("pin_key_$digit")
                    )
                }
            }
        }

        // Bottom row: extra action (glowing gold fingerprint button or recovery), 0, backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(buttonSize),
                contentAlignment = Alignment.Center
            ) {
                extraLeftAction?.invoke()
            }

            KeypadButton(
                text = "0",
                size = buttonSize,
                enabled = !isLockedOut,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigitClick("0")
                },
                modifier = Modifier.testTag("pin_key_0")
            )

            NeoCircularButton(
                onClick = onDeleteClick,
                enabled = !isLockedOut,
                size = buttonSize,
                backgroundColor = NeoPalette.SurfaceElevated,
                modifier = Modifier.testTag("pin_delete_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = if (isLockedOut) NeoPalette.TextSecondary.copy(alpha = 0.35f)
                    else NeoPalette.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCircularButton(
        onClick = onClick,
        enabled = enabled,
        size = size,
        backgroundColor = NeoPalette.SurfaceElevated,
        contentColor = NeoPalette.TextPrimary,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) NeoPalette.TextPrimary else NeoPalette.TextSecondary.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}
