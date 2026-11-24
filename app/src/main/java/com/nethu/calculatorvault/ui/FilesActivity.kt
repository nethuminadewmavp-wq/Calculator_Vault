package com.nethu.calculatorvault.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.nethu.calculatorvault.R
import com.nethu.calculatorvault.databinding.ActivityFilesBinding
import com.nethu.calculatorvault.security.VaultSession
import com.nethu.calculatorvault.vault.VaultItem
import com.nethu.calculatorvault.vault.VaultRepository
import java.util.concurrent.Executors

class FilesActivity : BaseSecureActivity() {
    private lateinit var b: ActivityFilesBinding
    private lateinit var adapter: FileAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingRestore: List<VaultItem> = emptyList()

    private val importLauncher = registerForActivityResult(PickDocuments()) { uris ->
        if (uris.isNotEmpty()) importUris(uris)
    }
    private val treeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) restore(uri) else pendingRestore = emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        b = ActivityFilesBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.files_title)

        adapter = FileAdapter(this, ::openItem) { count -> updateButtons(count) }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.btnPhotos.setOnClickListener { pick(arrayOf("image/*")) }
        b.btnVideos.setOnClickListener { pick(arrayOf("video/*")) }
        b.btnDocs.setOnClickListener {
            pick(
                arrayOf(
                    "application/pdf", "text/*", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            )
        }
        b.btnSelectAll.setOnClickListener { adapter.toggleAll() }
        b.btnRestore.setOnClickListener { startRestore() }
        b.btnRemove.setOnClickListener { confirmRemove() }
        updateButtons(0)

        executor.execute {
            try { VaultRepository.cleanOrphans(applicationContext) } catch (_: Exception) { }
            runOnUiThread { if (!isDestroyed) reload() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        if (::adapter.isInitialized) adapter.shutdown()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun reload() {
        try {
            val items = VaultRepository.list(this).sortedByDescending { it.addedAt }
            adapter.submit(items)
            b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            toast("Could not read the vault: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun updateButtons(count: Int) {
        b.btnRestore.isEnabled = count > 0
        b.btnRemove.isEnabled = count > 0
        b.btnSelectAll.text = if (::adapter.isInitialized && adapter.allSelected()) "Clear" else "Select all"
    }

    private fun setBusy(on: Boolean, msg: String = "") {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.tvStatus.visibility = if (on) View.VISIBLE else View.GONE
        b.tvStatus.text = msg
        listOf(b.btnPhotos, b.btnVideos, b.btnDocs, b.btnSelectAll, b.btnRestore, b.btnRemove)
            .forEach { it.isEnabled = !on }
        if (!on) updateButtons(adapter.selectedItems().size)
    }

    private fun pick(types: Array<String>) {
        VaultSession.external = true
        importLauncher.launch(types)
    }

    private fun openItem(item: VaultItem) {
        if (item.isImage || item.isVideo) {
            startActivity(Intent(this, ViewerActivity::class.java).putExtra(ViewerActivity.EXTRA_ID, item.id))
        } else {
            toast("No in-app preview for this file type. Use Restore to export a copy.")
        }
    }

    // ---------- import ----------
    private fun importUris(uris: List<Uri>) {
        setBusy(true, "Encrypting and verifying ${uris.size} file(s)…")
        executor.execute {
            val ok = mutableListOf<Uri>()
            val errors = mutableListOf<String>()
            for (uri in uris) {
                try {
                    VaultRepository.importFile(applicationContext, uri)
                    ok.add(uri)
                } catch (e: Exception) {
                    errors.add("${VaultRepository.displayName(applicationContext, uri)}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setBusy(false)
                reload()
                showImportResult(ok, errors)
            }
        }
    }

    private fun showImportResult(ok: List<Uri>, errors: List<String>) {
        val errText = if (errors.isEmpty()) "" else "\n\nFailed (nothing was changed for these):\n" + errors.joinToString("\n")
        if (ok.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Import failed").setMessage(errors.joinToString("\n"))
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Imported ${ok.size} file(s)")
            .setMessage(
                "The encrypted copies were created and verified.\n\nYour original files have NOT been touched, " +
                    "so they are still visible in Gallery / Files. Delete the originals now?$errText"
            )
            .setPositiveButton("Delete originals") { _, _ -> confirmDeleteOriginals(ok) }
            .setNegativeButton("Keep originals", null)
            .show()
    }

    private fun confirmDeleteOriginals(uris: List<Uri>) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${uris.size} original file(s)?")
            .setMessage("This permanently removes the originals from public storage. The encrypted copies stay in the vault. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteOriginals(uris) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteOriginals(uris: List<Uri>) {
        setBusy(true, "Deleting originals…")
        executor.execute {
            var deleted = 0
            for (u in uris) {
                val done = try { DocumentsContract.deleteDocument(contentResolver, u) } catch (e: Exception) { false }
                if (done) deleted++
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setBusy(false)
                val failed = uris.size - deleted
                AlertDialog.Builder(this)
                    .setTitle("Originals deleted: $deleted of ${uris.size}")
                    .setMessage(
                        if (failed == 0) "Gallery may take a moment to refresh."
                        else "$failed original(s) could not be deleted because their storage provider does not allow apps to delete them. Delete those yourself in Gallery or Files."
                    )
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    // ---------- restore ----------
    private fun startRestore() {
        val sel = adapter.selectedItems()
        if (sel.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Restore ${sel.size} file(s)")
            .setMessage(R.string.restore_explain)
            .setPositiveButton("Choose folder") { _, _ ->
                pendingRestore = sel
                VaultSession.external = true
                treeLauncher.launch(null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restore(tree: Uri) {
        val items = pendingRestore
        pendingRestore = emptyList()
        val dir = DocumentFile.fromTreeUri(this, tree)
        if (items.isEmpty() || dir == null || !dir.canWrite()) {
            toast("Cannot write to the chosen folder.")
            return
        }
        setBusy(true, "Restoring and verifying ${items.size} file(s)…")
        executor.execute {
            val done = mutableListOf<VaultItem>()
            val errors = mutableListOf<String>()
            for (item in items) {
                try {
                    VaultRepository.exportTo(applicationContext, item, dir)
                    done.add(item)
                } catch (e: Exception) {
                    errors.add("${item.name}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setBusy(false)
                showRestoreResult(done, errors)
            }
        }
    }

    private fun showRestoreResult(done: List<VaultItem>, errors: List<String>) {
        val errText = if (errors.isEmpty()) "" else "\n\nFailed:\n" + errors.joinToString("\n")
        if (done.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Restore failed").setMessage(errors.joinToString("\n"))
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Restored ${done.size} file(s)")
            .setMessage("The files were saved to the folder you chose and verified. Also remove them from the vault?$errText")
            .setPositiveButton("Remove from vault") { _, _ -> removeItems(done) }
            .setNegativeButton("Keep in vault", null)
            .show()
    }

    // ---------- remove ----------
    private fun confirmRemove() {
        val sel = adapter.selectedItems()
        if (sel.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Remove ${sel.size} file(s) from the vault?")
            .setMessage("The encrypted copies will be permanently deleted. If you have not restored them first, they cannot be recovered.")
            .setPositiveButton("Remove") { _, _ -> removeItems(sel) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeItems(items: List<VaultItem>) {
        executor.execute {
            var err: String? = null
            try { VaultRepository.delete(applicationContext, items.map { it.id }) }
            catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                reload()
                if (err != null) toast("Could not remove: $err")
            }
        }
    }
}
