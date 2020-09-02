@file:Suppress("unused", "ClassName", "ObjectPropertyName")
@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmony.internal

import android.util.Log
import com.frybits.harmony.BuildConfig

/*
 *  Copyright 2020 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 * https://github.com/pablobaxter/Harmony
 *
 * Logger tool
 */

private val LOG = BuildConfig.DEBUG

internal object _InternalHarmonyLog {

    @JvmSynthetic
    internal fun v(tag: String, msg: String, throwable: Throwable? = null) {
        log(Log.VERBOSE, tag, msg)
        throwable?.let {
            log(Log.VERBOSE, tag, Log.getStackTraceString(it))
        }
    }

    @JvmSynthetic
    internal fun d(tag: String, msg: String, throwable: Throwable? = null) {
        log(Log.DEBUG, tag, msg)
        throwable?.let {
            log(Log.DEBUG, tag, Log.getStackTraceString(it))
        }
    }

    @JvmSynthetic
    internal fun i(tag: String, msg: String, throwable: Throwable? = null) {
        log(Log.INFO, tag, msg)
        throwable?.let {
            log(Log.INFO, tag, Log.getStackTraceString(it))
        }
    }

    @JvmSynthetic
    internal fun w(tag: String, msg: String, throwable: Throwable? = null) {
        log(Log.WARN, tag, msg)
        throwable?.let {
            log(Log.WARN, tag, Log.getStackTraceString(it))
        }
    }

    @JvmSynthetic
    internal fun e(tag: String, msg: String, throwable: Throwable? = null) {
        log(Log.ERROR, tag, msg)
        throwable?.let {
            log(Log.ERROR, tag, Log.getStackTraceString(it))
        }
    }

    @JvmSynthetic
    internal fun wtf(tag: String, msg: String, throwable: Throwable? = null) {
        log(Log.ASSERT, tag, msg)
        throwable?.let {
            log(Log.ASSERT, tag, Log.getStackTraceString(it))
        }
    }

    private fun log(priority: Int, tag: String, msg: String) {
        if (LOG) { // Only log to console for debug builds
            Log.println(priority, tag, msg)
        }
    }

    private fun getMessage(tag: String?, msg: String?): String {
        return "$tag: $msg"
    }
}
