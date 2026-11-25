package com.kreativekoala.riddleverse

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reports 5xx responses from our own backend. Deliberately ignores 4xx (auth/validation noise) and
 * IOExceptions (a user with no signal is not an app failure). Path only, never the query, and path segments that can
 * identify a person or a record are replaced so reports group by endpoint.
 *
 * Added to every client that calls the backend: the shared client, [ContentLanguage.newClient] and the per-screen ones.
 */
class FailureReporterInterceptor(
    private val report: (flow: String, message: String) -> Unit = { flow, message -> FailureReporter.backendError(flow, message) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = chain.request().url
        if (response.code >= 500 && ContentLanguage.isOwnHost(url.host)) {
            report("${chain.request().method} ${safePath(url.pathSegments)}", "HTTP ${response.code} from ${url.host}")
        }
        return response
    }

    companion object {
        private val ENDPOINT_WORD = Regex("^[a-z]+(?:[-_][a-z]+)*$")

        /** Keeps lower-case words ("api", "game-creation", "difficulty"); anything else (ids, emails, numbers) becomes ":id". */
        fun safePath(segments: List<String>): String =
            "/" + segments.filter { it.isNotEmpty() }.joinToString("/") { if (it.length <= 40 && ENDPOINT_WORD.matches(it)) it else ":id" }

        /** A plain client (default timeouts) that reports server errors, for call sites that used a bare OkHttpClient(). */
        fun newClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder().addInterceptor(FailureReporterInterceptor()).build()
    }
}
