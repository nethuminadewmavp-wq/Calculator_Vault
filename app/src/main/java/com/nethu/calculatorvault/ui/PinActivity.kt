package com.nethu.calculatorvault.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.nethu.calculatorvault.R
import com.nethu.calculatorvault.databinding.ActivityPinBinding
import com.nethu.calculatorvault.security.PinManager
import java.util.concurrent.Executors

class PinActivity : BaseSecureActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SETUP = "setup"
        const val MODE_CHANGE = "change"
    }

    override val requiresSession: Boolean get() = intent.getStringExtra(EXTRA_MODE) == MODE_CHANGE

    private lateinit var b: ActivityPinBinding
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        val change = intent.getStringExtra(EXTRA_MODE) == MODE_CHANGE
        if (!change && PinManager.isPinSet(this)) { finish(); return }  // setup only when no PIN exists

        b = ActivityPinBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(if (change) R.string.pin_title_change else R.string.pin_title_setup)
        b.layoutCurrent.visibility = if (change) View.VISIBLE else View.GONE
        b.btnSave.setOnClickListener { save(change) }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun save(change: Boolean) {
        val current = b.etCurrent.text?.toString().orEmpty()
        val pin = b.etNew.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()
        b.layoutCurrent.error = null; b.layoutNew.error = null; b.layoutConfirm.error = null

        if (!Regex("\\d{4,8}").matches(pin)) { b.layoutNew.error = "Use 4 to 8 digits"; return }
        if (pin != confirm) { b.layoutConfirm.error = "PINs do not match"; return }

        b.progress.visibility = View.VISIBLE
        b.btnSave.isEnabled = false
        executor.execute {
            var error: String? = null
            var currentWrong = false
            try {
                if (change) {
                    val r = PinManager.verify(applicationContext, current)
                    if (r.result == PinManager.Result.LOCKED) error = "Too many tries. Wait ${r.waitSeconds} s."
                    else if (r.result != PinManager.Result.OK) { error = "Current PIN is incorrect"; currentWrong = true }
                }
                if (error == null) PinManager.setPin(applicationContext, pin)
            } catch (e: Exception) {
                error = "Could not store the PIN securely: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                b.progress.visibility = View.GONE
                b.btnSave.isEnabled = true
                if (error != null) {
                    if (currentWrong) b.layoutCurrent.error = error
                    else Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this, "PIN saved. Type it on the calculator and press = to open the vault.", Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
}
