package com.bigoke.metings.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the phone's hardware-backed Android Keystore.
 * Transcripts, summaries and remembered voices are stored encrypted with it.
 */
object Crypto {
    private const val ALIAS = "notule-data-key"
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), 1)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(text: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val body = c.doFinal(text.toByteArray(Charsets.UTF_8))
        return MAGIC + byteArrayOf(c.iv.size.toByte()) + c.iv + body
    }

    /** Decrypts data written by [encrypt]; plain JSON from older versions is returned as-is. */
    fun decrypt(data: ByteArray): String {
        if (data.size < 5 || !data.copyOfRange(0, 4).contentEquals(MAGIC)) return String(data, Charsets.UTF_8)
        val ivLen = data[4].toInt()
        val iv = data.copyOfRange(5, 5 + ivLen)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(data, 5 + ivLen, data.size - 5 - ivLen), Charsets.UTF_8)
    }
}
