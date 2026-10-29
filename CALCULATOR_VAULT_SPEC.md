# CALCULATOR_VAULT_SPEC.md — Calculator Redesign & Disguise Gateway Specification

**Document Version:** 1.0.0  
**Status:** Phase 0 (Specification & Interface Design)  
**Authority:** Governs visual redesign, arithmetic engine, secret sequence detection, disguise gateway routing, and Settings configuration  
**Visual Reference:** Supplied Android modern dark calculator screenshot  

---

## 1. Visual Redesign & Screenshot Alignment

The calculator interface is a complete, faithful redesign inspired by modern Android calculator aesthetics, elevated with Smart AppLock's **tactile dark Neomorphism**.

### Visual Appearance Specification
- **Canvas / Background:** Fullscreen edge-to-edge canvas in ultra-dark graphite with a subtle teal/navy undertone (`#090E17` to `#0B1218`).
- **Display Area:** Upper screen region featuring:
  - Top expression line (up to 24sp, muted slate `#94A3B8`, right-aligned, scrollable).
  - Main calculation/result line (up to 44sp, luminous high-contrast white `#F8FAFC`, right-aligned, dynamic text size reduction on overflow).
  - Clear separation from keypad without distracting dividers.
- **Keypad Grid:** Exact 4-column x 5-row responsive layout of circular tactile buttons.
- **Color Assignments (Direct Screenshot Alignment):**
  - **Number Buttons (`0`–`9`, `.`):** Muted dark-slate circular surfaces (`#1E293B` / `#1A2230`) with soft convex Neomorphic highlights and clean white glyphs (`#F1F5F9`).
  - **Standard Operators (`÷`, `×`, `−`, `+`):** Sophisticated blue-green / cyan-tinted surfaces (`#0E3A4B` / `#0F4C5C`) with bright cyan glyphs (`#38BDF8`).
  - **Clear Button (`AC`):** Highlighted bright teal/cyan accent (`#06B6D4` / `#14B8A6`) with crisp dark contrast glyph (`#090E17`).
  - **Parentheses `( )` & Percentage `%`:** Muted operator blue-slate (`#1E293B`).
  - **Backspace / Delete `⌫`:** Dark tactile surface with high-visibility icon.
  - **Equals Button (`=`):** Distinct luminous electric blue/cyan circle (`#2563EB` to `#0284C7`) with bold white glyph (`#FFFFFF`), providing a natural tactile focal point.

---

## 2. Keypad Layout Grid Matrix

```
┌───────────────┬───────────────┬───────────────┬───────────────┐
│      AC       │     (  )      │       %       │       ÷       │
│  (Teal Accent)│ (Muted Slate) │ (Muted Slate) │ (Blue-Green)  │
├───────────────┼───────────────┼───────────────┼───────────────┤
│       7       │       8       │       9       │       ×       │
│  (Dark Slate) │  (Dark Slate) │  (Dark Slate) │ (Blue-Green)  │
├───────────────┼───────────────┼───────────────┼───────────────┤
│       4       │       5       │       6       │       −       │
│  (Dark Slate) │  (Dark Slate) │  (Dark Slate) │ (Blue-Green)  │
├───────────────┼───────────────┼───────────────┼───────────────┤
│       1       │       2       │       3       │       +       │
│  (Dark Slate) │  (Dark Slate) │  (Dark Slate) │ (Blue-Green)  │
├───────────────┼───────────────┼───────────────┼───────────────┤
│       0       │       .       │       ⌫       │       =       │
│  (Dark Slate) │  (Dark Slate) │ (Back-Delete) │(Luminous Blue)│
└───────────────┴───────────────┴───────────────┴───────────────┘
```

- **Touch Target Dimensions:** Circular buttons with a fixed 72dp diameter on standard displays (adapts smoothly between 64dp and 76dp via `BoxWithConstraints`). Touch target never falls below the 48dp minimum standard.
- **Haptic Feedback:** Tactile `HapticFeedbackType.TextHandleMove` on button tap; distinct haptic tick on `=` and `AC`.

---

## 3. Real Arithmetic Engine Specification

The calculator is not a hollow UI facade; it performs real, robust mathematical operations:

### Arithmetic Rules & State Machine
1. **Basic Operations:** Addition (`+`), Subtraction (`−`), Multiplication (`×`), Division (`÷`).
2. **Order of Operations:** Evaluates expressions using standard PEMDAS operator precedence (multiplication and division before addition and subtraction).
3. **Division by Zero:** Evaluates cleanly to display `"Cannot divide by zero"` in the result field and disables further operators until cleared or backspaced. Never throws an unhandled crash.
4. **Decimal Rules:** Prevents multiple decimal points within a single operand (e.g. `3.14.5` is blocked; inputting second `.` is ignored).
5. **Repeated Operators:** If an operator is pressed immediately after another operator, the new operator replaces the preceding one (e.g. `5 + ×` becomes `5 ×`).
6. **Parentheses Handling:** The `( )` key dynamically injects `(` if an operand was just opened or operator typed, and `)` if an open parenthesis remains unclosed.
7. **Percentage (`%`):** Divides preceding operand by 100 or calculates proportional percentage depending on context.
8. **Result Continuation:** Tapping an operator (`+`, `−`, `×`, `÷`) after pressing `=` immediately carries forward the computed result as the first operand of the next calculation. Tapping a digit starts a fresh calculation.
9. **Clear (`AC`):** Resets both expression and result fields to initial empty state.
10. **Backspace (`⌫`):** Deletes the most recent token from right to left.

---

## 4. Disguise Entry & Vault Gateway Flow

### Strict Architectural Scope Rule
**Calculator Disguise applies EXCLUSIVELY to launching the Smart AppLock application itself.**  
It **NEVER** intercepts, overlays, or replaces the launch behavior of other protected apps (banking, messaging, social). External protected apps continue to trigger the standard `LockActivity` prompt.

### End-to-End Gateway Flow
```
[User Launches Smart AppLock]
             │
             ▼
[Is Calculator Disguise Enabled?]
             │
      ┌──────┴──────┐
     YES            NO
      │             │
      ▼             ▼
[Display Fullscreen     [Display Standard
 Calculator]             DashboardAuthGate / Main Nav]
      │
      ▼
[User Uses Calculator for Real Math]
      │
      ▼
[User Inputs Configured Secret Sequence] (e.g. 1984= or 7777=)
      │
      ▼
[Secret Sequence Matched?]
      │
      ├─(NO)──> [Evaluate Math Expression Normally]
      │
      ▼ (YES)
[Suppress Sequence from Display & Clear Input]
      │
      ▼
[TRIGGER MANDATORY MASTER AUTHENTICATION CHALLENGE]
(BiometricPrompt or Master PIN via AuthChallengeDialog)
      │
      ├─(Cancelled or Failed)──> [Return to Calculator with Zero Content Exposed]
      │
      ▼ (Successful Authentication)
[OPEN PRIVATE VAULT SCREEN]
```

### Inviolable Security Invariant: The Two-Key Rule
> [!CRITICAL]
> **The secret sequence ALONE must NEVER unlock the private vault.**  
> The secret sequence functions strictly as a covert "gateway trigger" that requests existing master credentials. Even if an unauthorized observer discovers the calculator secret sequence, they cannot enter the vault without passing the user's master biometric scan or 4–6 digit master PIN.

### Secrecy & Anti-Leak Safeguards
1. **Display Stealth:** The secret sequence is never rendered as an expression or output in the calculator display.
2. **No Log Leaks:** Keypad button strokes and sequence buffers are never written to Logcat or analytics.
3. **Buffer Timeout:** In-memory sequence detection buffer automatically clears after 5 seconds of inactivity.
4. **Attempt Rate Limiting:** Entering incorrect sequence triggers does not crash; failed attempts at the subsequent PIN screen follow the standard 5-attempt / 30-second lockout policy.
5. **Backgrounding & Session Termination:** If the vault is minimized or screen turns off, session unlocks are cleared in accordance with `AppLockPreferences.isRelockOnScreenOff()`. Reopening Smart AppLock returns immediately to the disguise calculator.

---

## 5. Security & Settings Configuration

The Settings screen (`SettingsScreen.kt`) includes a dedicated **Calculator Disguise** control card:

### Settings Components
1. **Master Toggle:** Switch enabling/disabling Calculator Disguise.
   - Enabling requires configuring a secret code first.
   - Disabling returns app launch to the standard Dashboard / PIN entry.
   - Gated behind `AuthChallengeDialog` before toggling.
2. **Secret Code Setup / Change Action:**
   - Label: `"Set Secret Sequence"` (if unconfigured) or `"Change Secret Sequence"` (if active).
   - Dialog: Prompts user to enter 4–8 digits followed by the trigger key (default: `=`).
   - Confirmation: User must re-enter sequence to verify.
   - Status Badge: Displays `"Configured & Active"` (green) or `"Disabled"` (muted).
3. **Storage Security:**
   - Secret sequence is stored as an iterative salted hash via `PinHashUtil` (or encrypted using Android Keystore), never in plaintext SharedPreferences.
