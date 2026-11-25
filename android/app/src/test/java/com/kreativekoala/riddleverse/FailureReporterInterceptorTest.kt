package com.kreativekoala.riddleverse

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interceptor existed but no client used it, so backend errors seen by the app never reached the reporter. */
class FailureReporterInterceptorTest {

    private fun reportsFor(url: String, code: Int): List<Pair<String, String>> {
        val reports = mutableListOf<Pair<String, String>>()
        val answer = Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body("".toResponseBody(null)).build()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(FailureReporterInterceptor { flow, message -> reports.add(flow to message) })
            .addInterceptor(answer) // answers without any network
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return reports
    }

    @Test
    fun serverErrorFromOurBackendIsReportedWithoutIdsOrQuery() {
        val reports = reportsFor(
            "https://puzzleverseai.com/api/game-creation/0f8c1a52-1111-2222-3333-444455556666/difficulty/3?userId=someone%40example.com",
            503,
        )
        assertEquals(listOf("GET /api/game-creation/:id/difficulty/:id" to "HTTP 503 from puzzleverseai.com"), reports)
    }

    @Test
    fun clientErrorsSuccessAndOtherHostsAreNotReported() {
        assertTrue(reportsFor("https://puzzleverseai.com/leaderboard", 404).isEmpty())
        assertTrue(reportsFor("https://puzzleverseai.com/leaderboard", 200).isEmpty())
        assertTrue(reportsFor("https://example.org/leaderboard", 500).isEmpty())
    }

    @Test
    fun identifyingPathSegmentsAreReplaced() {
        assertEquals(
            "/api/leaderboard/puzzle/:id/user/:id",
            FailureReporterInterceptor.safePath(listOf("api", "leaderboard", "puzzle", "riddle-1762", "user", "a.b@example.com")),
        )
        assertEquals("/fetch-next-puzzle-ios/:id", FailureReporterInterceptor.safePath(listOf("fetch-next-puzzle-ios", "Math Puzzles")))
    }

    @Test
    fun everyBackendClientFactoryCarriesTheInterceptor() {
        val clients = listOf(HttpClientProvider.client, ContentLanguage.newClient(), FailureReporterInterceptor.newClient())
        clients.forEach { client -> assertTrue(client.interceptors.any { it is FailureReporterInterceptor }) }
    }
}
