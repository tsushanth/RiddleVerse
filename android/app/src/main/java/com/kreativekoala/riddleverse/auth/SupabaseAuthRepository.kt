package com.kreativekoala.riddleverse.auth

import java.io.IOException

interface SessionStore {
    fun load(): SupabaseSession?
    fun save(session: SupabaseSession)
    fun clear()
}

/** Reversible encryption for the stored session. Implementations must not log. */
interface SessionCipher {
    fun encrypt(plain: String): String
    /** Returns null if the data can't be decrypted (e.g. key lost). */
    fun decrypt(encoded: String): String?
}

/** Stores the encoded+encrypted session in a key/value backend (SharedPreferences in the app). */
class EncryptedSessionStore(
    private val kv: KeyValue,
    private val cipher: SessionCipher
) : SessionStore {
    interface KeyValue {
        fun get(): String?
        fun put(value: String)
        fun remove()
    }

    override fun load(): SupabaseSession? {
        val enc = kv.get() ?: return null
        val plain = try { cipher.decrypt(enc) } catch (_: Exception) { null }
        val s = SupabaseAuthCore.decodeSession(plain)
        if (s == null) kv.remove()
        return s
    }

    override fun save(session: SupabaseSession) {
        kv.put(cipher.encrypt(SupabaseAuthCore.encodeSession(session)))
    }

    override fun clear() = kv.remove()
}

/**
 * Blocking API (call from a background dispatcher). Keeps Supabase session state and
 * performs the Supabase -> Firebase custom token exchange.
 */
class SupabaseAuthRepository(
    private val http: HttpTransport,
    private val store: SessionStore,
    private val clockSec: () -> Long = { System.currentTimeMillis() / 1000 },
    private val exchangeUrl: String = SupabaseConfig.EXCHANGE_URL
) {
    private fun send(req: HttpRequest): HttpResponse =
        try { http.execute(req) } catch (e: IOException) {
            throw AuthException(AuthFailure.NETWORK, cause = e)
        }

    fun hasStoredSession(): Boolean = store.load() != null

    fun signIn(email: String, password: String): SupabaseSession {
        val s = SupabaseAuthCore.parseSessionResponse(
            send(SupabaseAuthCore.passwordGrantRequest(email, password)), clockSec()
        )
        store.save(s)
        return s
    }

    fun signUp(email: String, password: String): SignUpResult {
        val r = SupabaseAuthCore.parseSignUpResponse(
            send(SupabaseAuthCore.signUpRequest(email, password)), clockSec()
        )
        if (r is SignUpResult.SignedIn) store.save(r.session)
        return r
    }

    fun exchange(session: SupabaseSession): ExchangeResult =
        SupabaseAuthCore.parseExchangeResponse(send(SupabaseAuthCore.exchangeRequest(session.accessToken, exchangeUrl)))

    /** Refreshes the stored session now. A rejected refresh token clears the session. */
    fun refresh(current: SupabaseSession): SupabaseSession {
        try {
            val s = SupabaseAuthCore.parseSessionResponse(
                send(SupabaseAuthCore.refreshRequest(current.refreshToken)), clockSec()
            )
            store.save(s)
            return s
        } catch (e: AuthException) {
            if (e.failure == AuthFailure.INVALID_TOKEN || e.failure == AuthFailure.INVALID_CREDENTIALS) store.clear()
            throw e
        }
    }

    /** Stored session, refreshed if near expiry. Null if none. Network errors propagate (session kept). */
    fun validSession(): SupabaseSession? {
        val s = store.load() ?: return null
        return if (SupabaseAuthCore.shouldRefresh(s, clockSec())) refresh(s) else s
    }

    /**
     * Silent re-login: stored Supabase session -> exchange. On a 401 retries once after a forced
     * refresh. Returns null when there is no stored session (or it was rejected and cleared).
     */
    fun silentExchange(): ExchangeResult? {
        val s = try { validSession() } catch (e: AuthException) {
            if (e.failure == AuthFailure.INVALID_TOKEN) return null else throw e
        } ?: return null
        return try {
            exchange(s)
        } catch (e: AuthException) {
            if (e.failure != AuthFailure.INVALID_TOKEN) throw e
            val fresh = try { refresh(s) } catch (r: AuthException) {
                if (r.failure == AuthFailure.INVALID_TOKEN) return null else throw r
            }
            try { exchange(fresh) } catch (e2: AuthException) {
                if (e2.failure == AuthFailure.INVALID_TOKEN) { store.clear(); null } else throw e2
            }
        }
    }

    fun signOut() = store.clear()
}
