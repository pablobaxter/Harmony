@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmony.core

import android.os.FileObserver
import java.io.File

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
 */

@JvmSynthetic
internal fun harmonyFileObserver(file: File, block: (event: Int, path: String?) -> Unit): FileObserver {
    return HarmonyFileObserver(file, block)
}

@Suppress("DEPRECATION")
private class HarmonyFileObserver(file: File, private val block: (event: Int, path: String?) -> Unit) : FileObserver(file.path, CLOSE_WRITE) {

    override fun onEvent(event: Int, path: String?) {
        block(event, path)
    }
}
