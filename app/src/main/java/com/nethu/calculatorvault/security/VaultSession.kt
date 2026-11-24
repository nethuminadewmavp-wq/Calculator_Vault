package com.nethu.calculatorvault.security

import android.os.SystemClock

/**
 * In-memory unlock state. The vault is unlocked only while at least one vault screen is visible.
 * When the last vault screen stops (user left the app, screen off, Home pressed) the vault locks.
 * Exception: while an Android system picker / another app is intentionally opened ([external]),
 * the vault stays unlocked for at most [GRACE_MS].
 */
object VaultSession {
    private const val GRACE_MS = 2 * 60 * 1000L

    @Volatile private var unlocked = false
    @Volatile private var expiresAt = Long.MAX_VALUE
    private var started = 0

    /** Set to true right before intentionally launching a picker or another app. */
    @Volatile var external = false

    fun unlock() { unlocked = true; expiresAt = Long.MAX_VALUE; external = false }

    fun lock() { unlocked = false; expiresAt = Long.MAX_VALUE }

    fun isUnlocked(): Boolean {
        if (unlocked && SystemClock.elapsedRealtime() > expiresAt) unlocked = false
        return unlocked
    }

    fun activityStarted() { started++; external = false; expiresAt = Long.MAX_VALUE }

    fun activityStopped(changingConfigurations: Boolean) {
        started = maxOf(0, started - 1)
        if (started == 0 && !changingConfigurations) {
            if (external) expiresAt = SystemClock.elapsedRealtime() + GRACE_MS else lock()
        }
    }
}
