package com.frybits.harmonyprefs.library.core

import android.os.FileObserver
import java.io.File

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

@JvmSynthetic
internal fun harmonyFileObserver(file: File, block: (event: Int, path: String?) -> Unit): FileObserver {
    return HarmonyFileObserver(file, block).apply { startWatching() } // Start watching as soon as the watcher is created
}

private class HarmonyFileObserver(file: File, private val block: (event: Int, path: String?) -> Unit) : FileObserver(file.path, CLOSE_WRITE) {

    override fun onEvent(event: Int, path: String?) {
        block(event, path)
    }
}
