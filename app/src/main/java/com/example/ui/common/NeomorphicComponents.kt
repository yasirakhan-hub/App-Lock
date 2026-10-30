package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Shared Premium Black & Gold/Yellow (and Light Warm White & Gold) Neomorphic color tokens.
 * Reactively updates across all screens when the Home Theme Switcher changes mode.
 */
object NeoPalette {
    var isDarkMode by mutableStateOf(true)

    private val DarkBackgroundBase = Color(0xFF0B0B0E)
    private val DarkSurface = Color(0xFF15151C)
    private val DarkSurfaceElevated = Color(0xFF1F1F29)
    private val DarkSurfaceSunken = Color(0xFF07070A)

    // Light-mode tokens: warm white / light grey with golden accents
    val LightBackgroundBase = Color(0xFFF6F5F2)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFFAF9F6)
    val LightSurfaceSunken = Color(0xFFE8E6DF)

    val BackgroundBase: Color
        get() = if (isDarkMode) DarkBackgroundBase else LightBackgroundBase

    val SurfaceDark: Color
        get() = if (isDarkMode) DarkSurface else LightSurface

    val SurfaceElevated: Color
        get() = if (isDarkMode) DarkSurfaceElevated else LightSurfaceElevated

    val SurfaceSunken: Color
        get() = if (isDarkMode) DarkSurfaceSunken else LightSurfaceSunken

    val ShadowLightEdge = Color(0x33F5B800)
    val ShadowLightSubtle = Color(0x22FFFFFF)
    val ShadowDarkEdge: Color
        get() = if (isDarkMode) Color(0xBF000000) else Color(0x261C1917)
    val BorderSubtle: Color
        get() = if (isDarkMode) Color(0x24FFFFFF) else Color(0x1F1C1917)

    // Primary Gold & Secondary Amber tokens
    val PrimaryGold = Color(0xFFF5B800)
    val PrimaryGoldBright = Color(0xFFFFD43B)
    val SecondaryAmber = Color(0xFFD97706)
    val GoldGlowSoft = Color(0x38F5B800)
    val DarkBadgeSurface: Color
        get() = if (isDarkMode) Color(0xFF211C10) else Color(0xFFFFF8E1)
    val DarkBadgeBorder = Color(0x59F5B800)

    // Legacy token names mapped to Gold/Amber for seamless consistency across all screens
    val PrimaryCyan = PrimaryGold
    val PrimaryBlue = SecondaryAmber
    val AccentGold = Color(0xFFFBBF24)
    val AccentTeal = PrimaryGold

    val SuccessGreen = Color(0xFF10B981)
    val WarningAmber = Color(0xFFF59E0B)
    val ErrorRed = Color(0xFFEF4444)

    val SurfaceLight: Color
        get() = if (isDarkMode) Color(0xFF22222C) else Color(0xFFF0EFEA)
    val SpecularBorder = Color(0x38FFFFFF)

    val TextPrimary: Color
        get() = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF17171C)
    val TextSecondary: Color
        get() = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF57534E)
    val TextTertiary: Color
        get() = if (isDarkMode) Color(0xFF6B7280) else Color(0xFF78716C)
    val TextInverse = Color(0xFF0B0B0E)
}

/**
 * Shared corner shapes matching the Black & Gold Neomorphism reference design.
 */
object NeoShapes {
    val FullPill = RoundedCornerShape(percent = 50)
    val LargeCard = RoundedCornerShape(22.dp)
    val MediumCard = RoundedCornerShape(16.dp)
    val SmallControl = RoundedCornerShape(12.dp)
}

/**
 * Four primary bottom navigation destinations in strict order:
 * 1. Home
 * 2. Apps
 * 3. Files
 * 4. Settings
 */
enum class MainBottomTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_bottom_home"),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps, "nav_bottom_apps"),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder, "nav_bottom_files"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_bottom_settings")
}

/**
 * Global 4-Destination Neomorphic Bottom Navigation Bar matching the primary reference image.
 */
@Composable
fun NeoBottomNavigationBar(
    currentTab: MainBottomTab?,
    onSelectTab: (MainBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = NeoPalette.isDarkMode
    val barShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 22.dp, bottomEnd = 22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = 10.dp,
                shape = barShape,
                ambientColor = NeoPalette.ShadowDarkEdge,
                spotColor = NeoPalette.ShadowLightEdge
            )
            .clip(barShape)
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF1C1C24),
                            Color(0xFF101015)
                        )
                    } else {
                        listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF0EFEA)
                        )
                    }
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(
                                Color(0x38FFFFFF),
                                Color(0x14F5B800),
                                Color(0x99000000)
                            )
                        } else {
                            listOf(
                                Color(0xCCFFFFFF),
                                Color(0x33F5B800),
                                Color(0x221C1917)
                            )
                        }
                    )
                ),
                barShape
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("global_bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, color = NeoPalette.PrimaryGold),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTab(tab)
                            }
                        )
                        .padding(vertical = 4.dp)
                        .testTag(tab.testTag)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                NeoPalette.PrimaryGold.copy(alpha = 0.28f),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                            )
                        }
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = if (isSelected) NeoPalette.PrimaryGold else NeoPalette.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NeoPalette.PrimaryGold else NeoPalette.TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Circular Neomorphic Icon Badge with warm dark-gold inner glow used on Home, Settings, and Auth screens.
 */
@Composable
fun NeoIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = NeoPalette.PrimaryGold,
    isGlowing: Boolean = true
) {
    val isDark = NeoPalette.isDarkMode
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (isGlowing) 6.dp else 2.dp,
                shape = CircleShape,
                ambientColor = if (isGlowing) NeoPalette.PrimaryGold.copy(alpha = 0.35f) else NeoPalette.ShadowDarkEdge,
                spotColor = if (isGlowing) NeoPalette.PrimaryGold.copy(alpha = 0.55f) else NeoPalette.ShadowDarkEdge
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isGlowing && isDark) {
                        listOf(
                            Color(0xFF33280B),
                            Color(0xFF171512)
                        )
                    } else if (isGlowing) {
                        listOf(
                            Color(0xFFFFF6D6),
                            Color(0xFFF5EFE0)
                        )
                    } else {
                        listOf(
                            NeoPalette.SurfaceElevated,
                            NeoPalette.SurfaceDark
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isGlowing) {
                        listOf(
                            NeoPalette.PrimaryGold.copy(alpha = 0.65f),
                            NeoPalette.PrimaryGold.copy(alpha = 0.18f)
                        )
                    } else {
                        listOf(
                            Color(0x30FFFFFF),
                            Color(0x40000000)
                        )
                    }
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Official Smart AppLock S-Lock Emblem inside a raised Neomorphic circular badge.
 * Uses the exact attached S-lock logo image (`R.drawable.smart_applock_logo`).
 */
@Composable
fun NeoGoldPadlockEmblem(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 88.dp,
    isOpenShackle: Boolean = true
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = NeoPalette.PrimaryGold.copy(alpha = 0.45f),
                spotColor = NeoPalette.PrimaryGold.copy(alpha = 0.65f)
            )
            .clip(CircleShape)
            .background(Color(0xFF0D0E11))
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NeoPalette.PrimaryGoldBright.copy(alpha = 0.75f),
                        NeoPalette.PrimaryGold.copy(alpha = 0.25f),
                        Color(0x99000000)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.smart_applock_logo),
            contentDescription = "Smart AppLock Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

/**
 * Two-tone "Smart AppLock" brand text ("Smart " in white + "AppLock" in warm gold).
 */
@Composable
fun NeoBrandTitle(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = NeoPalette.TextPrimary, fontWeight = FontWeight.Bold)) {
                append("Smart ")
            }
            withStyle(SpanStyle(color = NeoPalette.PrimaryGold, fontWeight = FontWeight.ExtraBold)) {
                append("AppLock")
            }
        },
        fontSize = fontSize,
        modifier = modifier
    )
}

/**
 * Tactile Neomorphic Card with soft dual-tone surface gradient and bevel highlights.
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.MediumCard,
    backgroundColor: Color = Color.Unspecified,
    accentBorder: Color? = null,
    elevation: Dp = 5.dp,
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

    val resolvedBase = if (isDark) Color(0xFF101015) else NeoPalette.LightSurfaceElevated
    val resolvedSunken = if (isDark) NeoPalette.SurfaceSunken else NeoPalette.LightSurfaceSunken

    val surfaceBrush = if (isPressed) {
        Brush.verticalGradient(
            colors = listOf(
                resolvedSunken,
                resolvedBg.copy(alpha = 0.94f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                resolvedBg,
                resolvedBase
            )
        )
    }

    val borderStroke = when {
        accentBorder != null -> BorderStroke(1.2.dp, accentBorder)
        isPressed -> BorderStroke(1.dp, if (isDark) Color(0x18F5B800) else Color(0x20000000))
        isDark -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color(0x28FFFFFF),
                    Color(0x14F5B800),
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
            indication = ripple(bounded = true, color = NeoPalette.PrimaryGold),
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
 * Tactile Neomorphic Button with warm gold primary state or dark charcoal secondary state.
 */
@Composable
fun NeoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.FullPill,
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
            listOf(NeoPalette.ErrorRed.copy(alpha = 0.22f), surfaceSunken)
        )
        isPrimaryGlowing && isPressed -> Brush.verticalGradient(
            listOf(NeoPalette.PrimaryGold, NeoPalette.SecondaryAmber)
        )
        isPrimaryGlowing -> Brush.verticalGradient(
            listOf(NeoPalette.PrimaryGoldBright, NeoPalette.PrimaryGold, Color(0xFFE09F00))
        )
        isPressed -> Brush.verticalGradient(listOf(surfaceSunken, surfaceBottom))
        else -> Brush.verticalGradient(listOf(surfaceTop, surfaceBottom))
    }

    val borderStroke = when {
        !enabled -> BorderStroke(1.dp, if (isDark) Color(0x10FFFFFF) else Color(0x150F172A))
        isDestructive -> BorderStroke(1.dp, NeoPalette.ErrorRed.copy(alpha = 0.55f))
        isPrimaryGlowing -> BorderStroke(1.dp, Color(0x99FFF3B0))
        isDark -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(NeoPalette.ShadowLightSubtle, Color(0x10F5B800), NeoPalette.ShadowDarkEdge)
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
                elevation = if (isPrimaryGlowing && enabled) 8.dp else if (isPressed) 1.dp else 4.dp,
                shape = shape,
                ambientColor = if (isPrimaryGlowing) NeoPalette.PrimaryGold.copy(alpha = 0.45f) else if (isDark) NeoPalette.ShadowDarkEdge else Color(0x260F172A),
                spotColor = if (isPrimaryGlowing) NeoPalette.PrimaryGold else NeoPalette.ShadowLightEdge
            )
            .clip(shape)
            .background(bgBrush)
            .border(borderStroke, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = if (isPrimaryGlowing) NeoPalette.TextInverse else NeoPalette.PrimaryGold),
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
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Convenience Neomorphic Button overload taking text and optional icon.
 * Uses high-contrast dark charcoal text on glowing gold buttons and soft white text on dark buttons.
 */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = NeoShapes.FullPill,
    isPrimaryGlowing: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val defaultTextColor = if (isDark) NeoPalette.TextPrimary else MaterialTheme.colorScheme.onSurface
    val contentColor = when {
        isDestructive -> NeoPalette.ErrorRed
        isPrimaryGlowing -> NeoPalette.TextInverse
        else -> defaultTextColor
    }

    NeoButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        isPrimaryGlowing = isPrimaryGlowing,
        isDestructive = isDestructive,
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) NeoPalette.ErrorRed else if (isPrimaryGlowing) NeoPalette.TextInverse else NeoPalette.PrimaryGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = contentColor
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
    isGoldSolid: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val resolvedBg = when {
        isGoldSolid -> NeoPalette.PrimaryGoldBright
        backgroundColor != Color.Unspecified -> backgroundColor
        isDark -> NeoPalette.SurfaceElevated
        else -> NeoPalette.LightSurface
    }
    val resolvedBottom = when {
        isGoldSolid -> NeoPalette.SecondaryAmber
        backgroundColor != Color.Unspecified -> Color(
            red = (backgroundColor.red * 0.72f).coerceIn(0f, 1f),
            green = (backgroundColor.green * 0.72f).coerceIn(0f, 1f),
            blue = (backgroundColor.blue * 0.72f).coerceIn(0f, 1f),
            alpha = backgroundColor.alpha
        )
        isDark -> NeoPalette.SurfaceDark
        else -> NeoPalette.LightSurfaceSunken
    }
    val resolvedSunken = if (isGoldSolid) {
        NeoPalette.SecondaryAmber
    } else if (backgroundColor != Color.Unspecified || isDark) {
        NeoPalette.SurfaceSunken
    } else {
        NeoPalette.LightSurfaceSunken
    }

    val brush = if (isPressed) {
        Brush.radialGradient(
            colors = listOf(
                resolvedSunken,
                resolvedBg.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                resolvedBg,
                resolvedBottom
            )
        )
    }

    val effectiveGlow = if (isGoldSolid) NeoPalette.PrimaryGold else accentGlow

    val borderStroke = when {
        isGoldSolid -> BorderStroke(1.5.dp, Color(0xAAFFF3B0))
        effectiveGlow != null -> BorderStroke(1.5.dp, effectiveGlow)
        isPressed -> BorderStroke(1.dp, if (isDark) Color(0x22F5B800) else Color(0x200F172A))
        else -> BorderStroke(
            1.dp,
            Brush.linearGradient(
                if (backgroundColor != Color.Unspecified || isDark) {
                    listOf(Color(0x32FFFFFF), Color(0x10F5B800), Color(0x99000000))
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
                elevation = if (isPressed) 1.dp else if (isGoldSolid) 8.dp else 5.dp,
                shape = CircleShape,
                ambientColor = effectiveGlow?.copy(alpha = 0.45f) ?: if (isDark) NeoPalette.ShadowDarkEdge else Color(0x260F172A),
                spotColor = effectiveGlow?.copy(alpha = 0.7f) ?: NeoPalette.ShadowLightEdge
            )
            .clip(CircleShape)
            .background(brush)
            .border(borderStroke, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = size / 2, color = effectiveGlow ?: NeoPalette.PrimaryGold),
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
    val bottomColor = if (isDark) NeoPalette.SurfaceDark.copy(alpha = 0.75f) else NeoPalette.LightSurfaceElevated

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
                        listOf(Color(0x80000000), Color(0x1AF5B800))
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
    activeColor: Color = NeoPalette.PrimaryGold,
    inactiveColor: Color = NeoPalette.WarningAmber
) {
    val color = if (isActive) activeColor else inactiveColor
    Surface(
        shape = NeoShapes.FullPill,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
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
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
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
