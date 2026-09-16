package dev.bema.shared.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual object PlatformKeyValueStore : KeyValueStore {
    private const val KEY_ALIAS = "bema.storage.key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private var preferences: SharedPreferences? = null

    fun install(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("bema_settings", Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences = preferences
        ?: error("PlatformKeyValueStore.install(context) must be called before using shared storage")

    override fun getString(key: String): String? {
        val encrypted = prefs().getString(key, null) ?: return null
        val (iv, ciphertext) = encrypted.split('.', limit = 2).let { parts ->
            require(parts.size == 2) { "Encrypted storage value is malformed" }
            Base64.decode(parts[0], Base64.NO_WRAP) to Base64.decode(parts[1], Base64.NO_WRAP)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(ciphertext).decodeToString()
    }

    override fun putString(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, encryptionKey())
        }
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs().edit().putString(key, encoded).apply()
    }

    override fun remove(key: String) {
        prefs().edit().remove(key).apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(specification)
            generateKey()
        }
    }
}
