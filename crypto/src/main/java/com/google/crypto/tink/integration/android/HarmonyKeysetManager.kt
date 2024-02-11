// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
// This file was mostly a copy of https://github.com/tink-crypto/tink-java/blob/d06efcd5a4332afa4a018a75a6a08790e41e82db/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java#L1
// It has been converted to Kotlin and modified to work with SecureHarmonyPreferences.
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.integration.android

import android.content.Context
import android.util.Log
import androidx.annotation.GuardedBy
import com.frybits.harmony.secure.HarmonyKeysetReader
import com.frybits.harmony.secure.HarmonyKeysetWriter
import com.frybits.harmony.secure.keysetFile
import com.frybits.harmony.secure.keysetFileLock
import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetManager
import com.google.crypto.tink.KeysetReader
import com.google.crypto.tink.KeysetWriter
import com.google.crypto.tink.shaded.protobuf.InvalidProtocolBufferException
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException

/**
 * A wrapper of [KeysetManager] that supports reading/writing [com.google.crypto.tink.proto.Keyset] across multiple processes.
 *
 * Warning
 *
 * This class reads and writes to a file, thus is best not to run on the UI thread.
 */
internal class HarmonyKeysetManager private constructor(builder: Builder) {

    @GuardedBy("this")
    private var keysetManager: KeysetManager = builder.keysetManager

    /**
     * A builder for [HarmonyKeysetManager].
     *
     *
     * This class is thread-safe.
     */
    internal class Builder {
        private lateinit var reader: KeysetReader
        lateinit var writer: KeysetWriter
            private set
        private var masterKeyUri: String? = null
        var masterKey: Aead? = null
            private set
        private var useKeystore = true
        private var keyTemplate: KeyTemplate? = null
        private var keyStore: KeyStore? = null

        @GuardedBy("this")
        lateinit var keysetManager: KeysetManager

        /** Reads and writes the keyset from harmony shared preferences directory.  */
        fun withHarmony(context: Context, type: String, prefFileName: String): Builder {
            val keysetFile = context.keysetFile(prefFileName, type)
            val keysetFileLock = context.keysetFileLock(prefFileName, type)
            reader = HarmonyKeysetReader(keysetFile, keysetFileLock)
            writer = HarmonyKeysetWriter(keysetFile, keysetFileLock)
            return this
        }

        /**
         * Sets the master key URI.
         *
         *
         * Only master keys stored in Android Keystore is supported. The URI must start with `android-keystore://`.
         */
        fun withMasterKeyUri(masterKeyUri: String): Builder {
            require(masterKeyUri.startsWith(AndroidKeystoreKmsClient.PREFIX)) { "key URI must start with " + AndroidKeystoreKmsClient.PREFIX }
            require(useKeystore) { "cannot call withMasterKeyUri() after calling doNotUseKeystore()" }
            this.masterKeyUri = masterKeyUri
            return this
        }

        /** If the keyset is not found or valid, generates a new one using keyTemplate.  */
        fun withKeyTemplate(keyTemplate: KeyTemplate?): Builder {
            this.keyTemplate = keyTemplate
            return this
        }

        /**
         * Builds and returns a new [HarmonyKeysetManager] with the specified options.
         *
         * @throws IOException If a keyset is found but unusable.
         * @throws KeyStoreException If a master key is found but unusable.
         * @throws GeneralSecurityException If cannot read an existing keyset or generate a new one.
         */
        @Synchronized
        fun build(): HarmonyKeysetManager {
            if (masterKeyUri != null) {
                masterKey = readOrGenerateNewMasterKey()
            }
            keysetManager = readOrGenerateNewKeyset()
            return HarmonyKeysetManager(this)
        }

        private fun readOrGenerateNewMasterKey(): Aead? {
            val client: AndroidKeystoreKmsClient = if (keyStore != null) {
                AndroidKeystoreKmsClient.Builder().setKeyStore(keyStore).build()
            } else {
                AndroidKeystoreKmsClient()
            }
            val existed = client.hasKey(masterKeyUri)
            if (!existed) {
                try {
                    AndroidKeystoreKmsClient.generateNewAeadKey(masterKeyUri)
                } catch (ex: GeneralSecurityException) {
                    Log.w(TAG, "cannot use Android Keystore, it'll be disabled", ex)
                    return null
                } catch (ex: ProviderException) {
                    Log.w(TAG, "cannot use Android Keystore, it'll be disabled", ex)
                    return null
                }
            }
            try {
                return client.getAead(masterKeyUri)
            } catch (ex: GeneralSecurityException) {
                // Throw the exception if the key exists but is unusable. We can't recover by generating a
                // new key because there might be existing encrypted data under the unusable key.
                // Users can provide a master key that is stored in StrongBox, which may throw a
                // ProviderException if there's any problem with it.
                if (existed) {
                    throw KeyStoreException("the master key $masterKeyUri exists but is unusable", ex)
                }
                // Otherwise swallow the exception if the key doesn't exist yet. We can recover by disabling
                // Keystore.
                Log.w(TAG, "cannot use Android Keystore, it'll be disabled", ex)
            } catch (ex: ProviderException) {
                if (existed) {
                    throw KeyStoreException("the master key $masterKeyUri exists but is unusable", ex)
                }
                Log.w(TAG, "cannot use Android Keystore, it'll be disabled", ex)
            }
            return null
        }

        private fun readOrGenerateNewKeyset(): KeysetManager {
            try {
                return read()
            } catch (ex: FileNotFoundException) {
                // Not found, handle below.
                Log.i(TAG, "keyset not found, will generate a new one. ${ex.message}")
            }

            // Not found.
            if (keyTemplate != null) {
                var manager = KeysetManager.withEmptyKeyset().add(keyTemplate)
                val keyId = manager.keysetHandle.keysetInfo.getKeyInfo(0).keyId
                manager = manager.setPrimary(keyId)
                if (masterKey != null) {
                    manager.keysetHandle.write(writer, masterKey)
                } else {
                    CleartextKeysetHandle.write(manager.keysetHandle, writer)
                }
                return manager
            }
            throw GeneralSecurityException("cannot read or generate keyset")
        }

        private fun read(): KeysetManager {
            if (masterKey != null) {
                try {
                    return KeysetManager.withKeysetHandle(KeysetHandle.read(reader, masterKey))
                } catch (ex: InvalidProtocolBufferException) {
                    // Swallow the exception and attempt to read the keyset in cleartext.
                    // This edge case may happen when either
                    //   - the keyset was generated on a pre M phone which is then upgraded to M or newer, or
                    //   - the keyset was generated with Keystore being disabled, then Keystore is enabled.
                    // By ignoring the security failure here, an adversary with write access to private
                    // preferences can replace an encrypted keyset (that it cannot read or write) with a
                    // cleartext value that it controls. This does not introduce new security risks because to
                    // overwrite the encrypted keyset in private preferences of an app, said adversaries must
                    // have the same privilege as the app, thus they can call Android Keystore to read or
                    // write
                    // the encrypted keyset in the first place.
                    Log.w(TAG, "cannot decrypt keyset: ", ex)
                } catch (ex: GeneralSecurityException) {
                    Log.w(TAG, "cannot decrypt keyset: ", ex)
                }
            }
            return KeysetManager.withKeysetHandle(CleartextKeysetHandle.read(reader))
        }
    }


    /** @return a [KeysetHandle] of the managed keyset
     */
    @get:Synchronized
    val keysetHandle: KeysetHandle
        get() = keysetManager.keysetHandle

    companion object {
        private val TAG = HarmonyKeysetManager::class.java.simpleName
    }
}
