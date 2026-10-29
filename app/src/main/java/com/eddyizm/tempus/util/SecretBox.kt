package com.eddyizm.tempus.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets (server passwords) with an AES-GCM key held in the Android Keystore.
 *
 * Sealed values look like `v1:<iv hex>:<ciphertext hex>`. A value without the `v1:` prefix is
 * legacy plaintext: [open] returns it unchanged, and callers re-seal it on their next write.
 * When a sealed value can't be opened (for example after the Keystore key is lost), [open]
 * returns null. It never falls back to the stored text, so the caller treats the secret as missing.
 */
object SecretBox {
    private const val PREFIX = "v1:"
    private const val ALIAS = "tempus_secret_box"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun isSealed(stored: String?): Boolean = stored != null && stored.startsWith(PREFIX)

    fun seal(plain: String?): String? {
        if (plain == null) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + toHex(cipher.iv) + ":" + toHex(ciphertext)
    }

    fun open(stored: String?): String? {
        if (stored == null) return null
        if (!isSealed(stored)) return stored
        val parts = stored.removePrefix(PREFIX).split(':')
        if (parts.size != 2) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, fromHex(parts[0])))
            String(cipher.doFinal(fromHex(parts[1])), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    internal fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) append("%02x".format(b.toInt() and 0xff))
    }

    internal fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' }) { "bad hex" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
