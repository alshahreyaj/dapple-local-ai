package com.localai.cutout.core.model

import java.io.File
import java.security.MessageDigest

object ModelVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(128 * 1024)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        // Convert Byte to an unsigned 0..255 value before formatting. Formatting a
        // signed Byte directly can sign-extend it and produce more than two hex digits.
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    fun isValid(file: File): Boolean = file.isFile && file.length() == ModelManager.EXPECTED_BYTES && sha256(file) == ModelManager.SHA256
}
