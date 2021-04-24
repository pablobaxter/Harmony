package com.google.crypto.tink.integration.android

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.GuardedBy
import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetManager
import com.google.crypto.tink.KeysetReader
import com.google.crypto.tink.KeysetWriter
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.shaded.protobuf.InvalidProtocolBufferException
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException

/**
 * A wrapper of [KeysetManager] that supports reading/writing [ ] to/from private harmony shared preferences on Android.
 *
 * <h3>Warning</h3>
 *
 *
 * This class reads and writes to harmony shared preferences, thus is best not to run on the UI thread.
 *
 * <h3>Usage</h3>
 *
 * <pre>`// One-time operations, should be done when the application is starting up.
 * // Instead of repeatedly instantiating these crypto objects, instantiate them once and save for
 * // later use.
 * AndroidKeysetManager manager = AndroidKeysetManager.Builder()
 * .withSharedPref(getApplicationContext(), "my_keyset_name", "my_pref_file_name")
 * .withKeyTemplate(AesGcmHkfStreamingKeyManager.aes128GcmHkdf4KBTemplate())
 * .build();
 * StreamingAead streamingAead = manager.getKeysetHandle().getPrimitive(StreamingAead.class);
`</pre> *
 *
 *
 * This will read a keyset stored in the `my_keyset_name` preference of the `my_pref_file_name` preferences file. If the preference file name is null, it uses the default
 * preferences file.
 *
 *
 *  * If a keyset is found, but it is invalid, an [IOException] is thrown. The most common
 * cause is when you decrypted a keyset with a wrong master key. In this case, an [InvalidProtocolBufferException] would be thrown. This is an irrecoverable error. You'd have
 * to delete the keyset in Shared Preferences and all existing data encrypted with it.
 *  * If a keyset is not found, and a [KeyTemplate] is set with [AndroidKeysetManager.Builder.withKeyTemplate], a fresh
 * keyset is generated and is written to the `my_keyset_name` preference of the `my_pref_file_name` shared preferences file.
 *
 *
 * <h3>Key rotation</h3>
 *
 *
 * The resulting manager supports all operations supported by [KeysetManager]. For example
 * to rotate the keyset, you can do:
 *
 * <pre>`manager.rotate(AesGcmHkfStreamingKeyManager.aes128GcmHkdf1MBTemplate());
`</pre> *
 *
 *
 * All operations that manipulate the keyset would automatically persist the new keyset to
 * permanent storage.
 *
 * <h3>Opportunistic keyset encryption with Android Keystore</h3>
 *
 * **Warning:** because Android Keystore is unreliable, we strongly recommend disabling it by not
 * setting any master key URI.
 *
 *
 * If a master key URI is set with [AndroidKeysetManager.Builder.withMasterKeyUri], the
 * keyset may be encrypted with a key generated and stored in [Android Keystore](https://developer.android.com/training/articles/keystore.html).
 *
 *
 * Android Keystore is only available on Android M or newer. Since it has been found that Android
 * Keystore is unreliable on certain devices. Tink runs a self-test to detect such problems and
 * disables Android Keystore accordingly, even if a master key URI is set. You can check whether
 * Android Keystore is in use with [.isUsingKeystore].
 *
 *
 * When Android Keystore is disabled or otherwise unavailable, keysets will be stored in
 * cleartext. This is not as bad as it sounds because keysets remain inaccessible to any other apps
 * running on the same device. Moreover, as of July 2020, most active Android devices support either
 * full-disk encryption or file-based encryption, which provide strong security protection against
 * key theft even from attackers with physical access to the device. Android Keystore is only useful
 * when you want to [require user
 * authentication for key use](https://developer.android.com/training/articles/keystore#UserAuthentication), which should be done if and only if you're absolutely sure that
 * Android Keystore is working properly on your target devices.
 *
 *
 * The master key URI must start with `android-keystore://`. The remaining of the URI is
 * used as a key ID when calling Android Keystore. If the master key doesn't exist, a fresh one is
 * generated. If the master key already exists but is unusable, a [KeyStoreException] is
 * thrown.
 *
 *
 * This class is thread-safe.
 *
 * @since 1.0.0
 */
internal class HarmonyKeysetManager private constructor(builder: Builder) {
    private val writer: KeysetWriter? = builder.writer
    private val masterKey: Aead? = builder.masterKey

    @GuardedBy("this")
    private var keysetManager: KeysetManager? = builder.keysetManager

    /**
     * A builder for [AndroidKeysetManager].
     *
     *
     * This class is thread-safe.
     */
    internal class Builder {
        private var reader: KeysetReader? = null
        var writer: KeysetWriter? = null
            private set
        private var masterKeyUri: String? = null
        var masterKey: Aead? = null
            private set
        private var useKeystore = true
        private var keyTemplate: KeyTemplate? = null
        private var keyStore: KeyStore? = null

        @GuardedBy("this")
        var keysetManager: KeysetManager? = null

        /** Reads and writes the keyset from shared preferences.  */
        @Throws(IOException::class)
        fun withSharedPref(context: Context, keysetName: String, prefFileName: String?): Builder {
            reader = HarmonyKeysetReader(context, keysetName, prefFileName)
            writer = HarmonyKeysetWriter(context, keysetName, prefFileName)
            return this
        }

        /**
         * Sets the master key URI.
         *
         *
         * Only master keys stored in Android Keystore is supported. The URI must start with `android-keystore://`.
         */
        fun withMasterKeyUri(_masterKeyUri: String): Builder {
            require(_masterKeyUri.startsWith(AndroidKeystoreKmsClient.PREFIX)) { "key URI must start with " + AndroidKeystoreKmsClient.PREFIX }
            require(useKeystore) { "cannot call withMasterKeyUri() after calling doNotUseKeystore()" }
            masterKeyUri = _masterKeyUri
            return this
        }

        /**
         * If the keyset is not found or valid, generates a new one using `val`.
         *
         */
        @Deprecated(
            """This method takes a KeyTemplate proto, which is an internal implementation
          detail. Please use the withKeyTemplate method that takes a {@link KeyTemplate} POJO."""
        )
        fun withKeyTemplate(_keyTemplate: com.google.crypto.tink.proto.KeyTemplate): Builder {
            keyTemplate = KeyTemplate.create(_keyTemplate.typeUrl, _keyTemplate.value.toByteArray(), fromProto(_keyTemplate.outputPrefixType))
            return this
        }

        /** If the keyset is not found or valid, generates a new one using `val`.  */
        fun withKeyTemplate(_keyTemplate: KeyTemplate?): Builder {
            keyTemplate = _keyTemplate
            return this
        }

        /**
         * Does not use Android Keystore which might not work well in some phones.
         *
         *
         * **Warning:** When Android Keystore is disabled, keys are stored in cleartext. This
         * should be safe because they are stored in private preferences.
         *
         */
        @Deprecated("Android Keystore can be disabled by not setting a master key URI.")
        fun doNotUseKeystore(): Builder {
            masterKeyUri = null
            useKeystore = false
            return this
        }

        /** This is for testing only  */
        fun withKeyStore(_keyStore: KeyStore?): Builder {
            keyStore = _keyStore
            return this
        }

        /**
         * Builds and returns a new [AndroidKeysetManager] with the specified options.
         *
         * @throws IOException If a keyset is found but unusable.
         * @throws KeystoreException If a master key is found but unusable.
         * @throws GeneralSecurityException If cannot read an existing keyset or generate a new one.
         */
        @Synchronized
        @Throws(GeneralSecurityException::class, IOException::class)
        fun build(): HarmonyKeysetManager {
            if (masterKeyUri != null) {
                masterKey = readOrGenerateNewMasterKey()
            }
            keysetManager = readOrGenerateNewKeyset()
            return HarmonyKeysetManager(this)
        }

        @Throws(GeneralSecurityException::class)
        private fun readOrGenerateNewMasterKey(): Aead? {
            if (!isAtLeastM) {
                Log.w(TAG, "Android Keystore requires at least Android M")
                return null
            }
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

        @Throws(GeneralSecurityException::class, IOException::class)
        private fun readOrGenerateNewKeyset(): KeysetManager {
            try {
                return read()
            } catch (ex: FileNotFoundException) {
                // Not found, handle below.
                Log.w(TAG, "keyset not found, will generate a new one", ex)
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

        @Throws(GeneralSecurityException::class, IOException::class)
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
    @get:Throws(GeneralSecurityException::class)
    @get:Synchronized
    val keysetHandle: KeysetHandle
        get() = keysetManager!!.keysetHandle

    /**
     * Generates and adds a fresh key generated using `keyTemplate`, and sets the new key as the
     * primary key.
     *
     * @throws GeneralSecurityException if cannot find any [KeyManager] that can handle `keyTemplate`
     */
    @Deprecated(
        """Please use {@link #add}. This method adds a new key and immediately promotes it to
        primary. However, when you do keyset rotation, you almost never want to make the new key
        primary, because old binaries don't know the new key yet."""
    )
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun rotate(keyTemplate: com.google.crypto.tink.proto.KeyTemplate?): HarmonyKeysetManager {
        keysetManager = keysetManager!!.rotate(keyTemplate)
        write(keysetManager)
        return this
    }

    /**
     * Generates and adds a fresh key generated using `keyTemplate`.
     *
     * @throws GeneralSecurityException if cannot find any [KeyManager] that can handle `keyTemplate`
     */
    @GuardedBy("this")
    @Deprecated(
        """This method takes a KeyTemplate proto, which is an internal implementation detail.
        Please use the add method that takes a {@link KeyTemplate} POJO."""
    )
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun add(keyTemplate: com.google.crypto.tink.proto.KeyTemplate?): HarmonyKeysetManager {
        keysetManager = keysetManager!!.add(keyTemplate)
        write(keysetManager)
        return this
    }

    /**
     * Generates and adds a fresh key generated using `keyTemplate`.
     *
     * @throws GeneralSecurityException if cannot find any [KeyManager] that can handle `keyTemplate`
     */
    @GuardedBy("this")
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun add(keyTemplate: KeyTemplate?): HarmonyKeysetManager {
        keysetManager = keysetManager!!.add(keyTemplate)
        write(keysetManager)
        return this
    }

    /**
     * Sets the key with `keyId` as primary.
     *
     * @throws GeneralSecurityException if the key is not found or not enabled
     */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun setPrimary(keyId: Int): HarmonyKeysetManager {
        keysetManager = keysetManager!!.setPrimary(keyId)
        write(keysetManager)
        return this
    }

    /**
     * Sets the key with `keyId` as primary.
     *
     * @throws GeneralSecurityException if the key is not found or not enabled
     */
    @Deprecated("use {@link setPrimary}", ReplaceWith("setPrimary(keyId)"))
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun promote(keyId: Int): HarmonyKeysetManager {
        return setPrimary(keyId)
    }

    /**
     * Enables the key with `keyId`.
     *
     * @throws GeneralSecurityException if the key is not found
     */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun enable(keyId: Int): HarmonyKeysetManager {
        keysetManager = keysetManager!!.enable(keyId)
        write(keysetManager)
        return this
    }

    /**
     * Disables the key with `keyId`.
     *
     * @throws GeneralSecurityException if the key is not found or it is the primary key
     */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun disable(keyId: Int): HarmonyKeysetManager {
        keysetManager = keysetManager!!.disable(keyId)
        write(keysetManager)
        return this
    }

    /**
     * Deletes the key with `keyId`.
     *
     * @throws GeneralSecurityException if the key is not found or it is the primary key
     */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun delete(keyId: Int): HarmonyKeysetManager {
        keysetManager = keysetManager!!.delete(keyId)
        write(keysetManager)
        return this
    }

    /**
     * Destroys the key material associated with the `keyId`.
     *
     * @throws GeneralSecurityException if the key is not found or it is the primary key
     */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun destroy(keyId: Int): HarmonyKeysetManager {
        keysetManager = keysetManager!!.destroy(keyId)
        write(keysetManager)
        return this
    }

    /** Returns whether Android Keystore is being used to wrap Tink keysets.  */
    @get:Synchronized
    val isUsingKeystore: Boolean
        get() = shouldUseKeystore()

    @Throws(GeneralSecurityException::class)
    private fun write(manager: KeysetManager?) {
        try {
            if (shouldUseKeystore()) {
                manager!!.keysetHandle.write(writer, masterKey)
            } else {
                CleartextKeysetHandle.write(manager!!.keysetHandle, writer)
            }
        } catch (e: IOException) {
            throw GeneralSecurityException(e)
        }
    }

    private fun shouldUseKeystore(): Boolean {
        return masterKey != null && isAtLeastM
    }

    companion object {
        private val TAG = HarmonyKeysetManager::class.java.simpleName
        private fun fromProto(outputPrefixType: OutputPrefixType): KeyTemplate.OutputPrefixType {
            return when (outputPrefixType) {
                OutputPrefixType.TINK -> KeyTemplate.OutputPrefixType.TINK
                OutputPrefixType.LEGACY -> KeyTemplate.OutputPrefixType.LEGACY
                OutputPrefixType.RAW -> KeyTemplate.OutputPrefixType.RAW
                OutputPrefixType.CRUNCHY -> KeyTemplate.OutputPrefixType.CRUNCHY
                else -> throw IllegalArgumentException("Unknown output prefix type")
            }
        }

        private val isAtLeastM: Boolean
            private get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
