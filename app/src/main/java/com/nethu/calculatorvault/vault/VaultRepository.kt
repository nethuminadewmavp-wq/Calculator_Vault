package com.nethu.calculatorvault.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.security.crypto.EncryptedFile
import com.nethu.calculatorvault.security.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Encrypted file storage. Each file is a separate AES-256-GCM streaming-encrypted blob in
 * filesDir/vault/<uuid>.enc ; metadata lives in Keystore-backed encrypted preferences.
 */
object VaultRepository {
    private const val KEY_INDEX = "vault_index"
    private const val BUF = 64 * 1024
    private const val SPACE_MARGIN = 32L * 1024 * 1024

    private fun dir(ctx: Context) = File(ctx.filesDir, "vault").also { it.mkdirs() }
    private fun blob(ctx: Context, id: String) = File(dir(ctx), "$id.enc")

    private fun encFile(ctx: Context, f: File): EncryptedFile =
        EncryptedFile.Builder(
            ctx.applicationContext, f, SecureStore.masterKey(ctx),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

    fun openPlain(ctx: Context, id: String): InputStream =
        BufferedInputStream(encFile(ctx, blob(ctx, id)).openFileInput(), BUF)

    // ---------- index ----------
    @Synchronized
    fun list(ctx: Context): List<VaultItem> {
        val raw = SecureStore.prefs(ctx).getString(KEY_INDEX, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            VaultItem(
                o.getString("id"), o.getString("name"), o.getString("mime"),
                o.getLong("size"), o.getString("sha"), o.getLong("added")
            )
        }
    }

    @Synchronized
    private fun save(ctx: Context, items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime)
                    .put("size", it.size).put("sha", it.sha256).put("added", it.addedAt)
            )
        }
        val ok = SecureStore.prefs(ctx).edit().putString(KEY_INDEX, arr.toString()).commit()
        if (!ok) throw IOException("Could not save the vault index")
    }

    // ---------- import ----------
    fun displayName(ctx: Context, uri: Uri): String {
        var name = uri.lastPathSegment ?: "file"
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
        } catch (_: Exception) { }
        return name
    }

    private fun sanitize(n: String): String =
        n.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun digestOf(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        input.use {
            val buf = ByteArray(BUF)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    /** Copies, encrypts and VERIFIES the file. Throws on any problem; never touches the original. */
    fun importFile(ctx: Context, uri: Uri): VaultItem {
        val cr = ctx.contentResolver
        var size = -1L
        try {
            cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) size = c.getLong(0)
            }
        } catch (_: Exception) { }
        val name = sanitize(displayName(ctx, uri))
        val mime = cr.getType(uri) ?: guessMime(name)
        if (size > 0 && dir(ctx).usableSpace < size + SPACE_MARGIN) throw IOException("Not enough free storage")

        val id = UUID.randomUUID().toString()
        val target = blob(ctx, id)
        try {
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val input = cr.openInputStream(uri) ?: throw IOException("Cannot open the selected file")
            input.use { src ->
                encFile(ctx, target).openFileOutput().use { out ->
                    val buf = ByteArray(BUF)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        total += n
                    }
                    out.flush()
                }
            }
            val sha = hex(md.digest())
            if (digestOf(openPlain(ctx, id)) != sha) {
                throw IOException("Verification failed: encrypted copy does not match the original")
            }
            val item = VaultItem(id, name, mime, total, sha, System.currentTimeMillis())
            synchronized(this) { save(ctx, list(ctx) + item) }
            return item
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    // ---------- export / delete ----------
    /** Decrypts into the user-chosen folder and verifies the written copy. */
    fun exportTo(ctx: Context, item: VaultItem, dest: DocumentFile): Uri {
        val mime = item.mime.ifBlank { "application/octet-stream" }
        val doc = dest.createFile(mime, item.name) ?: throw IOException("Cannot create a file in that folder")
        try {
            val out = ctx.contentResolver.openOutputStream(doc.uri, "w")
                ?: throw IOException("Cannot write to the chosen folder")
            out.use { o ->
                openPlain(ctx, item.id).use { src ->
                    val buf = ByteArray(BUF)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                    }
                    o.flush()
                }
            }
            val written = ctx.contentResolver.openInputStream(doc.uri)
                ?: throw IOException("Cannot re-read the restored file")
            if (digestOf(written) != item.sha256) throw IOException("Restored copy failed verification")
            return doc.uri
        } catch (e: Exception) {
            doc.delete()
            throw e
        }
    }

    fun delete(ctx: Context, ids: Collection<String>) {
        synchronized(this) { save(ctx, list(ctx).filterNot { it.id in ids }) }
        ids.forEach { blob(ctx, it).delete() }
    }

    fun cleanOrphans(ctx: Context) {
        val known = list(ctx).map { "${it.id}.enc" }.toSet()
        dir(ctx).listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
    }

    // ---------- viewing ----------
    fun decodeImage(ctx: Context, id: String, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openPlain(ctx, id).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = openPlain(ctx, id).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = try {
            openPlain(ctx, id).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (deg == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }

    /** Plain temporary copy for video playback. Caller must delete it. Also wiped at app start. */
    fun decryptToCache(ctx: Context, item: VaultItem): File {
        val tmp = File(ctx.cacheDir, "vault_tmp").also { it.mkdirs() }
        if (tmp.usableSpace < item.size + SPACE_MARGIN) throw IOException("Not enough free storage to play this video")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mime) ?: "mp4"
        val f = File(tmp, "${item.id}.$ext")
        try {
            openPlain(ctx, item.id).use { src ->
                f.outputStream().use { o ->
                    val buf = ByteArray(BUF)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            f.delete(); throw e
        }
        return f
    }
}
