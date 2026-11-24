package com.nethu.calculatorvault.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Small expression evaluator using BigDecimal. Operators: + - * / and postfix %.
 * "200+10%" = 220 (percent of the left value), "50%" = 0.5, "200*10%" = 20.
 */
object CalculatorEngine {
    class CalcException(message: String) : Exception(message)

    private val MC = MathContext(20, RoundingMode.HALF_UP)
    private data class Val(val v: BigDecimal, val pct: Boolean)

    fun evaluate(expr: String): BigDecimal {
        if (expr.isBlank()) throw CalcException("Empty")
        return try {
            Parser(expr).parse()
        } catch (e: NumberFormatException) {
            throw CalcException("Invalid number")
        }
    }

    fun format(v: BigDecimal): String {
        var r = v.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        if (r.scale() < 0) r = r.setScale(0)
        return r.toPlainString()
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): BigDecimal {
            val v = expr()
            if (pos != s.length) throw CalcException("Invalid expression")
            return v
        }

        private fun expr(): BigDecimal {
            var left = term().v
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val t = term()
                val right = if (t.pct) left.multiply(t.v) else t.v
                left = if (op == '+') left.add(right) else left.subtract(right)
            }
            return left
        }

        private fun term(): Val {
            var cur = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val r = factor()
                cur = if (op == '*') {
                    Val(cur.v.multiply(r.v), false)
                } else {
                    if (r.v.signum() == 0) throw CalcException("Cannot divide by zero")
                    Val(cur.v.divide(r.v, MC), false)
                }
            }
            return cur
        }

        private fun factor(): Val {
            var neg = false
            while (pos < s.length && s[pos] == '-') { neg = !neg; pos++ }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw CalcException("Invalid expression")
            var v = BigDecimal(s.substring(start, pos))
            var pct = false
            while (pos < s.length && s[pos] == '%') {
                v = v.divide(BigDecimal(100), MC); pct = true; pos++
            }
            if (neg) v = v.negate()
            return Val(v, pct)
        }
    }
}
