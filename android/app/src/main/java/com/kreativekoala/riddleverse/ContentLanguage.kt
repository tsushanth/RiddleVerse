package com.kreativekoala.riddleverse

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.Locale

/**
 * The language the user sees the app in, as one of the 32 shipped languages (res/xml/locales_config.xml), sent to the
 * server so everything it generates (riddles, game text, idea chips, push notifications) matches the UI language.
 *
 * The codes are the ones the backend knows (web/backend/utils/contentLanguage.js): Android's `in` is `id`, `pt-BR` is
 * `pt` and `zh-CN` is `zh` (Simplified, unchanged for old servers);
 * Traditional Chinese is sent as `zh-TW` or `zh-HK` so the server can name it in LLM prompts. A language the app does not ship resolves to English, because the app shows English then.
 */
object ContentLanguage {
    const val DEFAULT = "en"
    const val HEADER = "x-app-language"

    private val supported = setOf(
        "en", "ar", "bn", "cs", "de", "el", "es", "fa", "fi", "fr", "hi", "hu", "id", "it", "ja", "ko", "ms", "nl",
        "pl", "pt", "ro", "ru", "sv", "th", "tl", "tr", "uk", "ur", "vi", "zh", "zh-TW", "zh-HK",
    )

    /** Server language code for a Java/Android locale ("in" -> "id", "fil" -> "tl", zh-Hant/TW -> "zh-TW"); English when unsupported. */
    fun codeFor(language: String?, script: String? = null, country: String? = null): String {
        val l = (language ?: return DEFAULT).lowercase(Locale.ROOT)
        if (l == "zh") {
            // Simplified stays "zh"; Hant / TW / MO -> zh-TW, HK -> zh-HK (same rules as the in-app language list).
            return when (val tag = AppLanguages.supportedTagFor("zh", script.orEmpty(), country.orEmpty())) {
                "zh-TW", "zh-HK" -> tag
                else -> "zh"
            }
        }
        val mapped = when (l) {
            "in" -> "id"
            "fil" -> "tl"
            else -> l
        }
        return if (mapped in supported) mapped else DEFAULT
    }

    /** The current app language (the per-app locale on Android 13+, else the device locale). */
    fun current(): String = Locale.getDefault().let { codeFor(it.language, it.script, it.country) }

    /** Our own API host (and its subdomains): the only place the language headers are sent. */
    internal fun isOwnHost(host: String): Boolean = host == "puzzleverseai.com" || host.endsWith(".puzzleverseai.com")

    /**
     * Adds Accept-Language and x-app-language to requests to our own backend that do not set them itself. Third-party
     * requests (media, signed URLs, analytics) are passed through untouched.
     */
    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (!isOwnHost(request.url.host)) return@Interceptor chain.proceed(request)
        val code = current()
        val builder = request.newBuilder()
        if (request.header("Accept-Language") == null) builder.header("Accept-Language", code)
        if (request.header(HEADER) == null) builder.header(HEADER, code)
        chain.proceed(builder.build())
    }

    /** A plain client (default timeouts) that sends the language, for call sites that used a bare OkHttpClient(). */
    fun newClient(): OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor(FailureReporterInterceptor()).build()
}
