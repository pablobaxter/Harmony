@file:Suppress("unused", "ClassName")
@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmonyprefs.library.core

import android.util.Log
import com.frybits.harmonyprefs.library.BuildConfig

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 *
 * Logger tool
 */

private val LOG = BuildConfig.DEBUG

internal object _InternalHarmonyLog {

    @JvmSynthetic
    internal fun v(tag: String, msg: String?, throwable: Throwable? = null) {
        if (LOG) {
            log(Log.VERBOSE, tag, msg)
            throwable?.let { log(Log.VERBOSE, tag, Log.getStackTraceString(it)) }
        }
    }

    @JvmSynthetic
    internal fun d(tag: String, msg: String?, throwable: Throwable? = null) {
        if (LOG) {
            log(Log.DEBUG, tag, msg)
            throwable?.let { log(Log.DEBUG, tag, Log.getStackTraceString(it)) }
        }
    }

    @JvmSynthetic
    internal fun i(tag: String, msg: String?, throwable: Throwable? = null) {
        if (LOG) {
            log(Log.INFO, tag, msg)
            throwable?.let { log(Log.INFO, tag, Log.getStackTraceString(it)) }
        }
    }

    @JvmSynthetic
    internal fun w(tag: String, msg: String?, throwable: Throwable? = null) {
        if (LOG) {
            log(Log.WARN, tag, msg)
            throwable?.let { log(Log.WARN, tag, Log.getStackTraceString(it)) }
        }
    }

    @JvmSynthetic
    internal fun e(tag: String, msg: String?, throwable: Throwable? = null) {
        if (LOG) {
            log(Log.ERROR, tag, msg)
            throwable?.let { log(Log.ERROR, tag, Log.getStackTraceString(it)) }
        }
    }

    @JvmSynthetic
    internal fun wtf(tag: String, msg: String?, throwable: Throwable? = null) {
        if (LOG) {
            log(Log.ASSERT, tag, msg)
            throwable?.let { log(Log.ASSERT, tag, Log.getStackTraceString(it)) }
        }
    }

    private fun log(priority: Int, tag: String, msg: String?) {
        Log.println(priority, tag, msg)
    }
}
