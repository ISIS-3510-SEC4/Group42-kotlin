package com.example.espoti.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ============================================================================
// BIOMETRIC CREDENTIAL STORE
// ----------------------------------------------------------------------------
// Firebase Auth cannot sign in "with a fingerprint", so biometric login works
// as a local vault: when the user enrolls, the account's email + password are
// encrypted with an AES-GCM key that lives in the Android Keystore and can ONLY
// be used right after a successful biometric prompt (the Cipher is handed to
// BiometricPrompt as a CryptoObject). Logging in with the fingerprint decrypts
// them and runs the normal Firebase email/password sign-in.
//   - The key never leaves the secure hardware and is invalidated if the
//     device's biometrics change (new fingerprint added) -> must re-enroll.
//   - Only ciphertext + IV + email/uid are kept in a private SharedPreferences
//     file (excluded from backups, see backup_rules / data_extraction_rules).
// ============================================================================
class BiometricCredentialStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** What a successful decryption gives back. */
    data class Credentials(val uid: String, val email: String, val password: String)

    /** True when some account has biometric login enrolled on this device. */
    fun isEnrolled(): Boolean = prefs.contains(KEY_CIPHERTEXT)

    fun isEnrolledFor(uid: String?): Boolean = uid != null && isEnrolled() && prefs.getString(KEY_UID, null) == uid

    /** Email of the enrolled account (shown on the prompt, never secret). */
    fun enrolledEmail(): String? = prefs.getString(KEY_EMAIL, null)

    // ------------------------------------------------------------------ enroll

    /** Creates a fresh biometric-bound key and returns a Cipher ready to ENCRYPT (pass it to the prompt). */
    fun createEnrollCipher(): Cipher {
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
    }

    /** Encrypts and stores the credentials with the (already authenticated) [cipher]. */
    fun saveCredentials(cipher: Cipher, uid: String, email: String, password: String) {
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_EMAIL, email)
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    // ----------------------------------------------------------------- unlock

    /**
     * Returns a Cipher ready to DECRYPT, or null if nothing is enrolled or the
     * key was invalidated (biometrics changed) - in which case the enrollment is wiped.
     */
    fun createDecryptCipher(): Cipher? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: Exception) {
            // KeyPermanentlyInvalidatedException (biometrics changed), missing key, etc.
            clear()
            null
        }
    }

    /** Decrypts the stored password with the (already authenticated) [cipher]. */
    fun readCredentials(cipher: Cipher): Credentials? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val data = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val password = String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
            Credentials(uid, email, password)
        } catch (e: Exception) {
            null
        }
    }

    /** Removes the enrollment (stored data + Keystore key). */
    fun clear() {
        prefs.edit().clear().apply()
        runCatching {
            val keyStore = keyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    // ---------------------------------------------------------------- keystore

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as SecretKey

    private fun generateKey() {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Authentication needed for EVERY use, biometric only (no PIN fallback).
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "espoti_biometric_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFS_NAME = "espoti_biometric"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
    }
}
