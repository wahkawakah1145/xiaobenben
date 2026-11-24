package com.example.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupPasswordRequiredException(
    message: String = "该加密备份设置了自定义密码，请输入密码后解密恢复"
) : IllegalStateException(message)

class BackupWrongPasswordException(
    message: String = "备份解密密码错误或文件已损坏，请核对后重试"
) : IllegalStateException(message)

data class DecodedBackupEnvelope(
    val jsonText: String,
    val wasEncryptedZip: Boolean,
    val usedCustomPassword: Boolean
)

/**
 * Encrypts and packages ledger backups into a ZIP archive containing an AES-256-GCM encrypted
 * binary payload (`backup.enc`) and a non-sensitive metadata manifest (`manifest.json`), while
 * seamlessly reading legacy plaintext JSON backups for full backward compatibility.
 *
 * Uses only JDK/Android built-in `javax.crypto` and `java.util.zip` (0 KB extra APK dependency).
 */
object BackupCryptoCodec {

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_ENCRYPTED_PAYLOAD = "backup.enc"

    private val MAGIC = byteArrayOf('X'.code.toByte(), 'B'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private const val FLAG_CUSTOM_PASSWORD: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 100_000
    private const val MAX_ENTRY_BYTES = 64 * 1024 * 1024 // 64 MB safety guard

    // Application-scoped KDF seed used only when the user exports without setting a custom password.
    // Combined with a fresh 128-bit random salt & PBKDF2-HMAC-SHA256 per export so every archive
    // produces unique AES-256-GCM ciphertext.
    private val DEFAULT_KDF_SEED = "com.live150yo.lvshuiqingshan::xbb-backup-v1".toCharArray()

    fun isZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
    }

    fun encodeEncryptedZip(
        backupJson: String,
        password: String? = null
    ): ByteArray {
        val cleanPassword = password?.takeIf { it.isNotEmpty() }
        val compressed = gzipBytes(backupJson.toByteArray(Charsets.UTF_8))

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val keyChars = cleanPassword?.toCharArray() ?: DEFAULT_KDF_SEED
        val keySpec = deriveKey(keyChars, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val aad = buildAad(requiresCustomPassword = cleanPassword != null)
        cipher.updateAAD(aad)
        val cipherBytes = cipher.doFinal(compressed)

        val payloadOut = ByteArrayOutputStream(MAGIC.size + 1 + SALT_BYTES + IV_BYTES + cipherBytes.size)
        DataOutputStream(payloadOut).use { dos ->
            dos.write(MAGIC)
            dos.writeByte(if (cleanPassword != null) FLAG_CUSTOM_PASSWORD.toInt() else 0)
            dos.write(salt)
            dos.write(iv)
            dos.write(cipherBytes)
        }
        val encryptedBinary = payloadOut.toByteArray()

        val manifestJson = JSONObject().apply {
            put("format", "xbb-encrypted-backup-v1")
            put("cipher", "AES-256-GCM")
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", PBKDF2_ITERATIONS)
            put("payload", ENTRY_ENCRYPTED_PAYLOAD)
            put("requiresPassword", cleanPassword != null)
            put("createdAtMillis", System.currentTimeMillis())
        }.toString()

        val zipOutStream = ByteArrayOutputStream(encryptedBinary.size + 512)
        ZipOutputStream(zipOutStream).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(ENTRY_ENCRYPTED_PAYLOAD))
            zos.write(encryptedBinary)
            zos.closeEntry()
        }
        return zipOutStream.toByteArray()
    }

    /**
     * Decodes either a new encrypted `.zip` backup or a legacy plaintext `.json` backup.
     * Throws [BackupPasswordRequiredException] if the archive requires a user password that was not supplied,
     * or [BackupWrongPasswordException] if authentication fails.
     */
    fun decodeBackupBytes(
        rawBytes: ByteArray,
        password: String? = null
    ): DecodedBackupEnvelope {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("所选备份文件为空")
        }
        if (isZipArchive(rawBytes)) {
            return decodeEncryptedZip(rawBytes, password)
        }
        val text = decodePlaintextJson(rawBytes)
        return DecodedBackupEnvelope(
            jsonText = text,
            wasEncryptedZip = false,
            usedCustomPassword = false
        )
    }

    /**
     * Inspects whether a backup byte array is an encrypted ZIP requiring a user password.
     */
    fun requiresPassword(rawBytes: ByteArray): Boolean {
        if (!isZipArchive(rawBytes)) return false
        return runCatching {
            val extracted = extractZipEntries(rawBytes)
            val payload = extracted[ENTRY_ENCRYPTED_PAYLOAD]
            if (payload != null && payload.size > MAGIC.size) {
                val flags = payload[MAGIC.size]
                return@runCatching (flags.toInt() and FLAG_CUSTOM_PASSWORD.toInt()) != 0
            }
            val manifest = extracted[ENTRY_MANIFEST]
            if (manifest != null) {
                val obj = JSONObject(String(manifest, Charsets.UTF_8))
                return@runCatching obj.optBoolean("requiresPassword", false)
            }
            false
        }.getOrDefault(false)
    }

    private fun decodeEncryptedZip(
        zipBytes: ByteArray,
        password: String?
    ): DecodedBackupEnvelope {
        val entries = extractZipEntries(zipBytes)

        // Check for encrypted payload first
        val encBytes = entries[ENTRY_ENCRYPTED_PAYLOAD]
        if (encBytes != null) {
            val minSize = MAGIC.size + 1 + SALT_BYTES + IV_BYTES + 16
            if (encBytes.size < minSize) {
                throw IllegalArgumentException("加密备份数据包不完整或已损坏")
            }
            val dis = DataInputStream(ByteArrayInputStream(encBytes))
            val magic = ByteArray(MAGIC.size)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw IllegalArgumentException("不支持的加密备份文件头")
            }
            val flags = dis.readByte()
            val requiresCustomPassword = (flags.toInt() and FLAG_CUSTOM_PASSWORD.toInt()) != 0
            val salt = ByteArray(SALT_BYTES).also { dis.readFully(it) }
            val iv = ByteArray(IV_BYTES).also { dis.readFully(it) }
            val cipherBytes = encBytes.copyOfRange(MAGIC.size + 1 + SALT_BYTES + IV_BYTES, encBytes.size)

            if (requiresCustomPassword && password.isNullOrEmpty()) {
                throw BackupPasswordRequiredException()
            }

            val keyChars = if (requiresCustomPassword) {
                password!!.toCharArray()
            } else {
                DEFAULT_KDF_SEED
            }
            val keySpec = deriveKey(keyChars, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildAad(requiresCustomPassword))

            val plainCompressed = try {
                cipher.doFinal(cipherBytes)
            } catch (e: AEADBadTagException) {
                throw BackupWrongPasswordException(
                    if (requiresCustomPassword) "解密密码不正确或备份文件已被篡改" else "备份文件校验失败或已损坏"
                )
            }
            val jsonBytes = gunzipBytes(plainCompressed)
            return DecodedBackupEnvelope(
                jsonText = String(jsonBytes, Charsets.UTF_8),
                wasEncryptedZip = true,
                usedCustomPassword = requiresCustomPassword
            )
        }

        // Compatibility fallback: if someone zipped a legacy .json directly
        val legacyJsonEntry = entries.entries.firstOrNull { (name, _) ->
            name.endsWith(".json", ignoreCase = true) && !name.equals(ENTRY_MANIFEST, ignoreCase = true)
        }?.value
        if (legacyJsonEntry != null) {
            return DecodedBackupEnvelope(
                jsonText = decodePlaintextJson(legacyJsonEntry),
                wasEncryptedZip = false,
                usedCustomPassword = false
            )
        }

        throw IllegalArgumentException("压缩包内未找到有效的备份数据文件")
    }

    private fun extractZipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            var entryCount = 0
            while (entry != null) {
                entryCount++
                if (entryCount > 32) {
                    throw IllegalArgumentException("备份压缩包条目数量异常")
                }
                val rawName = entry.name.orEmpty().replace('\\', '/')
                // Zip-Slip path traversal guard
                if (rawName.contains("..") || rawName.startsWith("/")) {
                    throw SecurityException("检测到非法压缩包路径：$rawName")
                }
                if (!entry.isDirectory) {
                    val baseName = rawName.substringAfterLast('/')
                    if (baseName.isNotEmpty()) {
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var total = 0
                        var n: Int
                        while (zis.read(buf).also { n = it } != -1) {
                            total += n
                            if (total > MAX_ENTRY_BYTES) {
                                throw IllegalArgumentException("备份文件体积超出安全限制")
                            }
                            out.write(buf, 0, n)
                        }
                        result[baseName] = out.toByteArray()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun decodePlaintextJson(rawBytes: ByteArray): String {
        var text = String(rawBytes, Charsets.UTF_8)
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1)
        }
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) {
            throw IllegalArgumentException("无法识别的备份文件格式，请选择小本本加密备份 (.zip) 或旧版明文备份 (.json)")
        }
        return trimmed
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    private fun buildAad(requiresCustomPassword: Boolean): ByteArray {
        val flag = if (requiresCustomPassword) "pwd" else "def"
        return "xbb-encrypted-backup-v1:$flag".toByteArray(Charsets.UTF_8)
    }

    private fun gzipBytes(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(input.size / 2 + 64)
        GZIPOutputStream(bos).use { it.write(input) }
        return bos.toByteArray()
    }

    private fun gunzipBytes(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(input.size * 2)
        GZIPInputStream(ByteArrayInputStream(input)).use { gis ->
            val buf = ByteArray(8192)
            var total = 0
            var n: Int
            while (gis.read(buf).also { n = it } != -1) {
                total += n
                if (total > MAX_ENTRY_BYTES) {
                    throw IllegalArgumentException("解压后的备份数据超出安全限制")
                }
                bos.write(buf, 0, n)
            }
        }
        return bos.toByteArray()
    }
}
