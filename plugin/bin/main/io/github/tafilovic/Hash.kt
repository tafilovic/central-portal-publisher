package io.github.tafilovic

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Hash {
    fun File.md5(): String = hash(this, "MD5")
    fun File.sha1(): String = hash(this, "SHA-1")

    private fun hash(file: File, algorithm: String = "SHA-1"): String {
        val buffer = ByteArray(1024)
        val md = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        // Return the hash value as a hex string
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}