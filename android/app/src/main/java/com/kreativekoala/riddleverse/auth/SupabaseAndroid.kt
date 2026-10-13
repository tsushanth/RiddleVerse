package com.kreativekoala.riddleverse.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** OkHttp-backed transport. Never logs request/response bodies (they carry tokens). */
class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val b = Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> b.header(k, v) }
        val body = request.body?.toRequestBody("application/json".toMediaType())
        b.method(request.method, body)
        client.newCall(b.build()).execute().use { r ->
            return HttpResponse(r.code, r.body?.string().orEmpty())
        }
    }
}

/** AES-GCM with a non-exportable Android Keystore key. Format: base64(iv || ciphertext). */
class KeystoreSessionCipher(private val alias: String = "rv_supabase_session_key") : SessionCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

object SupabaseAuth {
    private const val PREFS = "rv_supabase_auth"
    private const val KEY = "session_enc"

    @Volatile private var repo: SupabaseAuthRepository? = null

    fun repository(context: Context): SupabaseAuthRepository =
        repo ?: synchronized(this) {
            repo ?: run {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val kv = object : EncryptedSessionStore.KeyValue {
                    override fun get() = prefs.getString(KEY, null)
                    override fun put(value: String) { prefs.edit().putString(KEY, value).apply() }
                    override fun remove() { prefs.edit().remove(KEY).apply() }
                }
                SupabaseAuthRepository(OkHttpTransport(), EncryptedSessionStore(kv, KeystoreSessionCipher()))
                    .also { repo = it }
            }
        }

    /** Clears the stored Supabase session. Call on every sign-out. */
    fun clearSession(context: Context) {
        try { repository(context).signOut() } catch (_: Exception) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }
}
