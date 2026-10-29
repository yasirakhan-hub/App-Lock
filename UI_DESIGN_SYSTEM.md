# UI_DESIGN_SYSTEM.md — Complete-App Neomorphism Design System

**Document Version:** 1.0.0  
**Status:** Phase 0 (Specification & Design System Definition)  
**Authority:** Governs visual hierarchy, color palette, surface elevation, typography, and accessibility  
**Framework:** Jetpack Compose, Material Design 3 (M3)  

---

## 1. Visual Philosophy & Identity

Smart AppLock employs a **Dark Modern Neomorphic** aesthetic. Unlike light-mode neomorphism (which often suffers from washed-out contrast and low legibility), dark neomorphism delivers physical depth, tactile luxury, and military-grade security confidence while maintaining full WCAG 2.1 AA accessibility contrast.

### Core Principles
1. **Tactile Physicality:** Interactive controls mimic physical hardware with subtle double-shadow highlights (top-left illumination, bottom-right soft ambient shadow).
2. **Restrained Elegance:** Surfaces utilize very dark navy and deep graphite hues rather than flat pitch-black, creating smooth depth gradients.
3. **High-Contrast Signal Colors:** Security actions, active switches, and primary confirmations utilize luminous cyan (`#06B6D4`) and royal blue (`#3B82F6`) accents.
4. **Accessibility First:** Contrast ratios exceed 4.5:1 for standard text and 3:1 for graphical UI components. All interactive targets adhere to the strict 48dp minimum touch target.
5. **No Color-Only State:** Statuses (Locked/Unlocked, Granted/Denied, Success/Error) always combine high-contrast icons, text labels, and structural cues.

---

## 2. Color Palette & Semantic Tokens

### Base Surface Tokens
```kotlin
object NeoPalette {
    // Backgrounds & Base Layers
    val BackgroundBase       = Color(0xFF0A0F1D) // Ultra-deep navy canvas
    val SurfaceDark          = Color(0xFF0F172A) // Raised card & container surface
    val SurfaceElevated      = Color(0xFF1E293B) // Floating modals & dialogs
    val SurfaceSunken        = Color(0xFF070B14) // Inset text fields & pressed states

    // Neomorphic Shadow & Highlight Tokens
    val ShadowLightEdge      = Color(0x1A38BDF8) // Top-left soft cyan-white specular highlight
    val ShadowLightSubtle    = Color(0x14FFFFFF) // Top-left neutral ambient highlight
    val ShadowDarkEdge       = Color(0x80000000) // Bottom-right deep ambient drop shadow
    val BorderSubtle         = Color(0x1FFFFFFF) // Crisp 1dp surface delineation

    // Accent Signals
    val PrimaryCyan          = Color(0xFF06B6D4) // Primary action & focus indicator
    val PrimaryBlue          = Color(0xFF3B82F6) // Secondary action & Danger Zone accent
    val AccentGold           = Color(0xFFFBBF24) // "ABOUT" section header & warning alerts
    val AccentTeal           = Color(0xFF14B8A6) // Calculator "AC" / Reset highlight

    // Semantic States
    val SuccessGreen         = Color(0xFF10B981) // Permission active / Protected status
    val WarningAmber         = Color(0xFFF59E0B) // Action needed / Critical system alert
    val ErrorRed             = Color(0xFFEF4444) // Lockout / Failed auth / Danger zone action

    // Typography & Content
    val TextPrimary          = Color(0xFFF8FAFC) // 100% white-slate high contrast
    val TextSecondary        = Color(0xFF94A3B8) // Muted label & descriptive text
    val TextTertiary         = Color(0xFF64748B) // Subtle hints & inactive icons
    val TextInverse          = Color(0xFF0A0F1D) // Dark text for luminous buttons
}
```

---

## 3. Neomorphic Elevation, Shadows & Corner Shapes

### Shadow Specifications
Neomorphic surfaces achieve depth by combining two directional shadows:
- **Raised Surfaces (Cards, Floating Buttons):**
  - Light Source (Top-Left): `offset(-3.dp, -3.dp)`, `blur = 6.dp`, color = `NeoPalette.ShadowLightSubtle`
  - Shadow (Bottom-Right): `offset(4.dp, 4.dp)`, `blur = 8.dp`, color = `NeoPalette.ShadowDarkEdge`
- **Sunken / Inset Surfaces (Text Fields, Pressed Buttons):**
  - Inner Top-Left Shadow: Inset dark shadow simulating depth beneath the canvas.
  - Inner Bottom-Right Highlight: Faint specular rim along the bottom bevel.

### Corner Radii Hierarchy
```kotlin
object NeoShapes {
    val FullPill    = RoundedCornerShape(percent = 50) // Floating action buttons, chips, pill toggles
    val LargeCard   = RoundedCornerShape(20.dp)        // Dashboard master cards, calculator container
    val MediumCard  = RoundedCornerShape(14.dp)        // Settings rows, app list items, dialog surfaces
    val SmallControl= RoundedCornerShape(10.dp)        // Input fields, small action chips, badges
}
```

---

## 4. Reusable Neomorphic UI Component Library

### 1. `NeoCard`
The standard container for grouping related items (e.g. Master Protection Card, Settings Row, Permission Card):
```kotlin
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.MediumCard,
    backgroundColor: Color = NeoPalette.SurfaceDark,
    isPressed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```
- **States:** Default (Raised), Pressed (Sunken depth animation on tap), Hover/Focused (Cyan outline glow).

### 2. `NeoButton`
Primary action button for confirmations, onboarding steps, and setup dialogs:
- **Variant A (Raised Glowing):** Background = `NeoPalette.PrimaryCyan`, Content = `NeoPalette.TextInverse`. Used for primary CTAs ("Set Up AppLock Now", "Continue").
- **Variant B (Dark Tactile):** Background = `NeoPalette.SurfaceDark`, Content = `NeoPalette.PrimaryCyan`. Used for secondary actions.
- **Variant C (Destructive Inset):** Background = `NeoPalette.SurfaceSunken`, Border = `NeoPalette.ErrorRed.copy(0.5f)`, Content = `NeoPalette.ErrorRed`. Used for Reset AppLock Data.

### 3. `NeoCircularButton`
Specially designed for numeric keypads (Lock Screen, Dashboard Gate, Calculator Disguise):
- **Dimensions:** Fixed 72dp diameter (comfortably exceeds the 48dp accessibility touch standard).
- **Surface:** Smooth 3D convex contour with centered high-legibility numerals (28sp semi-bold).
- **Haptics:** Integrates `HapticFeedbackType.TextHandleMove` on press and `HapticFeedbackType.LongPress` on delete.

### 4. `NeoInsetField`
For text inputs (Search, PIN Recovery Answer, Backup Passphrase):
- **Surface:** Sunken into canvas with dark top inner shadow.
- **Focus State:** 1.5dp border gradient fading into `NeoPalette.PrimaryCyan`.

### 5. `NeoSwitch`
For protection toggles and biometric switches:
- **Track:** Sunken pill shape with distinct contrast state.
- **Thumb:** Tactile raised disc with smooth sliding spring animation.

---

## 5. Screen-by-Screen Implementation Guide

### A. Dashboard Screen (`DashboardScreen.kt`)
- **Master Protection Card:** Large raised Neomorphic card (`NeoShapes.LargeCard`) with dynamic ambient cyan glow when protection is active.
- **Stat Cards:** 2-column tactile raised cards showing "Protected Apps" count and "Lock Simulator".
- **Permission Status Cards:** Tactile rows featuring green checkmark / amber warning badges with direct action buttons.
- **Header:** Integrated theme toggle (Light/Dark mode) and Settings navigation with smooth ripple feedback.

### B. App Selection Screen (`AppSelectionScreen.kt`)
- **Search Bar:** Sunken `NeoInsetField` with clear button.
- **Category Filter Chips:** Neomorphic pill buttons with sunken active state.
- **App List Rows:** Soft raised cards displaying app icon, label, system badge, and tactile protection toggle.

### C. Lock Overlay Screen (`LockActivity.kt`)
- **Fullscreen Ambience:** Dark navy base (`NeoPalette.BackgroundBase`) with edge-to-edge system bar integration.
- **Target App Header:** Elevated circular icon holder with ambient glow, app label, and lock state subtitle.
- **Keypad & Dots:** Tactile circular keypad buttons with instant reactive press states and smooth error shake animation.

### D. Security & Settings Screen (`SettingsScreen.kt`)
- **Section Headers:** Bold uppercase text in `NeoPalette.PrimaryBlue` (`MaterialTheme.colorScheme.primary`).
- **Interactive Rows:** Tactile cards with smooth chevron indicators.
- **Danger Zone:** Inset red-bordered container clearly signaling permanent destructive reset.
- **About Section:**
  - Header: `"ABOUT"` in bold gold (`0xFFFBBF24`), letter-spacing `2.sp`, center-aligned.
  - Attribution: `"Developed by Yasir A Khan"` in `NeoPalette.PrimaryBlue` (`MaterialTheme.colorScheme.primary`), center-aligned.

### E. Calculator Disguise Screen (`CalculatorDisguiseActivity.kt`)
- **Layout Reference:** Coordinated with `CALCULATOR_VAULT_SPEC.md` based on the supplied Android calculator screenshot.
- **Keypad Grid:** 4 columns of circular tactile buttons with specific functional color assignments.

### F. Hide Files Vault Screen (`VaultScreen.kt`)
- **Category Tabs:** Pill buttons (Documents, Pictures, Videos) with file counts.
- **Grid / List Views:** Tactile item cards with encrypted thumbnail placeholders, file metadata, and batch selection actions.

---

## 6. Accessibility, Contrast & Responsive Layout Rules

1. **Touch Target Size:**
   - Every interactive element (buttons, switches, dropdowns, list items, keypad buttons) must have an effective touch target of at least **48dp x 48dp** (Material 3 `minimumInteractiveComponentSize`).

2. **Typography Scaling:**
   - All text sizes must use scale-independent pixels (`sp`).
   - Dynamic Type (font scaling up to 200%) must not truncate or break labels.

3. **Window Insets & Edge-to-Edge:**
   - Must use `enableEdgeToEdge()` on all Activities.
   - Use `Scaffold(contentWindowInsets = ...)` and `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` to guarantee content is never obscured by camera cutouts, status bars, or gesture navigation handles.

4. **Screen Density & Adaptability:**
   - Fluid sizing using `fillMaxWidth()` with `widthIn(max = 600.dp)` on larger screens or foldables to prevent distorted layouts.
