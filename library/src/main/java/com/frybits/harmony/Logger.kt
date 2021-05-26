package com.frybits.harmony

/*
 *  Copyright 2021 Pablo Baxter
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
 */

@Volatile
internal var _harmonyLog: HarmonyLog? = null

/**
 * Log injector for Harmony
 */
interface HarmonyLog {

    /**
     * This uses the same values as [android.util.Log] for log priority.
     */
    fun log(priority: Int, msg: String)

    /**
     * Set for future use. Not used in Harmony currently.
     */
    fun recordException(throwable: Throwable)
}