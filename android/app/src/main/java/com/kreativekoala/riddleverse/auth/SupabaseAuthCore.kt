package com.kreativekoala.riddleverse.auth

import androidx.annotation.StringRes
import com.kreativekoala.riddleverse.R
import org.json.JSONObject

/**
 * Pure (JVM-testable) building blocks for Supabase email/password auth and the
 * Supabase -> Firebase custom-token exchange. No Android types in here.
 *
 * The publishable key is public by design (it ships in every client). Never put a
 * service-role key anywhere in this app.
 */
object SupabaseConfig {
    const val URL = "https://uujjodxicvifmiwlimob.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_Y6ShRo7h0y-chbflQ5to_A_b0mDisxY"
    const val EXCHANGE_URL = "https://puzzleverseai.com/api/auth/supabase-exchange"
}

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?
)

data class HttpResponse(val code: Int, val body: String)

/** Abstraction over the HTTP client so the logic is testable. Throws IOException on network failure. */
interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds at which accessToken expires. */
    val expiresAtSec: Long,
    val userId: String?,
    val email: String?
)

data class ExchangeResult(val firebaseCustomToken: String, val canonicalUid: String?, val isNew: Boolean)

enum class AuthFailure {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    USER_EXISTS,
    WEAK_PASSWORD,
    RATE_LIMITED,
    INVALID_TOKEN,
    BRIDGE_UNAVAILABLE,
    NETWORK,
    SERVER,
    UNKNOWN
}

class AuthException(val failure: AuthFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)

sealed class SignUpResult {
    data class SignedIn(val session: SupabaseSession) : SignUpResult()
    /** Account created but the user must confirm their email before signing in. */
    object ConfirmationRequired : SignUpResult()
}

object SupabaseAuthCore {

    private fun supabaseHeaders() = mapOf(
        "apikey" to SupabaseConfig.PUBLISHABLE_KEY,
        "Content-Type" to "application/json"
    )

    private fun credentialsBody(email: String, password: String) =
        JSONObject().put("email", email.trim()).put("password", password).toString()

    fun passwordGrantRequest(email: String, password: String) = HttpRequest(
        "POST", "${SupabaseConfig.URL}/auth/v1/token?grant_type=password",
        supabaseHeaders(), credentialsBody(email, password)
    )

    fun signUpRequest(email: String, password: String) = HttpRequest(
        "POST", "${SupabaseConfig.URL}/auth/v1/signup",
        supabaseHeaders(), credentialsBody(email, password)
    )

    fun refreshRequest(refreshToken: String) = HttpRequest(
        "POST", "${SupabaseConfig.URL}/auth/v1/token?grant_type=refresh_token",
        supabaseHeaders(), JSONObject().put("refresh_token", refreshToken).toString()
    )

    fun exchangeRequest(accessToken: String, url: String = SupabaseConfig.EXCHANGE_URL) = HttpRequest(
        "POST", url,
        mapOf("Authorization" to "Bearer $accessToken", "Content-Type" to "application/json"),
        "{}"
    )

    /** True when the access token is expired or within [skewSec] of expiring. */
    fun shouldRefresh(session: SupabaseSession, nowSec: Long, skewSec: Long = 60): Boolean =
        session.expiresAtSec - nowSec <= skewSec

    private fun parseJson(body: String): JSONObject? =
        try { JSONObject(body) } catch (_: Exception) { null }

    private fun errorCodeOf(json: JSONObject?): String =
        (json?.optString("error_code").orEmpty().ifEmpty { json?.optString("error").orEmpty() })
            .lowercase()

    private fun errorMsgOf(json: JSONObject?): String =
        json?.optString("msg").orEmpty().ifEmpty {
            json?.optString("error_description").orEmpty().ifEmpty { json?.optString("message").orEmpty() }
        }

    /** Maps a non-2xx GoTrue response to a typed failure. */
    fun supabaseError(resp: HttpResponse): AuthException {
        val json = parseJson(resp.body)
        val code = errorCodeOf(json)
        val msg = errorMsgOf(json)
        val lowerMsg = msg.lowercase()
        val failure = when {
            code == "email_not_confirmed" || lowerMsg.contains("email not confirmed") -> AuthFailure.EMAIL_NOT_CONFIRMED
            code == "invalid_credentials" || lowerMsg.contains("invalid login credentials") -> AuthFailure.INVALID_CREDENTIALS
            code == "user_already_exists" || code == "email_exists" || lowerMsg.contains("already registered") -> AuthFailure.USER_EXISTS
            code == "weak_password" || lowerMsg.contains("password should be") -> AuthFailure.WEAK_PASSWORD
            resp.code == 429 || code.contains("rate_limit") -> AuthFailure.RATE_LIMITED
            code == "invalid_grant" || code == "refresh_token_not_found" || code == "refresh_token_already_used" ||
                code == "session_not_found" -> AuthFailure.INVALID_TOKEN
            resp.code in 500..599 -> AuthFailure.SERVER
            else -> AuthFailure.UNKNOWN
        }
        return AuthException(failure, msg.ifEmpty { null })
    }

    /** Parses a token response ({access_token, refresh_token, expires_in|expires_at, user}). */
    fun parseSession(body: String, nowSec: Long): SupabaseSession? {
        val json = parseJson(body) ?: return null
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isEmpty() || refresh.isEmpty()) return null
        val expiresAt = when {
            json.optLong("expires_at", 0L) > 0L -> json.optLong("expires_at")
            else -> nowSec + json.optLong("expires_in", 3600L)
        }
        val user = json.optJSONObject("user")
        return SupabaseSession(
            access, refresh, expiresAt,
            user?.optString("id")?.ifEmpty { null },
            user?.optString("email")?.ifEmpty { null }
        )
    }

    fun parseSessionResponse(resp: HttpResponse, nowSec: Long): SupabaseSession {
        if (resp.code !in 200..299) throw supabaseError(resp)
        return parseSession(resp.body, nowSec)
            ?: throw AuthException(AuthFailure.UNKNOWN, "Unexpected response from sign-in service")
    }

    /** Signup returns a session when autoconfirm is on, else a bare user object. */
    fun parseSignUpResponse(resp: HttpResponse, nowSec: Long): SignUpResult {
        if (resp.code !in 200..299) throw supabaseError(resp)
        val session = parseSession(resp.body, nowSec)
        return if (session != null) SignUpResult.SignedIn(session) else SignUpResult.ConfirmationRequired
    }

    fun parseExchangeResponse(resp: HttpResponse): ExchangeResult {
        when {
            resp.code in 200..299 -> {
                val json = parseJson(resp.body)
                val token = json?.optString("firebaseCustomToken").orEmpty()
                if (token.isEmpty()) throw AuthException(AuthFailure.UNKNOWN, "Bad exchange response")
                return ExchangeResult(
                    token,
                    json?.optString("canonicalUid")?.ifEmpty { null },
                    json?.optBoolean("isNew", false) ?: false
                )
            }
            resp.code == 401 -> throw AuthException(AuthFailure.INVALID_TOKEN)
            resp.code == 429 -> throw AuthException(AuthFailure.RATE_LIMITED)
            resp.code == 503 -> throw AuthException(AuthFailure.BRIDGE_UNAVAILABLE)
            resp.code in 500..599 -> throw AuthException(AuthFailure.SERVER)
            else -> throw AuthException(AuthFailure.UNKNOWN, "Exchange failed (${resp.code})")
        }
    }

    /** Session <-> JSON for storage. Contains tokens: only ever store encrypted, never log. */
    fun encodeSession(s: SupabaseSession): String = JSONObject()
        .put("a", s.accessToken).put("r", s.refreshToken).put("e", s.expiresAtSec)
        .put("u", s.userId ?: JSONObject.NULL).put("m", s.email ?: JSONObject.NULL)
        .toString()

    fun decodeSession(text: String?): SupabaseSession? {
        if (text.isNullOrEmpty()) return null
        val j = parseJson(text) ?: return null
        val a = j.optString("a"); val r = j.optString("r")
        if (a.isEmpty() || r.isEmpty()) return null
        return SupabaseSession(
            a, r, j.optLong("e", 0L),
            if (j.isNull("u")) null else j.optString("u").ifEmpty { null },
            if (j.isNull("m")) null else j.optString("m").ifEmpty { null }
        )
    }

    /** Readable, non-leaky message for the UI, as a string resource id (resolve with the UI context). */
    @StringRes
    fun userMessageRes(e: AuthException): Int = when (e.failure) {
        AuthFailure.INVALID_CREDENTIALS -> R.string.onb_auth_err_invalid_credentials
        AuthFailure.EMAIL_NOT_CONFIRMED -> R.string.onb_auth_err_email_not_confirmed
        AuthFailure.USER_EXISTS -> R.string.onb_auth_err_user_exists
        AuthFailure.WEAK_PASSWORD -> R.string.onb_auth_err_weak_password
        AuthFailure.RATE_LIMITED -> R.string.onb_auth_err_rate_limited
        AuthFailure.INVALID_TOKEN -> R.string.onb_auth_err_invalid_token
        AuthFailure.BRIDGE_UNAVAILABLE -> R.string.onb_auth_err_bridge_unavailable
        AuthFailure.NETWORK -> R.string.onb_auth_err_network
        AuthFailure.SERVER -> R.string.onb_auth_err_server
        AuthFailure.UNKNOWN -> R.string.onb_auth_err_unknown
    }

    /** Returns a string resource id describing the first input problem, or null when the input is valid. */
    @StringRes
    fun validateInputRes(email: String, password: String): Int? = when {
        !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim()) -> R.string.onb_auth_err_invalid_email
        password.length < 8 -> R.string.onb_auth_err_password_short
        else -> null
    }
}
