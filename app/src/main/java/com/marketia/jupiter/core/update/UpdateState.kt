package com.marketia.jupiter.core.update

import java.io.File

sealed class UpdateState {
    object Idle        : UpdateState()
    object Checking    : UpdateState()
    object UpToDate    : UpdateState()
    data class Available(val manifest: UpdateManifest) : UpdateState()
    data class Downloading(val progress: Int)          : UpdateState()
    object Verifying   : UpdateState()
    data class Downloaded(val file: File)              : UpdateState()
    object Installing  : UpdateState()
    data class Failed(val reason: String)              : UpdateState()
}
