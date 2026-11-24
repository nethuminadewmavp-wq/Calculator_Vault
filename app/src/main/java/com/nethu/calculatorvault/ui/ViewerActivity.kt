package com.nethu.calculatorvault.ui

import android.os.Bundle
import android.view.View
import android.widget.MediaController
import com.nethu.calculatorvault.databinding.ActivityViewerBinding
import com.nethu.calculatorvault.vault.VaultRepository
import java.io.File
import java.util.concurrent.Executors

/** In-app viewer for images and videos. Videos are decrypted to a cache file that is deleted on close. */
class ViewerActivity : BaseSecureActivity() {
    companion object { const val EXTRA_ID = "id" }

    private lateinit var b: ActivityViewerBinding
    private val executor = Executors.newSingleThreadExecutor()
    private var tempFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        b = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getStringExtra(EXTRA_ID)
        val item = try { VaultRepository.list(this).firstOrNull { it.id == id } } catch (e: Exception) { null }
        if (item == null) { finish(); return }
        title = item.name

        b.progress.visibility = View.VISIBLE
        if (item.isImage) {
            val dm = resources.displayMetrics
            val maxDim = maxOf(dm.widthPixels, dm.heightPixels)
            executor.execute {
                val bmp = try { VaultRepository.decodeImage(applicationContext, item.id, maxDim) } catch (e: Throwable) { null }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    b.progress.visibility = View.GONE
                    if (bmp != null) { b.ivImage.visibility = View.VISIBLE; b.ivImage.setImageBitmap(bmp) }
                    else showMessage("This image format cannot be previewed on this phone. Use Restore to export it.")
                }
            }
        } else if (item.isVideo) {
            executor.execute {
                var error: String? = null
                var f: File? = null
                try { f = VaultRepository.decryptToCache(applicationContext, item) }
                catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
                runOnUiThread {
                    if (isDestroyed) { f?.delete(); return@runOnUiThread }
                    b.progress.visibility = View.GONE
                    if (f == null) { showMessage(error ?: "Cannot play this video"); return@runOnUiThread }
                    tempFile = f
                    b.video.visibility = View.VISIBLE
                    val mc = MediaController(this)
                    mc.setAnchorView(b.video)
                    b.video.setMediaController(mc)
                    b.video.setOnErrorListener { _, _, _ ->
                        b.video.visibility = View.GONE
                        showMessage("This video format cannot be played on this phone. Use Restore to export it.")
                        true
                    }
                    b.video.setOnPreparedListener { it.start() }
                    b.video.setVideoPath(f.absolutePath)
                }
            }
        } else {
            b.progress.visibility = View.GONE
            showMessage("No preview for this file type. Use Restore to export a copy.")
        }
    }

    private fun showMessage(m: String) {
        b.tvMessage.visibility = View.VISIBLE
        b.tvMessage.text = m
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        try { tempFile?.delete() } catch (_: Exception) { }
    }
}
