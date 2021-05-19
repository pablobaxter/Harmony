package com.frybits.harmony

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
