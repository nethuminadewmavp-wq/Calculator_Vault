package com.nethu.calculatorvault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed master key and encrypted preferences shared by the whole app. */
object SecureStore {
    private var cachedPrefs: SharedPreferences? = null
    private var cachedKey: MasterKey? = null

    @Synchronized
    fun masterKey(ctx: Context): MasterKey {
        cachedKey?.let { return it }
        val k = MasterKey.Builder(ctx.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        cachedKey = k
        return k
    }

    @Synchronized
    fun prefs(ctx: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        val app = ctx.applicationContext
        val p = EncryptedSharedPreferences.create(
            app,
            "vault_secure_prefs",
            masterKey(app),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        cachedPrefs = p
        return p
    }
}
