package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.AppLockPreferences
import com.example.util.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalculatorVaultTest {

    private lateinit var context: Context
    private lateinit var preferences: AppLockPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = AppLockPreferences(context)
        preferences.clearAllData()
    }

    @Test
    fun calculatorEngine_pemdasOrderOfOperations_TC_CALC_01() {
        assertEquals("14", CalculatorEngine.evaluate("2+3×4"))
        assertEquals("20", CalculatorEngine.evaluate("(2+3)×4"))
        assertEquals("80", CalculatorEngine.evaluate("12+34×2"))
        assertEquals("25", CalculatorEngine.evaluate("100÷4"))
        assertEquals("100", CalculatorEngine.evaluate("50%×200"))
    }

    @Test
    fun calculatorEngine_divisionByZero_TC_CALC_02() {
        assertEquals(CalculatorEngine.ERROR_DIVIDE_BY_ZERO, CalculatorEngine.evaluate("10÷0"))
        assertEquals(CalculatorEngine.ERROR_DIVIDE_BY_ZERO, CalculatorEngine.evaluate("42÷0"))
    }

    @Test
    fun calculatorEngine_percentageAndDecimals_evaluateAccurately() {
        assertEquals("20", CalculatorEngine.evaluate("200×10%"))

        // Single decimal point validation per operand ("3.14.5" -> "3.145")
        var expr = ""
        expr = CalculatorEngine.appendDigit(expr, "3", false)
        expr = CalculatorEngine.appendDecimal(expr, false)
        expr = CalculatorEngine.appendDigit(expr, "1", false)
        expr = CalculatorEngine.appendDigit(expr, "4", false)
        expr = CalculatorEngine.appendDecimal(expr, false) // second dot in same operand must be ignored
        expr = CalculatorEngine.appendDigit(expr, "5", false)
        assertEquals("3.145", expr)
    }

    @Test
    fun calculatorEngine_consecutiveOperators_replacesPreviousOperator_TC_CALC_02() {
        var expr = "5+"
        expr = CalculatorEngine.appendOperator(expr, '×', "", false)
        expr = CalculatorEngine.appendDigit(expr, "3", false)
        assertEquals("5×3", expr)
        assertEquals("15", CalculatorEngine.evaluate(expr))
    }

    @Test
    fun calculatorEngine_dynamicParenthesesAndBackspace() {
        var expr = ""
        expr = CalculatorEngine.appendParenthesis(expr, false)
        expr = CalculatorEngine.appendDigit(expr, "8", false)
        expr = CalculatorEngine.appendOperator(expr, '+', "", false)
        expr = CalculatorEngine.appendDigit(expr, "2", false)
        expr = CalculatorEngine.appendParenthesis(expr, false)
        assertEquals("(8+2)", expr)
        assertEquals("10", CalculatorEngine.evaluate(expr))

        expr = CalculatorEngine.backspace(expr)
        assertEquals("(8+2", expr)
        // Auto-closes unclosed parenthesis during evaluation
        assertEquals("10", CalculatorEngine.evaluate(expr))
    }

    @Test
    fun calculatorEngine_postEqualsContinuation_behavesCorrectly() {
        // Pressing operator after '=' continues from result
        val continuedWithOp = CalculatorEngine.appendOperator("2+3×4", '+', "14", justEvaluated = true)
        assertEquals("14+", continuedWithOp)

        // Pressing digit after '=' starts a fresh expression
        val freshWithDigit = CalculatorEngine.appendDigit("14", "9", justEvaluated = true)
        assertEquals("9", freshWithDigit)
    }

    @Test
    fun preferences_calculatorSecretSequenceAndDisguiseToggle_enforcesConfiguredSecret() {
        assertFalse(preferences.isCalculatorSecretConfigured())
        assertFalse(preferences.isCalculatorDisguiseEnabled())

        // Enabling disguise without a secret sequence configured must still return false
        preferences.setCalculatorDisguiseEnabled(true)
        assertFalse(preferences.isCalculatorDisguiseEnabled())

        // Save 6-digit secret sequence
        preferences.saveCalculatorSecretSequence("739182")
        assertTrue(preferences.isCalculatorSecretConfigured())
        assertEquals(6, preferences.getCalculatorSecretLength())
        assertTrue(preferences.isCalculatorDisguiseEnabled())

        // Verify correct and wrong sequences
        assertTrue(preferences.verifyCalculatorSecretSequence("739182"))
        assertFalse(preferences.verifyCalculatorSecretSequence("739183"))
        assertFalse(preferences.verifyCalculatorSecretSequence("1234"))

        // Disabling disguise preserves secret sequence but turns off gateway mode
        preferences.setCalculatorDisguiseEnabled(false)
        assertFalse(preferences.isCalculatorDisguiseEnabled())
        assertTrue(preferences.isCalculatorSecretConfigured())
    }
}
