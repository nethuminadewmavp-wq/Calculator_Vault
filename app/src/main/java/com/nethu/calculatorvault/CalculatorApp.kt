package com.nethu.calculatorvault

import android.app.Application
import com.nethu.calculatorvault.ui.ThemeUtil
import java.io.File

class CalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeUtil.apply(this)
        // Remove any decrypted temporary files left over from a previous session.
        try { File(cacheDir, "vault_tmp").deleteRecursively() } catch (_: Exception) { }
    }
}
