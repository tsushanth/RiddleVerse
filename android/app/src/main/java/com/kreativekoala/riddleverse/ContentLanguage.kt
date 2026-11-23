package com.kreativekoala.riddleverse

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.Locale

/**
 * The language the user sees the app in, as one of the 30 shipped languages (res/xml/locales_config.xml), sent to the
 * server so everything it generates (riddles, game text, idea chips, push notifications) matches the UI language.
 *
 * The codes are the ones the backend knows (web/backend/utils/contentLanguage.js): Android's `in` is `id`, `pt-BR` is
 * `pt` and `zh-CN` is `zh`. A language the app does not ship resolves to English, because the app shows English then.
 */
object ContentLanguage {
    const val DEFAULT = "en"
    const val HEADER = "x-app-language"

    private val supported = setOf(
        "en", "ar", "bn", "cs", "de", "el", "es", "fa", "fi", "fr", "hi", "hu", "id", "it", "ja", "ko", "ms", "nl",
        "pl", "pt", "ro", "ru", "sv", "th", "tl", "tr", "uk", "ur", "vi", "zh",
    )

    /** Server language code for a Java/Android locale tag language ("in" -> "id", "fil" -> "tl"); English when unsupported. */
    fun codeFor(language: String?): String {
        val l = (language ?: return DEFAULT).lowercase(Locale.ROOT)
        val mapped = when (l) {
            "in" -> "id"
            "fil" -> "tl"
            else -> l
        }
        return if (mapped in supported) mapped else DEFAULT
    }

    /** The current app language (the per-app locale on Android 13+, else the device locale). */
    fun current(): String = codeFor(Locale.getDefault().language)

    /** Adds Accept-Language and x-app-language to every request that does not set them itself. */
    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val code = current()
        val builder = request.newBuilder()
        if (request.header("Accept-Language") == null) builder.header("Accept-Language", code)
        if (request.header(HEADER) == null) builder.header(HEADER, code)
        chain.proceed(builder.build())
    }

    /** A plain client (default timeouts) that sends the language, for call sites that used a bare OkHttpClient(). */
    fun newClient(): OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
}
