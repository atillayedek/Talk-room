package com.talkroom.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

// Only encrypted token data is persisted; the encryption key never leaves Android Keystore.
class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
    private val alias = "talkroom.auth.session.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    suspend fun read(): AuthSession? = withContext(Dispatchers.IO) {
        val stored = preferences.getString("encrypted", null) ?: return@withContext null
        try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            apiJson.decodeFromString<AuthSession>(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        } catch (_: Exception) {
            // Reinstallation/key invalidation must lead to login, not a startup crash.
            if (!preferences.edit().remove("encrypted").commit()) {
                throw ApiException("Geçersiz oturum temizlenemedi. Lütfen uygulama verilerini temizleyin.")
            }
            null
        }
    }

    suspend fun write(session: AuthSession?) = withContext(Dispatchers.IO) {
        val editor = preferences.edit()
        if (session == null) editor.remove("encrypted")
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.iv + cipher.doFinal(apiJson.encodeToString(session).toByteArray(Charsets.UTF_8))
            editor.putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        if (!editor.commit()) throw ApiException("Oturum cihazda saklanamadı. Lütfen tekrar deneyin.")
    }
}
