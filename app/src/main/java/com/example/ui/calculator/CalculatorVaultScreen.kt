package com.example.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.data.security.AppLockPreferences
import com.example.ui.common.AuthChallengeDialog
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.util.CalculatorEngine
import kotlinx.coroutines.delay

// Premium Black & Gold Calculator Palette matching Panel 8 of the reference image
private val CalcBgTop = Color(0xFF0B0B0E)
private val CalcBgBottom = Color(0xFF101015)
private val CalcNumberSurface = Color(0xFF1A1A22)
private val CalcNumberGlyph = Color(0xFFF8FAFC)
private val CalcGoldGlyph = Color(0xFF0B0B0E)
private val CalcExpressionColor = Color(0xFF9CA3AF)
private val CalcResultColor = Color(0xFFF8FAFC)
private val CalcErrorColor = Color(0xFFEF4444)

private const val SECRET_BUFFER_TIMEOUT_MS = 5_000L

/**
 * Fullscreen tactile Black & Gold Neomorphic Calculator & Disguise Gateway matching Panel 8.
 *
 * Implements:
 * - Real PEMDAS mathematical calculation via [CalculatorEngine]
 * - 4x5 circular tactile keypad with Gold operator buttons and Dark number buttons
 * - 5-second timeout covert sequence buffer
 * - Two-Key Rule: Entering the secret sequence followed by '=' suppresses display output
 *   and triggers mandatory Master Authentication ([AuthChallengeDialog]) before opening the Vault.
 */
@Composable
fun CalculatorVaultScreen(
    preferences: AppLockPreferences,
    onGatewayUnlocked: () -> Unit,
    isStandaloneDisguiseMode: Boolean = true,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToApps: (() -> Unit)? = null,
    onNavigateToFiles: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var expression by remember { mutableStateOf("") }
    var resultPreview by remember { mutableStateOf("") }
    var justEvaluated by remember { mutableStateOf(false) }

    // Covert sequence buffer (auto-expires after 5 seconds of inactivity)
    var secretDigitBuffer by remember { mutableStateOf("") }
    var lastInputTimestamp by remember { mutableLongStateOf(0L) }

    // Mandatory Two-Key Master Authentication modal state
    var showMasterAuthChallenge by remember { mutableStateOf(false) }

    LaunchedEffect(lastInputTimestamp) {
        if (lastInputTimestamp > 0L && secretDigitBuffer.isNotEmpty()) {
            delay(SECRET_BUFFER_TIMEOUT_MS)
            secretDigitBuffer = ""
        }
    }

    fun updatePreview(expr: String) {
        val hasOperatorOrPercent = expr.drop(if (expr.startsWith("−")) 1 else 0)
            .any { CalculatorEngine.isOperator(it) || it == '%' }
        if (expr.isNotEmpty() && hasOperatorOrPercent) {
            val evaluated = CalculatorEngine.evaluate(expr)
            resultPreview = if (!CalculatorEngine.isErrorResult(evaluated) && evaluated != expr) {
                evaluated
            } else {
                ""
            }
        } else {
            resultPreview = ""
        }
    }

    fun onDigitInput(digit: String) {
        lastInputTimestamp = System.currentTimeMillis()
        secretDigitBuffer = (secretDigitBuffer + digit).takeLast(8)

        val next = CalculatorEngine.appendDigit(expression, digit, justEvaluated)
        justEvaluated = false
        expression = next
        updatePreview(next)
    }

    fun onClearAll() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        expression = ""
        resultPreview = ""
        justEvaluated = false
        secretDigitBuffer = ""
    }

    fun onBackspace() {
        lastInputTimestamp = System.currentTimeMillis()
        if (secretDigitBuffer.isNotEmpty()) {
            secretDigitBuffer = secretDigitBuffer.dropLast(1)
        }
        if (justEvaluated) {
            expression = ""
            resultPreview = ""
            justEvaluated = false
            return
        }
        val next = CalculatorEngine.backspace(expression)
        expression = next
        updatePreview(next)
    }

    fun onEqualsPressed() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Check covert secret sequence if configured
        if (preferences.isCalculatorSecretConfigured()) {
            val exprCandidate = expression.trim()
            val bufferCandidate = secretDigitBuffer.trim()

            val matched = preferences.verifyCalculatorSecretSequence(exprCandidate) ||
                    preferences.verifyCalculatorSecretSequence(bufferCandidate)

            if (matched) {
                // Suppress sequence from display immediately & clear buffer
                expression = ""
                resultPreview = ""
                secretDigitBuffer = ""
                justEvaluated = false
                // Trigger Mandatory Master Authentication Challenge (Two-Key Rule)
                showMasterAuthChallenge = true
                return
            }
        }

        // Standard math evaluation
        secretDigitBuffer = ""
        if (expression.isEmpty()) return
        val evaluated = CalculatorEngine.evaluate(expression)
        if (evaluated.isNotEmpty()) {
            resultPreview = evaluated
            justEvaluated = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CalcBgTop, CalcBgBottom)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("calculator_vault_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar matching Panel 8 ("Calculator Vault")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isStandaloneDisguiseMode && onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("calc_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = NeoPalette.TextPrimary
                            )
                        }
                    }
                    Text(
                        text = if (isStandaloneDisguiseMode) "Calculator" else "Calculator Vault",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeoPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = if (isStandaloneDisguiseMode) 4.dp else 0.dp)
                    )
                }

                // Recessed Dark Neomorphic Display Region matching Panel 8
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clip(NeoShapes.LargeCard)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF050507), Color(0xFF0E0E13))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x99000000), Color(0x24F5B800))
                            ),
                            shape = NeoShapes.LargeCard
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val mainDisplayText = when {
                            justEvaluated && resultPreview.isNotEmpty() -> resultPreview
                            expression.isNotEmpty() -> expression
                            else -> "0"
                        }

                        val secondaryDisplayText = when {
                            justEvaluated -> expression
                            resultPreview.isNotEmpty() -> resultPreview
                            else -> ""
                        }

                        if (secondaryDisplayText.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = secondaryDisplayText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = CalcExpressionColor,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("calc_secondary_display")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        val isError = CalculatorEngine.isErrorResult(mainDisplayText)
                        val dynamicFontSize = when {
                            isError -> 24.sp
                            mainDisplayText.length > 14 -> 26.sp
                            mainDisplayText.length > 10 -> 34.sp
                            else -> 42.sp
                        }

                        Text(
                            text = mainDisplayText,
                            fontSize = dynamicFontSize,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) CalcErrorColor else CalcResultColor,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = dynamicFontSize * 1.15f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calc_main_display")
                        )
                    }
                }

                // 4-Column x 5-Row Responsive Tactile Circular Keypad Grid matching Panel 8
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    val spacing = 12.dp
                    val calculatedSize = (maxWidth - spacing * 3) / 4
                    val buttonSize: Dp = min(70.dp, maxOf(56.dp, calculatedSize))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        // Row 1: AC (Gold), ( ), %, ÷ (Gold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CalcKeyButton(
                                label = "AC",
                                size = buttonSize,
                                isGoldOperator = true,
                                onClick = { onClearAll() },
                                testTag = "calc_key_ac"
                            )
                            CalcKeyButton(
                                label = "( )",
                                size = buttonSize,
                                isGoldOperator = false,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendParenthesis(expression, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_parens"
                            )
                            CalcKeyButton(
                                label = "%",
                                size = buttonSize,
                                isGoldOperator = false,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendPercentage(expression, resultPreview, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_percent"
                            )
                            CalcKeyButton(
                                label = "÷",
                                size = buttonSize,
                                isGoldOperator = true,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendOperator(expression, '÷', resultPreview, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_div"
                            )
                        }

                        // Row 2: 7, 8, 9, × (Gold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("7", "8", "9").forEach { digit ->
                                CalcKeyButton(
                                    label = digit,
                                    size = buttonSize,
                                    isGoldOperator = false,
                                    onClick = { onDigitInput(digit) },
                                    testTag = "calc_key_$digit"
                                )
                            }
                            CalcKeyButton(
                                label = "×",
                                size = buttonSize,
                                isGoldOperator = true,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendOperator(expression, '×', resultPreview, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_mul"
                            )
                        }

                        // Row 3: 4, 5, 6, − (Gold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("4", "5", "6").forEach { digit ->
                                CalcKeyButton(
                                    label = digit,
                                    size = buttonSize,
                                    isGoldOperator = false,
                                    onClick = { onDigitInput(digit) },
                                    testTag = "calc_key_$digit"
                                )
                            }
                            CalcKeyButton(
                                label = "−",
                                size = buttonSize,
                                isGoldOperator = true,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendOperator(expression, '−', resultPreview, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_sub"
                            )
                        }

                        // Row 4: 1, 2, 3, + (Gold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("1", "2", "3").forEach { digit ->
                                CalcKeyButton(
                                    label = digit,
                                    size = buttonSize,
                                    isGoldOperator = false,
                                    onClick = { onDigitInput(digit) },
                                    testTag = "calc_key_$digit"
                                )
                            }
                            CalcKeyButton(
                                label = "+",
                                size = buttonSize,
                                isGoldOperator = true,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendOperator(expression, '+', resultPreview, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_add"
                            )
                        }

                        // Row 5: 0, ., ⌫, = (Gold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CalcKeyButton(
                                label = "0",
                                size = buttonSize,
                                isGoldOperator = false,
                                onClick = { onDigitInput("0") },
                                testTag = "calc_key_0"
                            )
                            CalcKeyButton(
                                label = ".",
                                size = buttonSize,
                                isGoldOperator = false,
                                onClick = {
                                    secretDigitBuffer = ""
                                    val next = CalculatorEngine.appendDecimal(expression, justEvaluated)
                                    justEvaluated = false
                                    expression = next
                                    updatePreview(next)
                                },
                                testTag = "calc_key_dot"
                            )
                            NeoCircularButton(
                                onClick = { onBackspace() },
                                size = buttonSize,
                                backgroundColor = CalcNumberSurface,
                                contentColor = CalcNumberGlyph,
                                modifier = Modifier.testTag("calc_key_backspace")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeoPalette.PrimaryGold)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = CalcGoldGlyph,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            CalcKeyButton(
                                label = "=",
                                size = buttonSize,
                                isGoldOperator = true,
                                onClick = { onEqualsPressed() },
                                testTag = "calc_key_equals"
                            )
                        }
                    }
                }

                // Footer hint matching Panel 8
                Text(
                    text = "Enter your real password to access vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp)
                )
            }

            // Bottom Navigation Bar when opened from inside the app
            if (!isStandaloneDisguiseMode && onNavigateBack != null) {
                NeoBottomNavigationBar(
                    currentTab = MainBottomTab.HOME,
                    onSelectTab = { tab ->
                        when (tab) {
                            MainBottomTab.HOME -> onNavigateBack()
                            MainBottomTab.APPS -> (onNavigateToApps ?: onNavigateBack)()
                            MainBottomTab.FILES -> (onNavigateToFiles ?: onGatewayUnlocked)()
                            MainBottomTab.SETTINGS -> (onNavigateToSettings ?: onNavigateBack)()
                        }
                    }
                )
            }
        }

        // Mandatory Two-Key Master Authentication Challenge
        if (showMasterAuthChallenge) {
            AuthChallengeDialog(
                title = "Unlock Private Vault",
                subtitle = "Use fingerprint or enter your master PIN to access your encrypted vault",
                preferences = preferences,
                onAuthorized = {
                    showMasterAuthChallenge = false
                    onGatewayUnlocked()
                },
                onDismiss = {
                    showMasterAuthChallenge = false
                }
            )
        }
    }
}

@Composable
private fun CalcKeyButton(
    label: String,
    size: Dp,
    isGoldOperator: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val contentColor = if (isGoldOperator) CalcGoldGlyph else CalcNumberGlyph
    NeoCircularButton(
        onClick = onClick,
        size = size,
        backgroundColor = if (isGoldOperator) NeoPalette.PrimaryGold else CalcNumberSurface,
        contentColor = contentColor,
        isGoldSolid = isGoldOperator,
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 1) 20.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
