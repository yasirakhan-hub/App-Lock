package com.example.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared Neomorphic color tokens aligned with UI_DESIGN_SYSTEM.md.
 */
object NeoPalette {
    val BackgroundBase = Color(0xFF0A0F1D)
    val SurfaceDark = Color(0xFF0F172A)
    val SurfaceElevated = Color(0xFF1E293B)
    val SurfaceSunken = Color(0xFF070B14)

    // Light-mode Neomorphic surface tokens
    val LightBackgroundBase = Color(0xFFF1F5F9)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFF8FAFC)
    val LightSurfaceSunken = Color(0xFFE2E8F0)

    val ShadowLightEdge = Color(0x2638BDF8)
    val ShadowLightSubtle = Color(0x1FFFFFFF)
    val ShadowDarkEdge = Color(0x80000000)
    val BorderSubtle = Color(0x1FFFFFFF)

    val PrimaryCyan = Color(0xFF06B6D4)
    val PrimaryBlue = Color(0xFF38BDF8)
    val AccentGold = Color(0xFFFBBF24)
    val AccentTeal = Color(0xFF14B8A6)

    val SuccessGreen = Color(0xFF10B981)
    val WarningAmber = Color(0xFFF59E0B)
    val ErrorRed = Color(0xFFEF4444)

    val SurfaceLight = Color(0xFF1E293B)
    val SpecularBorder = Color(0x35FFFFFF)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)
    val TextInverse = Color(0xFF0A0F1D)
}

/**
 * Shared corner shapes.
 */
object NeoShapes {
    val FullPill = RoundedCornerShape(percent = 50)
    val LargeCard = RoundedCornerShape(20.dp)
    val MediumCard = RoundedCornerShape(14.dp)
    val SmallControl = RoundedCornerShape(10.dp)
}

/**
 * Tactile Neomorphic Card with soft dual-tone surface gradient and bevel highlights.
 * Automatically adapts to Dark/Light theme when using the default backgroundColor.
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.MediumCard,
    backgroundColor: Color = Color.Unspecified,
    accentBorder: Color? = null,
    elevation: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val resolvedBg = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else if (isDark) {
        NeoPalette.SurfaceDark
    } else {
        NeoPalette.LightSurface
    }

    val resolvedBase = if (isDark) NeoPalette.BackgroundBase else NeoPalette.LightSurfaceElevated
    val resolvedSunken = if (isDark) NeoPalette.SurfaceSunken else NeoPalette.LightSurfaceSunken

    val surfaceBrush = if (isPressed) {
        Brush.verticalGradient(
            colors = listOf(
                resolvedSunken,
                resolvedBg.copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                resolvedBg.copy(alpha = 0.96f),
                resolvedBase.copy(alpha = 0.92f)
            )
        )
    }

    val borderStroke = when {
        accentBorder != null -> BorderStroke(1.2.dp, accentBorder)
        isPressed -> BorderStroke(1.dp, if (isDark) Color(0x10FFFFFF) else Color(0x20000000))
        isDark -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    NeoPalette.ShadowLightSubtle,
                    Color(0x05FFFFFF),
                    NeoPalette.ShadowDarkEdge
                )
            )
        )
        else -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xCCFFFFFF),
                    Color(0x140F172A),
                    Color(0x240F172A)
                )
            )
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = NeoPalette.PrimaryCyan),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isPressed) 1.dp else elevation,
                shape = shape,
                ambientColor = if (isDark) NeoPalette.ShadowDarkEdge else Color(0x260F172A),
                spotColor = NeoPalette.ShadowLightEdge
            )
            .clip(shape)
            .background(surfaceBrush)
            .border(borderStroke, shape)
            .then(clickableModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Tactile Neomorphic Button with illuminated specular border.
 */
@Composable
fun NeoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.MediumCard,
    isPrimaryGlowing: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val surfaceTop = if (isDark) NeoPalette.SurfaceElevated else NeoPalette.LightSurface
    val surfaceBottom = if (isDark) NeoPalette.SurfaceDark else NeoPalette.LightSurfaceSunken
    val surfaceSunken = if (isDark) NeoPalette.SurfaceSunken else NeoPalette.LightSurfaceSunken

    val bgBrush = when {
        !enabled -> Brush.verticalGradient(
            listOf(surfaceTop.copy(alpha = 0.45f), surfaceBottom.copy(alpha = 0.45f))
        )
        isDestructive -> Brush.verticalGradient(
            listOf(NeoPalette.ErrorRed.copy(alpha = 0.16f), surfaceSunken)
        )
        isPrimaryGlowing && isPressed -> Brush.verticalGradient(
            listOf(NeoPalette.PrimaryCyan.copy(alpha = 0.8f), NeoPalette.PrimaryBlue.copy(alpha = 0.9f))
        )
        isPrimaryGlowing -> Brush.verticalGradient(
            listOf(NeoPalette.PrimaryCyan, NeoPalette.PrimaryBlue)
        )
        isPressed -> Brush.verticalGradient(listOf(surfaceSunken, surfaceBottom))
        else -> Brush.verticalGradient(listOf(surfaceTop, surfaceBottom))
    }

    val borderStroke = when {
        !enabled -> BorderStroke(1.dp, if (isDark) Color(0x10FFFFFF) else Color(0x150F172A))
        isDestructive -> BorderStroke(1.dp, NeoPalette.ErrorRed.copy(alpha = 0.5f))
        isPrimaryGlowing -> BorderStroke(1.dp, Color(0x60FFFFFF))
        isDark -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(NeoPalette.ShadowLightSubtle, Color(0x05FFFFFF), NeoPalette.ShadowDarkEdge)
            )
        )
        else -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(Color(0xCCFFFFFF), Color(0x180F172A), Color(0x280F172A))
            )
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isPrimaryGlowing && enabled) 6.dp else if (isPressed) 1.dp else 3.dp,
                shape = shape,
                ambientColor = if (isPrimaryGlowing) NeoPalette.PrimaryCyan.copy(alpha = 0.4f) else if (isDark) NeoPalette.ShadowDarkEdge else Color(0x260F172A),
                spotColor = if (isPrimaryGlowing) NeoPalette.PrimaryCyan else NeoPalette.ShadowLightEdge
            )
            .clip(shape)
            .background(bgBrush)
            .border(borderStroke, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = if (isPrimaryGlowing) Color.White else NeoPalette.PrimaryCyan),
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Convenience Neomorphic Button overload taking text and optional icon.
 */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = NeoShapes.MediumCard,
    isPrimaryGlowing: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val defaultTextColor = if (isDark) NeoPalette.TextPrimary else MaterialTheme.colorScheme.onSurface

    NeoButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        isPrimaryGlowing = isPrimaryGlowing,
        isDestructive = isDestructive,
        enabled = enabled
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) NeoPalette.ErrorRed else if (isPrimaryGlowing) Color.White else NeoPalette.PrimaryCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) NeoPalette.ErrorRed else if (isPrimaryGlowing) Color.White else defaultTextColor
        )
    }
}

/**
 * 3D Tactile Circular Button specifically crafted for Keypads and Calculators.
 */
@Composable
fun NeoCircularButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = NeoPalette.TextPrimary,
    accentGlow: Color? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val resolvedBg = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else if (isDark) {
        NeoPalette.SurfaceElevated
    } else {
        NeoPalette.LightSurface
    }
    val resolvedBottom = if (backgroundColor != Color.Unspecified || isDark) {
        NeoPalette.SurfaceDark
    } else {
        NeoPalette.LightSurfaceSunken
    }
    val resolvedSunken = if (backgroundColor != Color.Unspecified || isDark) {
        NeoPalette.SurfaceSunken
    } else {
        NeoPalette.LightSurfaceSunken
    }

    val brush = if (isPressed) {
        Brush.radialGradient(
            colors = listOf(
                resolvedSunken,
                resolvedBg.copy(alpha = 0.7f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                resolvedBg.copy(alpha = 0.95f),
                resolvedBottom
            )
        )
    }

    val borderStroke = when {
        accentGlow != null -> BorderStroke(1.5.dp, accentGlow)
        isPressed -> BorderStroke(1.dp, if (isDark) Color(0x15FFFFFF) else Color(0x200F172A))
        else -> BorderStroke(
            1.dp,
            Brush.linearGradient(
                if (backgroundColor != Color.Unspecified || isDark) {
                    listOf(Color(0x35FFFFFF), Color(0x05FFFFFF), Color(0x80000000))
                } else {
                    listOf(Color(0xCCFFFFFF), Color(0x150F172A), Color(0x300F172A))
                }
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (isPressed) 1.dp else 4.dp,
                shape = CircleShape,
                ambientColor = accentGlow?.copy(alpha = 0.3f) ?: if (isDark) NeoPalette.ShadowDarkEdge else Color(0x260F172A),
                spotColor = accentGlow?.copy(alpha = 0.6f) ?: NeoPalette.ShadowLightEdge
            )
            .clip(CircleShape)
            .background(brush)
            .border(borderStroke, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = size / 2, color = accentGlow ?: NeoPalette.PrimaryCyan),
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Sunken Inset Container for Text Fields, Segmented Controls, and Displays.
 */
@Composable
fun NeoInsetBox(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.SmallControl,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val topColor = if (isDark) NeoPalette.SurfaceSunken else NeoPalette.LightSurfaceSunken
    val bottomColor = if (isDark) NeoPalette.SurfaceDark.copy(alpha = 0.6f) else NeoPalette.LightSurfaceElevated

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(topColor, bottomColor)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color(0x40000000), Color(0x15FFFFFF))
                    } else {
                        listOf(Color(0x260F172A), Color(0xB3FFFFFF))
                    }
                ),
                shape
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * Tactile Status Pill Badge (Enabled / Action Needed / Protected).
 */
@Composable
fun NeoStatusBadge(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = NeoPalette.SuccessGreen,
    inactiveColor: Color = NeoPalette.WarningAmber
) {
    val color = if (isActive) activeColor else inactiveColor
    Surface(
        shape = NeoShapes.FullPill,
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Tactile Status Pill Badge accepting explicit Color.
 */
@Composable
fun NeoStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = NeoShapes.FullPill,
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
