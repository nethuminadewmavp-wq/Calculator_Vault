package com.nethu.calculatorvault

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nethu.calculatorvault.calc.CalculatorEngine
import com.nethu.calculatorvault.databinding.ActivityMainBinding
import com.nethu.calculatorvault.security.PinManager
import com.nethu.calculatorvault.security.VaultSession
import com.nethu.calculatorvault.ui.PinActivity
import com.nethu.calculatorvault.ui.PrivacyActivity
import com.nethu.calculatorvault.ui.ThemeUtil
import com.nethu.calculatorvault.ui.VaultHomeActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()

    private var expr = ""          // internal form: digits . + - * / %
    private var lastExpr = ""
    private var showingResult = false
    private var errorText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        savedInstanceState?.let {
            expr = it.getString("expr", "")
            lastExpr = it.getString("last", "")
            showingResult = it.getBoolean("res", false)
        }
        wire(b.keypad)
        b.btnMenu.setOnClickListener { showMenu(it) }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expr", expr)
        outState.putString("last", lastExpr)
        outState.putBoolean("res", showingResult)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun wire(v: View) {
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) wire(v.getChildAt(i))
        } else {
            val t = v.tag
            if (v is Button && t is String) v.setOnClickListener { onKey(t) }
        }
    }

    private fun onKey(k: String) {
        errorText = null
        if (showingResult) {
            showingResult = false
            if ((k.length == 1 && k[0].isDigit()) || k == ".") expr = ""
        }
        when (k) {
            "C" -> { expr = ""; lastExpr = "" }
            "DEL" -> if (expr.isNotEmpty()) expr = expr.dropLast(1)
            "=" -> equalsPressed()
            "+", "-", "*", "/" -> addOperator(k[0])
            "%" -> if (expr.isNotEmpty() && expr.last().isDigit()) append("%")
            "." -> addDot()
            else -> addDigit(k)
        }
        render()
    }

    private fun append(s: String) { if (expr.length < 60) expr += s }

    private fun currentNumber() = expr.takeLastWhile { it.isDigit() || it == '.' }

    private fun addDigit(d: String) {
        if (expr.endsWith("%")) return
        if (currentNumber() == "0") expr = expr.dropLast(1)
        append(d)
    }

    private fun addDot() {
        if (expr.endsWith("%")) return
        val cur = currentNumber()
        if (cur.contains('.')) return
        if (cur.isEmpty()) append("0")
        append(".")
    }

    private fun isOp(c: Char) = c == '+' || c == '-' || c == '*' || c == '/'

    private fun addOperator(op: Char) {
        when {
            expr.isEmpty() -> if (op == '-') expr = "-"
            expr == "-" -> {}
            op == '-' && (expr.last() == '*' || expr.last() == '/') -> append("-")
            else -> {
                while (expr.isNotEmpty() && isOp(expr.last())) expr = expr.dropLast(1)
                if (expr.isNotEmpty()) append(op.toString())
            }
        }
    }

    private fun trimTrailing(s: String) = s.trimEnd('+', '-', '*', '/', '.')

    private fun equalsPressed() {
        if (expr.isEmpty()) return
        val candidate = expr
        // Documented vault entry: type the PIN (4-8 digits) and press "=".
        if (candidate.length in 4..8 && candidate.all { it.isDigit() }) checkPin(candidate)
        try {
            val clean = trimTrailing(expr)
            val r = CalculatorEngine.format(CalculatorEngine.evaluate(clean))
            lastExpr = clean
            expr = r
            showingResult = true
        } catch (e: CalculatorEngine.CalcException) {
            errorText = e.message
        }
    }

    private fun checkPin(candidate: String) {
        executor.execute {
            val r = try { PinManager.verify(applicationContext, candidate) } catch (e: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (r?.result) {  // r is non-null in the branches below
                    PinManager.Result.OK -> {
                        expr = ""; lastExpr = ""; showingResult = false; errorText = null
                        render()
                        VaultSession.unlock()
                        startActivity(Intent(this, VaultHomeActivity::class.java))
                    }
                    PinManager.Result.LOCKED -> {
                        val wait = r?.waitSeconds ?: 0L
                        Toast.makeText(this, "Vault is temporarily blocked. Try again in $wait s.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun display(s: String) = s.replace('*', '×').replace('/', '÷').replace('-', '−')

    private fun preview(): String {
        val body = expr.drop(1)
        if (expr.none { it == '+' || it == '*' || it == '/' || it == '%' } && !body.contains('-')) return ""
        return try {
            CalculatorEngine.format(CalculatorEngine.evaluate(trimTrailing(expr))).let { display(it) }
        } catch (e: Exception) { "" }
    }

    private fun render() {
        val err = errorText
        when {
            err != null -> { b.tvExpression.text = display(expr); b.tvResult.text = err }
            showingResult -> { b.tvExpression.text = display(expr); b.tvResult.text = display(lastExpr) + " =" }
            else -> { b.tvExpression.text = display(expr); b.tvResult.text = preview() }
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.menu_how)
            if (!PinManager.isPinSet(this@MainActivity)) menu.add(0, 2, 1, R.string.menu_setup_pin)
            menu.add(0, 3, 2, R.string.menu_privacy)
            menu.add(0, 4, 3, R.string.menu_theme)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.how_title).setMessage(R.string.how_body)
                        .setPositiveButton("OK", null).show()
                    2 -> startActivity(
                        Intent(this@MainActivity, PinActivity::class.java)
                            .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP)
                    )
                    3 -> startActivity(Intent(this@MainActivity, PrivacyActivity::class.java))
                    4 -> {
                        val next = (ThemeUtil.mode(this@MainActivity) + 1) % 3
                        Toast.makeText(this@MainActivity, "Theme: " + ThemeUtil.label(next), Toast.LENGTH_SHORT).show()
                        ThemeUtil.set(this@MainActivity, next)
                    }
                }
                true
            }
            show()
        }
    }
}
