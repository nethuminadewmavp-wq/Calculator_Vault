package com.nethu.calculatorvault.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.nethu.calculatorvault.R
import com.nethu.calculatorvault.databinding.ActivityAppsBinding
import com.nethu.calculatorvault.security.SecureStore
import com.nethu.calculatorvault.security.VaultSession
import java.util.concurrent.Executors

/**
 * Informational list of launcher apps plus a private reference list.
 * It does not hide, disable, uninstall, modify or monitor any app.
 */
class AppsActivity : BaseSecureActivity() {
    private companion object { const val KEY = "private_apps" }

    private lateinit var b: ActivityAppsBinding
    private lateinit var adapter: AppAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private val privateSet = mutableSetOf<String>()
    private var all: List<AppEntry> = emptyList()
    private var showAll = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        b = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.apps_title)

        try { privateSet.addAll(SecureStore.prefs(this).getStringSet(KEY, emptySet()) ?: emptySet()) }
        catch (e: Exception) { Toast.makeText(this, "Could not read the saved list", Toast.LENGTH_LONG).show() }

        adapter = AppAdapter(privateSet, ::openApp) { app, checked ->
            if (checked) privateSet.add(app.pkg) else privateSet.remove(app.pkg)
            persist()
            if (!showAll) refresh()
        }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.toggle.check(R.id.btn_all)
        b.toggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) { showAll = id == R.id.btn_all; refresh() }
        }

        b.progress.visibility = View.VISIBLE
        executor.execute {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = try {
                pm.queryIntentActivities(launcher, 0)
                    .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                    .filter { it.pkg != packageName }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
            } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                all = list
                b.progress.visibility = View.GONE
                refresh()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun refresh() {
        val shown = if (showAll) all else all.filter { it.pkg in privateSet }
        adapter.submit(shown)
        b.tvEmpty.visibility = if (shown.isEmpty() && !b.progress.isShown) View.VISIBLE else View.GONE
        b.tvEmpty.text = if (showAll) "No launcher apps found." else "Your private list is empty. Tick apps on the All apps tab."
    }

    private fun persist() {
        try { SecureStore.prefs(this).edit().putStringSet(KEY, HashSet(privateSet)).apply() }
        catch (e: Exception) { Toast.makeText(this, "Could not save the list", Toast.LENGTH_LONG).show() }
    }

    private fun openApp(app: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(app.pkg)
        if (intent == null) { Toast.makeText(this, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show(); return }
        try {
            VaultSession.external = true
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }
}
