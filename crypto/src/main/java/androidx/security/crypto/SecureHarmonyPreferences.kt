@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.ArraySet
import com.frybits.harmony.OnHarmonySharedPreferenceChangedListener
import com.frybits.harmony.getHarmonySharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.HarmonyKeysetManager
import com.google.crypto.tink.subtle.Base64
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.WeakHashMap
import kotlin.text.Charsets.UTF_8

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
 * An implementation of {@link SharedPreferences} that encrypts keys and values and is process-safe.
 */

internal const val KEY_KEYSET = "KEY_KEYSET"
internal const val VALUE_KEYSET = "VALUE_KEYSET"
private const val NULL_VALUE = "__NULL__"

private class SecureHarmonyPreferencesImpl(
    private val fileName: String,
    private val sharedPreferences: SharedPreferences,
    private val aead: Aead,
    private val deterministicAead: DeterministicAead
) : SharedPreferences {

    private val changedListeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, SecureWrappedOnSharedPreferenceChangeListener>()

    private inner class SecureEditor(private val editor: SharedPreferences.Editor) : SharedPreferences.Editor by editor {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            val mutableValue: String = value ?: NULL_VALUE
            val stringBytes = mutableValue.toByteArray(UTF_8)
            val stringByteLength = stringBytes.size
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES + stringByteLength)
            buffer.putInt(EncryptedType.STRING)
            buffer.putInt(stringByteLength)
            buffer.put(stringBytes)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            val mutableValues = values ?: setOf(NULL_VALUE)
            val byteValues = arrayListOf<ByteArray>()
            var totalBytes = mutableValues.size * Int.SIZE_BYTES
            mutableValues.forEach { strValue ->
                val byteValue = strValue.toByteArray(UTF_8)
                byteValues.add(byteValue)
                totalBytes += byteValue.size
            }
            totalBytes += Int.SIZE_BYTES
            val buffer = ByteBuffer.allocate(totalBytes)
            buffer.putInt(EncryptedType.STRING_SET)
            byteValues.forEach { bytes ->
                buffer.putInt(bytes.size)
                buffer.put(bytes)
            }
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES)
            buffer.putInt(EncryptedType.INT)
            buffer.putInt(value)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES)
            buffer.putInt(EncryptedType.LONG)
            buffer.putLong(value)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Float.SIZE_BYTES)
            buffer.putInt(EncryptedType.FLOAT)
            buffer.putFloat(value)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Byte.SIZE_BYTES)
            buffer.putInt(EncryptedType.BOOLEAN)
            buffer.put(if (value) 1.toByte() else 0.toByte())
            putEncryptedObject(key, buffer.array())
            return this
        }

        private fun putEncryptedObject(key: String?, value: ByteArray) {
            val mutableKey = key ?: NULL_VALUE
            try {
                val encryptedPair = encryptKeyValuePair(mutableKey, value)
                editor.putString(encryptedPair.first, encryptedPair.second)
            } catch (e: GeneralSecurityException) {
                throw SecurityException("Could not encrypt data: ${e.message}", e)
            }
        }
    }

    // SharedPreferences methods

    override fun getAll(): MutableMap<String?, *> {
        val allEntries = hashMapOf<String?, Any?>()
        sharedPreferences.all.keys.forEach { key ->
            val decryptedKey = decryptKey(key)
            allEntries[decryptedKey] = getDecryptedObject(decryptedKey)
        }
        return allEntries
    }

    override fun getString(key: String?, defValue: String?): String? {
        val obj = getDecryptedObject(key)
        return obj as String? ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val obj = getDecryptedObject(key)
        @Suppress("UNCHECKED_CAST")
        return (obj as Set<String>?)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val obj = getDecryptedObject(key)
        return obj as Int? ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        val obj = getDecryptedObject(key)
        return obj as Long? ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        val obj = getDecryptedObject(key)
        return obj as Float? ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val obj = getDecryptedObject(key)
        return obj as Boolean? ?: defValue
    }

    override fun contains(key: String?): Boolean {
        val encryptedKey = encryptKey(key)
        return sharedPreferences.contains(encryptedKey)
    }

    override fun edit(): SharedPreferences.Editor {
        return SecureEditor(sharedPreferences.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) {
            val secureListener = SecureWrappedOnSharedPreferenceChangeListener(this, listener)
            changedListeners[listener] = secureListener
            sharedPreferences.registerOnSharedPreferenceChangeListener(secureListener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) {
            val secureListener = changedListeners.remove(listener)
            if (secureListener != null) {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(secureListener)
            }
        }
    }

    /**
     * Internal enum to set the type of encrypted data.
     */
    private object EncryptedType {
        const val STRING = 0
        const val STRING_SET = 1
        const val INT = 2
        const val LONG = 3
        const val FLOAT = 4
        const val BOOLEAN = 5
    }

    private fun getDecryptedObject(key: String?): Any? {
        val mutableKey: String = key ?: NULL_VALUE

        try {
            val encryptedKey = encryptKey(mutableKey)
            val encryptedValue = sharedPreferences.getString(encryptedKey, null)
            if (encryptedValue != null) {
                val cipherText = Base64.decode(encryptedValue, Base64.DEFAULT)
                val result = aead.decrypt(cipherText, encryptedKey.toByteArray(UTF_8))
                val buffer = ByteBuffer.wrap(result)
                buffer.position(0)
                when (buffer.int) {
                    EncryptedType.STRING -> {
                        val stringLength = buffer.int
                        val stringSlice = buffer.slice()
                        buffer.limit(stringLength)
                        val stringValue = UTF_8.decode(stringSlice).toString()
                        return if (stringValue == NULL_VALUE) {
                            null
                        } else {
                            stringValue
                        }
                    }
                    EncryptedType.INT -> return buffer.int
                    EncryptedType.LONG -> return buffer.long
                    EncryptedType.FLOAT -> return buffer.float
                    EncryptedType.BOOLEAN -> return buffer.get() != 0.toByte()
                    EncryptedType.STRING_SET -> {
                        val stringSet = ArraySet<String>()
                        while (buffer.hasRemaining()) {
                            val subStringLength = buffer.int
                            val subStringSlice = buffer.slice()
                            subStringSlice.limit(subStringLength)
                            buffer.position(buffer.position() + subStringLength)
                            stringSet.add(UTF_8.decode(subStringSlice).toString())
                        }
                        return if (stringSet.size == 1 && stringSet.valueAt(0) == NULL_VALUE) {
                            null
                        } else {
                            stringSet
                        }
                    }
                    else -> return null
                }
            }
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Could not decrypt value. ${e.message}", e)
        }
        return null
    }

    private fun encryptKey(key: String?): String {
        val k = key ?: NULL_VALUE
        try {
            val encryptedKeyBytes = deterministicAead.encryptDeterministically(k.toByteArray(UTF_8), fileName.toByteArray())
            return Base64.encode(encryptedKeyBytes)
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Could not encrypt key. ${e.message}", e)
        }
    }

    fun decryptKey(encryptedKey: String): String? {
        try {
            val clearText = deterministicAead.decryptDeterministically(Base64.decode(encryptedKey, Base64.DEFAULT), fileName.toByteArray())
            var key: String? = String(clearText, UTF_8)
            if (key == NULL_VALUE) {
                key = null
            }
            return key
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Could not decrypt key. ${e.message}", e)
        }
    }

    private fun encryptKeyValuePair(key: String?, bytes: ByteArray): Pair<String, String> {
        val encryptedKey = encryptKey(key)
        val cipherText = aead.encrypt(bytes, encryptedKey.toByteArray(UTF_8))
        return encryptedKey to Base64.encode(cipherText)
    }
}

private class SecureWrappedOnSharedPreferenceChangeListener(
    private val secureHarmonyPreferences: SecureHarmonyPreferencesImpl,
    private val onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
) : OnHarmonySharedPreferenceChangedListener {

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        onSharedPreferenceChangeListener.onSharedPreferenceChanged(secureHarmonyPreferences, key?.let { secureHarmonyPreferences.decryptKey(it) })
    }

    override fun onSharedPreferencesCleared(sharedPreferences: SharedPreferences) {
        if (onSharedPreferenceChangeListener is OnHarmonySharedPreferenceChangedListener) {
            onSharedPreferenceChangeListener.onSharedPreferencesCleared(secureHarmonyPreferences)
        } else {
            onSharedPreferenceChangeListener.onSharedPreferenceChanged(secureHarmonyPreferences, null)
        }
    }
}

@Suppress("FunctionName")
@JvmSynthetic
internal fun SecureHarmonyPreferences(
    fileName: String,
    masterKeyAlias: String,
    context: Context,
    prefKeyEncryptionScheme: EncryptedSharedPreferences.PrefKeyEncryptionScheme,
    prefValueEncryptionScheme: EncryptedSharedPreferences.PrefValueEncryptionScheme
): SharedPreferences {
    DeterministicAeadConfig.register()
    AeadConfig.register()

    val daeadKeysetHandle: KeysetHandle = HarmonyKeysetManager.Builder()
        .withKeyTemplate(prefKeyEncryptionScheme.keyTemplate)
        .withSharedPref(context, KEY_KEYSET, fileName)
        .withMasterKeyUri(MasterKeys.KEYSTORE_PATH_URI + masterKeyAlias)
        .build().keysetHandle
    val aeadKeysetHandle: KeysetHandle = HarmonyKeysetManager.Builder()
        .withKeyTemplate(prefValueEncryptionScheme.keyTemplate)
        .withSharedPref(context, VALUE_KEYSET, fileName)
        .withMasterKeyUri(MasterKeys.KEYSTORE_PATH_URI + masterKeyAlias)
        .build().keysetHandle

    val daead: DeterministicAead = daeadKeysetHandle.getPrimitive(DeterministicAead::class.java)
    val aead: Aead = aeadKeysetHandle.getPrimitive(Aead::class.java)

    return SecureHarmonyPreferencesImpl(fileName, context.getHarmonySharedPreferences(fileName), aead, daead)
}
