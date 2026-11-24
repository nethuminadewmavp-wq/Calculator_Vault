package com.nethu.calculatorvault.security

import android.content.Context
import android.util.Base64
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN handling: only a salted PBKDF2 hash is stored (inside Keystore-backed encrypted prefs). */
object PinManager {
    enum class Result { OK, WRONG, LOCKED, NO_PIN }
    data class Verify(val result: Result, val waitSeconds: Long = 0)

    private const val K_HASH = "pin_hash"
    private const val K_SALT = "pin_salt"
    private const val K_FAILS = "pin_fails"
    private const val K_LOCK_UNTIL = "pin_lock_until"
    private const val ITERATIONS = 60_000
    private const val MAX_FAILS = 5
    private val lock = Any()

    fun isPinSet(ctx: Context): Boolean =
        try { SecureStore.prefs(ctx).contains(K_HASH) } catch (e: Exception) { false }

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        val ok = SecureStore.prefs(ctx).edit()
            .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(K_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(K_FAILS, 0)
            .putLong(K_LOCK_UNTIL, 0L)
            .commit()
        if (!ok) throw IOException("Could not save the PIN")
    }

    fun verify(ctx: Context, pin: String): Verify {
        return synchronized(lock) {
            val p = SecureStore.prefs(ctx)
            val saltS = p.getString(K_SALT, null)
            val hashS = p.getString(K_HASH, null)
            if (saltS == null || hashS == null) return@synchronized Verify(Result.NO_PIN)

            val now = System.currentTimeMillis()
            val until = p.getLong(K_LOCK_UNTIL, 0L)
            if (now < until) return@synchronized Verify(Result.LOCKED, (until - now + 999) / 1000)

            val expected = Base64.decode(hashS, Base64.NO_WRAP)
            val actual = derive(pin, Base64.decode(saltS, Base64.NO_WRAP))
            if (MessageDigest.isEqual(expected, actual)) {
                p.edit().putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0L).apply()
                Verify(Result.OK)
            } else {
                val fails = p.getInt(K_FAILS, 0) + 1
                val e = p.edit().putInt(K_FAILS, fails)
                if (fails >= MAX_FAILS) {
                    val delay = minOf(30_000L shl minOf(fails - MAX_FAILS, 5), 15 * 60_000L)
                    e.putLong(K_LOCK_UNTIL, now + delay)
                }
                e.apply()
                Verify(Result.WRONG)
            }
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
