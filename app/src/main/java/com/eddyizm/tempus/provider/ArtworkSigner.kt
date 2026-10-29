package com.eddyizm.tempus.provider

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * HMAC key for home-page artwork ids. The key stays in the Android Keystore, so no other app can
 * read it or make ids the provider will accept.
 */
object ArtworkSigner {
    private const val ALIAS = "tempus_artwork_hmac"
    private const val ALGORITHM = "HmacSHA256"

    private val key: SecretKey by lazy { loadOrCreateKey() }

    fun mac(data: ByteArray): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(data)

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
        }.generateKey()
    }
}
