package com.kreativekoala.riddleverse.auth

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SupabaseAuthTest {

    private class FakeHttp(val handler: (HttpRequest) -> HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse { requests += request; return handler(request) }
    }

    private class MemStore(var s: SupabaseSession? = null) : SessionStore {
        override fun load() = s
        override fun save(session: SupabaseSession) { s = session }
        override fun clear() { s = null }
    }

    private object ReverseCipher : SessionCipher {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(encoded: String): String? = if (encoded == "BAD") null else encoded.reversed()
    }

    private fun tokenJson(a: String = "acc", r: String = "ref", exp: Long = 5000) =
        """{"access_token":"$a","refresh_token":"$r","expires_at":$exp,"user":{"id":"u1","email":"a@b.co"}}"""

    private val session = SupabaseSession("acc", "ref", 5000, "u1", "a@b.co")

    // ---- request building
    @Test fun passwordGrantRequest() {
        val r = SupabaseAuthCore.passwordGrantRequest(" a@b.co ", "pw123456")
        assertEquals("POST", r.method)
        assertEquals("https://uujjodxicvifmiwlimob.supabase.co/auth/v1/token?grant_type=password", r.url)
        assertEquals(SupabaseConfig.PUBLISHABLE_KEY, r.headers["apikey"])
        val j = JSONObject(r.body!!)
        assertEquals("a@b.co", j.getString("email")); assertEquals("pw123456", j.getString("password"))
    }

    @Test fun signUpAndRefreshRequests() {
        assertTrue(SupabaseAuthCore.signUpRequest("a@b.co", "x").url.endsWith("/auth/v1/signup"))
        val r = SupabaseAuthCore.refreshRequest("rtok")
        assertTrue(r.url.endsWith("grant_type=refresh_token"))
        assertEquals("rtok", JSONObject(r.body!!).getString("refresh_token"))
    }

    @Test fun exchangeRequestUsesBearer() {
        val r = SupabaseAuthCore.exchangeRequest("tok")
        assertEquals("https://puzzleverseai.com/api/auth/supabase-exchange", r.url)
        assertEquals("Bearer tok", r.headers["Authorization"])
    }

    @Test fun noServiceKeyInConfig() {
        assertTrue(SupabaseConfig.PUBLISHABLE_KEY.startsWith("sb_publishable_"))
    }

    // ---- response parsing
    @Test fun parsesSessionWithExpiresAt() {
        val s = SupabaseAuthCore.parseSession(tokenJson(), 100)!!
        assertEquals(5000, s.expiresAtSec); assertEquals("u1", s.userId); assertEquals("a@b.co", s.email)
    }

    @Test fun parsesSessionWithExpiresIn() {
        val s = SupabaseAuthCore.parseSession("""{"access_token":"a","refresh_token":"r","expires_in":3600}""", 1000)!!
        assertEquals(4600, s.expiresAtSec); assertNull(s.userId)
    }

    @Test fun garbageSessionIsNull() {
        assertNull(SupabaseAuthCore.parseSession("not json", 0))
        assertNull(SupabaseAuthCore.parseSession("""{"user":{}}""", 0))
    }

    @Test fun mapsGoTrueErrors() {
        fun f(code: Int, body: String) = SupabaseAuthCore.supabaseError(HttpResponse(code, body)).failure
        assertEquals(AuthFailure.INVALID_CREDENTIALS, f(400, """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))
        assertEquals(AuthFailure.EMAIL_NOT_CONFIRMED, f(400, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}"""))
        assertEquals(AuthFailure.USER_EXISTS, f(422, """{"error_code":"user_already_exists"}"""))
        assertEquals(AuthFailure.WEAK_PASSWORD, f(422, """{"error_code":"weak_password"}"""))
        assertEquals(AuthFailure.RATE_LIMITED, f(429, """{"error_code":"over_request_rate_limit"}"""))
        assertEquals(AuthFailure.INVALID_TOKEN, f(400, """{"error":"invalid_grant"}"""))
        assertEquals(AuthFailure.SERVER, f(502, "<html>"))
        assertEquals(AuthFailure.UNKNOWN, f(418, "{}"))
    }

    @Test fun signUpWithoutSessionNeedsConfirmation() {
        val r = SupabaseAuthCore.parseSignUpResponse(HttpResponse(200, """{"id":"u","email":"a@b.co"}"""), 0)
        assertEquals(SignUpResult.ConfirmationRequired, r)
        val r2 = SupabaseAuthCore.parseSignUpResponse(HttpResponse(200, tokenJson()), 0)
        assertTrue(r2 is SignUpResult.SignedIn)
    }

    @Test fun parsesExchangeResponses() {
        val ok = SupabaseAuthCore.parseExchangeResponse(
            HttpResponse(200, """{"firebaseCustomToken":"ct","canonicalUid":"uid1","isNew":true}"""))
        assertEquals("ct", ok.firebaseCustomToken); assertEquals("uid1", ok.canonicalUid); assertTrue(ok.isNew)
        fun f(code: Int, body: String = "{}") = try {
            SupabaseAuthCore.parseExchangeResponse(HttpResponse(code, body)); null
        } catch (e: AuthException) { e.failure }
        assertEquals(AuthFailure.INVALID_TOKEN, f(401, """{"error":"invalid_token"}"""))
        assertEquals(AuthFailure.BRIDGE_UNAVAILABLE, f(503, """{"error":"auth_bridge_unavailable"}"""))
        assertEquals(AuthFailure.RATE_LIMITED, f(429))
        assertEquals(AuthFailure.UNKNOWN, f(200, "{}"))
    }

    @Test fun userMessagesAreReadable() {
        AuthFailure.values().forEach {
            val m = SupabaseAuthCore.userMessage(AuthException(it))
            assertTrue(m.isNotBlank()); assertFalse(m.contains("_"))
        }
    }

    @Test fun inputValidation() {
        assertNotNull(SupabaseAuthCore.validateInput("nope", "12345678"))
        assertNotNull(SupabaseAuthCore.validateInput("a@b.co", "short"))
        assertNull(SupabaseAuthCore.validateInput("a@b.co", "12345678"))
    }

    // ---- refresh decision
    @Test fun refreshDecision() {
        assertFalse(SupabaseAuthCore.shouldRefresh(session, 1000))
        assertTrue(SupabaseAuthCore.shouldRefresh(session, 4950))
        assertTrue(SupabaseAuthCore.shouldRefresh(session, 6000))
        assertTrue(SupabaseAuthCore.shouldRefresh(session, 4900, skewSec = 100))
    }

    // ---- storage encoding
    @Test fun sessionCodecRoundTrip() {
        assertEquals(session, SupabaseAuthCore.decodeSession(SupabaseAuthCore.encodeSession(session)))
        val nullable = SupabaseSession("a", "r", 1, null, null)
        assertEquals(nullable, SupabaseAuthCore.decodeSession(SupabaseAuthCore.encodeSession(nullable)))
        assertNull(SupabaseAuthCore.decodeSession("garbage"))
        assertNull(SupabaseAuthCore.decodeSession(null))
    }

    @Test fun encryptedStoreRoundTripAndCorruption() {
        var blob: String? = null
        val kv = object : EncryptedSessionStore.KeyValue {
            override fun get() = blob
            override fun put(value: String) { blob = value }
            override fun remove() { blob = null }
        }
        val store = EncryptedSessionStore(kv, ReverseCipher)
        store.save(session)
        assertFalse(blob!!.contains("acc\""))  // not plain JSON
        assertEquals(session, store.load())
        blob = "BAD"
        assertNull(store.load()); assertNull(blob)   // corrupt blob dropped
        store.save(session); store.clear(); assertNull(store.load())
    }

    // ---- repository
    @Test fun signInStoresSessionThenExchange() {
        val http = FakeHttp { req ->
            if (req.url.contains("grant_type=password")) HttpResponse(200, tokenJson())
            else HttpResponse(200, """{"firebaseCustomToken":"ct","canonicalUid":"c","isNew":false}""")
        }
        val store = MemStore()
        val repo = SupabaseAuthRepository(http, store, { 100 })
        val s = repo.signIn("a@b.co", "12345678")
        assertEquals(s, store.s)
        assertEquals("ct", repo.exchange(s).firebaseCustomToken)
        assertEquals("Bearer acc", http.requests.last().headers["Authorization"])
    }

    @Test fun networkErrorBecomesNetworkFailure() {
        val repo = SupabaseAuthRepository(FakeHttp { throw IOException("down") }, MemStore(), { 0 })
        val e = assertThrows(AuthException::class.java) { repo.signIn("a@b.co", "12345678") }
        assertEquals(AuthFailure.NETWORK, e.failure)
    }

    @Test fun invalidCredentialsNotStored() {
        val store = MemStore()
        val repo = SupabaseAuthRepository(
            FakeHttp { HttpResponse(400, """{"error_code":"invalid_credentials"}""") }, store, { 0 })
        assertThrows(AuthException::class.java) { repo.signIn("a@b.co", "12345678") }
        assertNull(store.s)
    }

    @Test fun validSessionRefreshesNearExpiry() {
        val store = MemStore(session)
        val http = FakeHttp { HttpResponse(200, tokenJson("new", "ref2", 99999)) }
        val repo = SupabaseAuthRepository(http, store, { 4990 })
        assertEquals("new", repo.validSession()!!.accessToken)
        assertEquals("ref2", store.s!!.refreshToken)
    }

    @Test fun validSessionNoRefreshWhenFresh() {
        val http = FakeHttp { fail("no http expected"); HttpResponse(500, "") }
        assertEquals(session, SupabaseAuthRepository(http, MemStore(session), { 10 }).validSession())
    }

    @Test fun rejectedRefreshClearsSessionButNetworkErrorKeepsIt() {
        val s1 = MemStore(session)
        SupabaseAuthRepository(FakeHttp { HttpResponse(400, """{"error":"invalid_grant"}""") }, s1, { 6000 })
            .let { assertNull(it.silentExchange()) }
        assertNull(s1.s)
        val s2 = MemStore(session)
        val repo = SupabaseAuthRepository(FakeHttp { throw IOException() }, s2, { 6000 })
        assertThrows(AuthException::class.java) { repo.silentExchange() }
        assertNotNull(s2.s)
    }

    @Test fun silentExchangeRetriesOnceAfter401() {
        var exchanges = 0
        val http = FakeHttp { req ->
            when {
                req.url.contains("refresh_token") -> HttpResponse(200, tokenJson("fresh", "r2", 99999))
                else -> { exchanges++; if (exchanges == 1) HttpResponse(401, """{"error":"invalid_token"}""")
                    else HttpResponse(200, """{"firebaseCustomToken":"ct","isNew":false}""") }
            }
        }
        val repo = SupabaseAuthRepository(http, MemStore(session), { 10 })
        assertEquals("ct", repo.silentExchange()!!.firebaseCustomToken)
        assertEquals("Bearer fresh", http.requests.last().headers["Authorization"])
    }

    @Test fun silentExchangeNullWithoutSession() {
        assertNull(SupabaseAuthRepository(FakeHttp { fail(); HttpResponse(0, "") }, MemStore(), { 0 }).silentExchange())
    }

    @Test fun bridgeUnavailableKeepsSession() {
        val store = MemStore(session)
        val repo = SupabaseAuthRepository(FakeHttp { HttpResponse(503, "{}") }, store, { 10 })
        val e = assertThrows(AuthException::class.java) { repo.silentExchange() }
        assertEquals(AuthFailure.BRIDGE_UNAVAILABLE, e.failure); assertNotNull(store.s)
    }

    @Test fun signOutClears() {
        val store = MemStore(session)
        SupabaseAuthRepository(FakeHttp { HttpResponse(200, "") }, store).signOut()
        assertNull(store.s)
    }
}
