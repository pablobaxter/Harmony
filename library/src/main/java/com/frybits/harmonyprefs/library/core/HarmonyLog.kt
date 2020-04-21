@file:Suppress("unused")

package com.frybits.harmonyprefs.library.core

import android.util.Log
import com.frybits.harmonyprefs.library.BuildConfig

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 *
 * Logger tool
 */

internal inline fun <reified T> T.logVerbose(tag: String, msg: String, throwable: Throwable? = null) {
    log<T>(Log.VERBOSE, tag, msg)
    throwable?.let { 
        log<T>(Log.VERBOSE, tag, Log.getStackTraceString(it))
    }
}

internal inline fun <reified T> T.logDebug(tag: String, msg: String, throwable: Throwable? = null) {
    log<T>(Log.DEBUG, tag, msg)
    throwable?.let {
        log<T>(Log.DEBUG, tag, Log.getStackTraceString(it))
    }
}

internal inline fun <reified T> T.logInfo(tag: String, msg: String, throwable: Throwable? = null) {
    log<T>(Log.INFO, tag, msg)
    throwable?.let {
        log<T>(Log.INFO, tag, Log.getStackTraceString(it))
    }
}

internal inline fun <reified T> T.logWarn(tag: String, msg: String, throwable: Throwable? = null) {
    log<T>(Log.WARN, tag, msg)
    throwable?.let {
        log<T>(Log.WARN, tag, Log.getStackTraceString(it))
    }
}

internal inline fun <reified T> T.logError(tag: String, msg: String, throwable: Throwable? = null) {
    log<T>(Log.ERROR, tag, msg)
    throwable?.let {
        log<T>(Log.ERROR, tag, Log.getStackTraceString(it))
    }
}

internal inline fun <reified T> T.logWTF(tag: String, msg: String, throwable: Throwable? = null) {
    log<T>(Log.ASSERT, tag, msg)
    throwable?.let {
        log<T>(Log.ASSERT, tag, Log.getStackTraceString(it))
    }
}

private inline fun <reified T> log(priority: Int, tag: String, msg: String) {
    if (BuildConfig.DEBUG) Log.println(priority, T::class.java.simpleName, "$tag: $msg")
}
