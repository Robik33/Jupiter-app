package com.marketia.jupiter.core.update

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val mandatory: Boolean,
    val changelog: List<String>
)
