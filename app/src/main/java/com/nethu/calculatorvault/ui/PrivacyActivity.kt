package com.nethu.calculatorvault.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nethu.calculatorvault.R
import com.nethu.calculatorvault.databinding.ActivityPrivacyBinding

class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.privacy_title)
        b.tvBody.setText(R.string.privacy_body)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
