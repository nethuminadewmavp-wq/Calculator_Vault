package com.nethu.calculatorvault.ui

import android.content.Intent
import android.os.Bundle
import com.nethu.calculatorvault.R
import com.nethu.calculatorvault.databinding.ActivityVaultHomeBinding
import com.nethu.calculatorvault.security.VaultSession

class VaultHomeActivity : BaseSecureActivity() {
    private lateinit var b: ActivityVaultHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        b = ActivityVaultHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        setTitle(R.string.vault_title)

        b.btnFiles.setOnClickListener { startActivity(Intent(this, FilesActivity::class.java)) }
        b.btnApps.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
        b.btnChangePin.setOnClickListener {
            startActivity(Intent(this, PinActivity::class.java).putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE))
        }
        b.btnPrivacy.setOnClickListener { startActivity(Intent(this, PrivacyActivity::class.java)) }
        b.btnTheme.setOnClickListener {
            val next = (ThemeUtil.mode(this) + 1) % 3
            VaultSession.external = true   // activity is re-created; keep the session alive
            ThemeUtil.set(this, next)
        }
        b.btnLock.setOnClickListener {
            VaultSession.lock()
            finish()
        }
        updateThemeLabel()
    }

    private fun updateThemeLabel() {
        b.btnTheme.text = "Theme: " + ThemeUtil.label(ThemeUtil.mode(this))
    }
}
