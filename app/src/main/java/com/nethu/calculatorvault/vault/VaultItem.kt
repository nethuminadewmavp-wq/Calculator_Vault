package com.nethu.calculatorvault.vault

data class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    val addedAt: Long
) {
    val isImage get() = mime.startsWith("image/")
    val isVideo get() = mime.startsWith("video/")
}
