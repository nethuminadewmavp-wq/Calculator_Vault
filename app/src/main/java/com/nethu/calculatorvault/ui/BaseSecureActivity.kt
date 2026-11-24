package com.nethu.calculatorvault.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.nethu.calculatorvault.security.VaultSession

/**
 * Base for every vault screen: blocks screenshots / recents preview, refuses to show anything
 * while the vault is locked (finishing back to the calculator), and reports start/stop to VaultSession.
 * Subclasses must do `if (isFinishing) return` right after super.onCreate().
 */
abstract class BaseSecureActivity : AppCompatActivity() {
    protected open val requiresSession: Boolean get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (requiresSession && !VaultSession.isUnlocked()) finish()
    }

    override fun onStart() {
        super.onStart()
        val ok = !requiresSession || VaultSession.isUnlocked()
        VaultSession.activityStarted()
        if (!ok) finish()
    }

    override fun onStop() {
        super.onStop()
        VaultSession.activityStopped(isChangingConfigurations)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
