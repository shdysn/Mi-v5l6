package com.mi.explorer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.mi.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mi_vault_prefs", Context.MODE_PRIVATE)

    private val vaultDir: File = File(context.filesDir, "MiVault_Secure").apply {
        if (!exists()) mkdirs()
    }

    private val filesDir: File = File(vaultDir, "vault_files").apply {
        if (!exists()) mkdirs()
    }

    private val vaultKey: SecretKey by lazy {
        getOrCreateVaultKey()
    }

    private fun getOrCreateVaultKey(): SecretKey {
        var keyHex = prefs.getString(KEY_CIPHER_SECRET, null)
        if (keyHex == null) {
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            keyHex = keyBytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_CIPHER_SECRET, keyHex).apply()
        }
        val bytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return SecretKeySpec(bytes, "AES")
    }

    fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hash(pin) == storedHash
    }

    fun setPin(pin: String, securityAnswer: String) {
        prefs.edit()
            .putString(KEY_PIN_HASH, hash(pin))
            .putString(KEY_SECURITY_ANSWER, hash(securityAnswer.trim().lowercase()))
            .apply()
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val storedHash = prefs.getString(KEY_SECURITY_ANSWER, null) ?: return false
        return hash(answer.trim().lowercase()) == storedHash
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    suspend fun getVaultFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val files = filesDir.listFiles() ?: return@withContext emptyList()
        files.map { FileItem(it) }.sortedByDescending { it.lastModified }
    }

    /**
     * Encrypts and moves source file into encrypted vault storage using AES-256-CBC.
     */
    suspend fun addToVault(source: File): Boolean = withContext(Dispatchers.IO) {
        if (!source.exists() || source.isDirectory) return@withContext false
        try {
            val dest = File(filesDir, source.name)
            val finalDest = if (dest.exists()) {
                File(filesDir, "${System.currentTimeMillis()}_${source.name}")
            } else dest

            val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, vaultKey, IvParameterSpec(iv))

            FileOutputStream(finalDest).use { fos ->
                fos.write(MAGIC_HEADER)
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(source).use { fis ->
                        fis.copyTo(cos)
                    }
                }
            }

            source.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Decrypts and restores vault file to target directory using AES-256-CBC.
     */
    suspend fun restoreFromVault(vaultFile: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext false
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val dest = File(targetDir, vaultFile.name)

            FileInputStream(vaultFile).use { fis ->
                val header = ByteArray(MAGIC_HEADER.size)
                val readHeader = fis.read(header)
                if (readHeader == MAGIC_HEADER.size && header.contentEquals(MAGIC_HEADER)) {
                    val iv = ByteArray(16)
                    val readIv = fis.read(iv)
                    if (readIv == 16) {
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(Cipher.DECRYPT_MODE, vaultKey, IvParameterSpec(iv))
                        CipherInputStream(fis, cipher).use { cis ->
                            FileOutputStream(dest).use { fos ->
                                cis.copyTo(fos)
                            }
                        }
                    } else {
                        // Fallback copy
                        FileOutputStream(dest).use { fos -> fis.copyTo(fos) }
                    }
                } else {
                    // Plaintext legacy fallback
                    FileOutputStream(dest).use { fos ->
                        fos.write(header, 0, readHeader)
                        fis.copyTo(fos)
                    }
                }
            }

            vaultFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFromVault(vaultFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            vaultFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val MAGIC_HEADER = byteArrayOf(0x4D, 0x49, 0x56, 0x31) // "MIV1"
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_SECURITY_ANSWER = "vault_security_answer"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
        private const val KEY_CIPHER_SECRET = "vault_cipher_secret_key"
    }
}
