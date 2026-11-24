package com.kreativekoala.riddleverse

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Server codes -> localized strings, and the app language the client sends (GameServerText, ContentLanguage). */
class GameServerTextTest {
    private val resDir: File = listOf("src/main/res", "android/app/src/main/res").map { File(it) }.first { it.isDirectory }
    private val savedLocale = Locale.getDefault()

    @After fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test fun statusMessageUsesTheCodeThenThePhaseThenTheServerText() {
        assertEquals(UiText.Res(R.string.gen_status_no_issues), GameServerText.statusMessage("no_issues", "fix", "No issues found"))
        // Unknown code, known phase: the phase string, not the English text.
        assertEquals(UiText.Res(R.string.gen_phase_polish), GameServerText.statusMessage("brand_new_code", "polish", "Polishing"))
        assertEquals(UiText.Res(R.string.gen_phase_generate), GameServerText.statusMessage(null, "generate", "Building your game"))
        // Unknown phase (a server newer than the app): the server text is the only thing left.
        assertEquals(UiText.Raw("Something new"), GameServerText.statusMessage(null, "teleport", "Something new"))
        assertEquals(UiText.Raw("Something new"), GameServerText.statusMessage(null, null, "Something new"))
        assertNull(GameServerText.statusMessage(null, null, ""))
    }

    @Test fun statusDetailIsLocalizedOrDroppedNeverShownInEnglishForAKnownPhase() {
        assertEquals(UiText.Res(R.string.gen_detail_found_issues, listOf(3)), GameServerText.statusDetail("found_issues", listOf(3), "fix", "Found 3 issues to resolve"))
        assertEquals(UiText.Res(R.string.gen_detail_passed_checks), GameServerText.statusDetail("passed_checks", emptyList(), "fix", "Game passed all quality checks"))
        // Known phase, no detail code (the English text embeds a game title): nothing, not English.
        assertNull(GameServerText.statusDetail(null, emptyList(), "generate", "Creating level 3 of Neon Snake"))
        // Unknown phase: server text.
        assertEquals(UiText.Raw("Teleporting"), GameServerText.statusDetail(null, emptyList(), "teleport", "Teleporting"))
        assertNull(GameServerText.statusDetail(null, emptyList(), "teleport", ""))
    }

    @Test fun detailArgsAreReadFromTheEvent() {
        assertEquals(listOf(4), GameServerText.detailArgs(JSONObject("""{"detailArgs":[4]}""")))
        assertEquals(emptyList<Int>(), GameServerText.detailArgs(JSONObject("{}")))
    }

    @Test fun knownErrorCodesAreLocalizedUnknownFallBackToTheServerText() {
        assertEquals(UiText.Res(R.string.gen_err_description_too_long), GameServerText.errorText("description_too_long"))
        assertEquals(UiText.Res(R.string.gen_err_connection_lost), GameServerText.errorText("build_connection_lost"))
        assertEquals(UiText.Res(R.string.onb_gate_sign_in_to_create), GameServerText.errorText("auth_required"))
        assertNull(GameServerText.errorText("a_code_from_the_future"))
        assertNull(GameServerText.errorText(null))
    }

    @Test fun everyMappedResourceExistsInEnglishAndEveryCodeMapsSomewhere() {
        assertTrue(GameServerText.knownErrorCodes.size >= 30)
        assertTrue(GameServerText.knownMessageCodes.containsAll(listOf("build", "no_issues", "package")))
        assertTrue(GameServerText.knownDetailCodes.containsAll(listOf("found_issues", "creating_bundle")))
    }

    @Test fun serverLanguageCodesFollowTheBackend() {
        assertEquals("id", ContentLanguage.codeFor("in"))
        assertEquals("id", ContentLanguage.codeFor("id"))
        assertEquals("tl", ContentLanguage.codeFor("fil"))
        assertEquals("pt", ContentLanguage.codeFor("pt"))
        assertEquals("zh", ContentLanguage.codeFor("zh"))
        assertEquals("zh", ContentLanguage.codeFor("zh", "Hans", "CN"))
        assertEquals("zh", ContentLanguage.codeFor("zh", null, "SG"))
        assertEquals("zh-TW", ContentLanguage.codeFor("zh", null, "TW"))
        assertEquals("zh-TW", ContentLanguage.codeFor("zh", "Hant", ""))
        assertEquals("zh-TW", ContentLanguage.codeFor("zh", "Hant", "MO"))
        assertEquals("zh-HK", ContentLanguage.codeFor("ZH", "", "hk"))
        assertEquals("zh-HK", ContentLanguage.codeFor("zh", "Hant", "HK"))
        assertEquals("de", ContentLanguage.codeFor("DE"))
        // Not a shipped language: the app shows English, so the server text is English too.
        assertEquals("en", ContentLanguage.codeFor("he"))
        assertEquals("en", ContentLanguage.codeFor(null))
    }

    @Test fun everyShippedLanguageMapsToItself() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "xml/locales_config.xml"))
        val nodes = doc.getElementsByTagName("locale")
        val codes = (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttribute("android:name") }
        assertEquals(32, codes.size)
        for (tag in codes) {
            val locale = Locale.forLanguageTag(tag)
            val language = locale.language
            // Java reports Indonesian as "in" on older runtimes; the server code is "id". Simplified Chinese is "zh",
            // Traditional keeps its region so the server can tell Taiwan from Hong Kong.
            val expected = when (tag) { "zh-CN" -> "zh"; "zh-TW", "zh-HK" -> tag; else -> if (language == "in") "id" else language }
            assertEquals(tag, expected, ContentLanguage.codeFor(language, locale.script, locale.country))
        }
    }

    @Test fun theInterceptorKeepsTraditionalChineseDistinguishable() {
        val seen = ArrayList<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(ContentLanguage.interceptor)
            .addInterceptor { chain ->
                seen += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                    .body("".toResponseBody(null)).build()
            }
            .build()
        val expected = mapOf("zh-TW" to "zh-TW", "zh-HK" to "zh-HK", "zh-Hant-TW" to "zh-TW", "zh-Hant-HK" to "zh-HK", "zh-CN" to "zh", "zh-Hans-CN" to "zh", "zh-SG" to "zh")
        for ((tag, code) in expected) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            client.newCall(Request.Builder().url("https://puzzleverseai.com/x").build()).execute().close()
            assertEquals(tag, code, seen.last().header(ContentLanguage.HEADER))
            assertEquals(tag, code, seen.last().header("Accept-Language"))
        }
    }

    @Test fun theInterceptorSendsTheAppLanguageUnlessTheCallerSetsIt() {
        val seen = ArrayList<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(ContentLanguage.interceptor)
            .addInterceptor { chain ->
                seen += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                    .body("".toResponseBody(null)).build()
            }
            .build()
        Locale.setDefault(Locale.forLanguageTag("de-AT"))
        client.newCall(Request.Builder().url("https://puzzleverseai.com/x").build()).execute().close()
        assertEquals("de", seen[0].header("Accept-Language"))
        assertEquals("de", seen[0].header(ContentLanguage.HEADER))

        Locale.setDefault(Locale("in", "ID"))
        client.newCall(Request.Builder().url("https://puzzleverseai.com/x").build()).execute().close()
        assertEquals("id", seen[1].header(ContentLanguage.HEADER))

        client.newCall(Request.Builder().url("https://puzzleverseai.com/x").header("Accept-Language", "fr").build()).execute().close()
        assertEquals("fr", seen[2].header("Accept-Language"))
        assertNotNull(seen[2].header(ContentLanguage.HEADER))

        // Third-party hosts (and look-alikes) get no language headers at all.
        for (other in listOf("https://picsum.photos/x", "https://evilpuzzleverseai.com/x", "https://puzzleverseai.com.evil.test/x")) {
            client.newCall(Request.Builder().url(other).build()).execute().close()
            val sent = seen.last()
            assertEquals(other, null, sent.header(ContentLanguage.HEADER))
            assertEquals(other, null, sent.header("Accept-Language"))
        }
    }
}
