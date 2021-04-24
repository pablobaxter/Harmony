package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences
import com.frybits.harmony.getHarmonySharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.HarmonyKeysetManager

private const val KEY_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
private const val VALUE_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_value_keyset__"

@JvmSynthetic
internal fun Context.encryptedHarmonyPreferences(
    fileName: String,
    masterKeyAlias: String,
    prefKeyEncryptionScheme: EncryptedSharedPreferences.PrefKeyEncryptionScheme,
    prefValueEncryptionScheme: EncryptedSharedPreferences.PrefValueEncryptionScheme
): SharedPreferences {
    DeterministicAeadConfig.register()
    AeadConfig.register()

    val daeadKeysetHandle: KeysetHandle = HarmonyKeysetManager.Builder()
        .withKeyTemplate(prefKeyEncryptionScheme.keyTemplate)
        .withSharedPref(this, KEY_KEYSET_ALIAS, fileName)
        .withMasterKeyUri(MasterKeys.KEYSTORE_PATH_URI + masterKeyAlias)
        .build().keysetHandle
    val aeadKeysetHandle: KeysetHandle = HarmonyKeysetManager.Builder()
        .withKeyTemplate(prefValueEncryptionScheme.keyTemplate)
        .withSharedPref(this, VALUE_KEYSET_ALIAS, fileName)
        .withMasterKeyUri(MasterKeys.KEYSTORE_PATH_URI + masterKeyAlias)
        .build().keysetHandle

    val daead: DeterministicAead = daeadKeysetHandle.getPrimitive(DeterministicAead::class.java)
    val aead: Aead = aeadKeysetHandle.getPrimitive(Aead::class.java)

    return EncryptedSharedPreferences(fileName, masterKeyAlias, getHarmonySharedPreferences(fileName), aead, daead)
}
