package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Deterministic PEMDAS arithmetic engine and input state machine for the Calculator Disguise.
 *
 * Implements:
 * - Standard operator precedence (Parentheses, Multiplication/Division, Addition/Subtraction)
 * - Division by zero safety ("Cannot divide by zero")
 * - Single decimal point enforcement per operand
 * - Consecutive operator replacement (e.g., "5 + ×" -> "5 ×")
 * - Intelligent dynamic parentheses "( )" insertion
 * - Percentage "%" operator evaluation
 * - Post-equals continuation (operator continues with result; digit starts fresh)
 */
object CalculatorEngine {

    const val ERROR_DIVIDE_BY_ZERO = "Cannot divide by zero"
    const val ERROR_FORMAT = "Format error"

    private val OPERATORS = setOf('+', '−', '-', '×', '*', '÷', '/')

    fun isOperator(ch: Char): Boolean = ch in OPERATORS

    /**
     * Appends a digit ('0'..'9') to the current expression, respecting post-equals state.
     */
    fun appendDigit(currentExpr: String, digit: String, justEvaluated: Boolean): String {
        if (justEvaluated) {
            return digit
        }
        if (currentExpr == "0") {
            return digit
        }
        // If previous char is ')' or '%', implicitly insert '×'
        if (currentExpr.isNotEmpty() && (currentExpr.last() == ')' || currentExpr.last() == '%')) {
            return "$currentExpr×$digit"
        }
        return currentExpr + digit
    }

    /**
     * Appends a decimal point ('.') if the current active number token does not already contain one.
     */
    fun appendDecimal(currentExpr: String, justEvaluated: Boolean): String {
        if (justEvaluated || currentExpr.isEmpty()) {
            return "0."
        }
        val lastChar = currentExpr.last()
        if (isOperator(lastChar) || lastChar == '(') {
            return "${currentExpr}0."
        }
        if (lastChar == ')' || lastChar == '%') {
            return "${currentExpr}×0."
        }
        // Check current trailing number token for an existing '.'
        val currentOperand = extractTrailingOperand(currentExpr)
        if (currentOperand.contains('.')) {
            return currentExpr // Ignore duplicate decimal point in same operand
        }
        return "$currentExpr."
    }

    /**
     * Appends or replaces an arithmetic operator ('+', '−', '×', '÷').
     */
    fun appendOperator(
        currentExpr: String,
        operator: Char,
        lastResult: String,
        justEvaluated: Boolean
    ): String {
        val normalizedOp = when (operator) {
            '-' -> '−'
            '*' -> '×'
            '/' -> '÷'
            else -> operator
        }

        if (justEvaluated && lastResult.isNotEmpty() && !isErrorResult(lastResult)) {
            return "$lastResult$normalizedOp"
        }

        if (currentExpr.isEmpty()) {
            return if (normalizedOp == '−') "−" else ""
        }

        val lastChar = currentExpr.last()
        if (isOperator(lastChar)) {
            // If expression is just unary minus "−" and user presses another operator, ignore unless '+'
            if (currentExpr == "−") {
                return if (normalizedOp == '+') "" else currentExpr
            }
            // Replace trailing operator
            return currentExpr.dropLast(1) + normalizedOp
        }

        if (lastChar == '(') {
            return if (normalizedOp == '−') "${currentExpr}−" else currentExpr
        }

        if (lastChar == '.') {
            return "${currentExpr}0$normalizedOp"
        }

        return "$currentExpr$normalizedOp"
    }

    /**
     * Dynamically inserts '(' or ')' depending on open parenthesis balance and trailing token.
     */
    fun appendParenthesis(currentExpr: String, justEvaluated: Boolean): String {
        if (justEvaluated || currentExpr.isEmpty()) {
            return "("
        }
        val openCount = currentExpr.count { it == '(' }
        val closeCount = currentExpr.count { it == ')' }
        val lastChar = currentExpr.last()

        return if (openCount > closeCount && (lastChar.isDigit() || lastChar == ')' || lastChar == '%')) {
            "$currentExpr)"
        } else if (lastChar.isDigit() || lastChar == ')' || lastChar == '%') {
            "$currentExpr×("
        } else {
            "$currentExpr("
        }
    }

    /**
     * Appends percentage ('%') if preceded by a valid number or closing parenthesis.
     */
    fun appendPercentage(currentExpr: String, lastResult: String, justEvaluated: Boolean): String {
        if (justEvaluated && lastResult.isNotEmpty() && !isErrorResult(lastResult)) {
            return "$lastResult%"
        }
        if (currentExpr.isEmpty()) return ""
        val lastChar = currentExpr.last()
        if (lastChar.isDigit() || lastChar == ')') {
            return "$currentExpr%"
        }
        return currentExpr
    }

    /**
     * Deletes the last character from the expression.
     */
    fun backspace(currentExpr: String): String {
        return if (currentExpr.isNotEmpty()) currentExpr.dropLast(1) else ""
    }

    fun isErrorResult(result: String): Boolean {
        return result == ERROR_DIVIDE_BY_ZERO || result == ERROR_FORMAT
    }

    private fun extractTrailingOperand(expr: String): String {
        var i = expr.length - 1
        while (i >= 0 && (expr[i].isDigit() || expr[i] == '.')) {
            i--
        }
        return expr.substring(i + 1)
    }

    /**
     * Evaluates a mathematical expression string using PEMDAS precedence.
     * Returns formatted result string, or [ERROR_DIVIDE_BY_ZERO] / [ERROR_FORMAT].
     */
    fun evaluate(expression: String): String {
        val cleaned = expression.trim()
        if (cleaned.isEmpty()) return ""

        // Strip trailing incomplete operators or open parens for live/final evaluation
        var expr = cleaned
        while (expr.isNotEmpty() && (isOperator(expr.last()) || expr.last() == '(' || expr.last() == '.')) {
            if (expr.last() == '.') {
                expr += "0"
                break
            }
            expr = expr.dropLast(1)
        }
        if (expr.isEmpty()) return ""

        // Auto-close any unclosed parentheses
        val openCount = expr.count { it == '(' }
        val closeCount = expr.count { it == ')' }
        if (openCount > closeCount) {
            expr += ")".repeat(openCount - closeCount)
        }

        return try {
            val value = parseExpression(expr)
            if (value.isInfinite() || value.isNaN()) {
                ERROR_DIVIDE_BY_ZERO
            } else {
                formatNumber(value)
            }
        } catch (e: ArithmeticException) {
            if (e.message == ERROR_DIVIDE_BY_ZERO) ERROR_DIVIDE_BY_ZERO else ERROR_FORMAT
        } catch (_: Exception) {
            ERROR_FORMAT
        }
    }

    private fun formatNumber(value: Double): String {
        if (value == 0.0) return "0"
        val bd = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = bd.toPlainString()
        // Avoid overly long strings on screen; use scientific notation only for huge/tiny numbers
        return if (plain.length > 15 && (kotlin.math.abs(value) >= 1e12 || kotlin.math.abs(value) < 1e-6)) {
            String.format(Locale.US, "%.6G", value)
        } else {
            plain
        }
    }

    // Recursive descent PEMDAS parser
    private fun parseExpression(expr: String): Double {
        val normalized = expr
            .replace('−', '-')
            .replace('×', '*')
            .replace('÷', '/')
            .replace(" ", "")

        val parser = object {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < normalized.length) normalized[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseAddSub()
                if (pos < normalized.length) throw IllegalArgumentException("Unexpected char")
                return x
            }

            fun parseAddSub(): Double {
                var x = parseMulDiv()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseMulDiv()
                        eat('-'.code) -> x -= parseMulDiv()
                        else -> return x
                    }
                }
            }

            fun parseMulDiv(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> {
                            val divisor = parseFactor()
                            if (divisor == 0.0) {
                                throw ArithmeticException(ERROR_DIVIDE_BY_ZERO)
                            }
                            x /= divisor
                        }
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseAddSub()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    val numStr = normalized.substring(startPos, pos)
                    x = numStr.toDouble()
                } else {
                    throw IllegalArgumentException("Unexpected token")
                }

                // Handle trailing percentage '%' operators (can be chained)
                while (eat('%'.code)) {
                    x /= 100.0
                }

                return x
            }
        }

        return parser.parse()
    }
}
