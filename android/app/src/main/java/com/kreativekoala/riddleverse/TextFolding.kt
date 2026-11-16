package com.kreativekoala.riddleverse

import java.text.Normalizer
import java.util.Locale

/**
 * Script-aware text folding shared by the answer matchers, so a typed answer in any script is compared as letters
 * instead of being deleted. Output is lower case, NFC, with:
 *  - Latin / Greek / Arabic / Hebrew combining marks removed (accents, tashkeel, niqqud), so "café" ~ "cafe", "مُدَرِّس" ~ "مدرس";
 *  - Cyrillic stress and diaeresis marks removed (ё ~ е) but the breve kept (й stays distinct from и);
 *  - Indic, Thai, Hangul and kana marks KEPT (they are part of the letter: removing a Devanagari vowel sign changes the word);
 *  - letters that do not decompose folded to ASCII (ß ø đ ł ı æ œ), Arabic alef, ya, kaf and ta marbuta variants unified;
 *  - every decimal digit system (Arabic-Indic, Persian, Devanagari, Bengali, fullwidth) mapped to 0-9.
 * Everything else (punctuation, spaces) is returned unchanged for the caller to treat.
 * The matchers lowercase with Locale.ROOT; this never uses the device locale (no Turkish-i problem: İ -> i, ı -> i).
 */
object TextFolding {
    private val latinSpecials = mapOf(
        'ß' to "ss", 'ø' to "o", 'đ' to "d", 'ð' to "d", 'ł' to "l", 'ı' to "i", 'æ' to "ae", 'œ' to "oe", 'þ' to "th", 'ħ' to "h",
    )

    private fun foldsMarksOf(script: Character.UnicodeScript?): Boolean = when (script) {
        Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.ARABIC, Character.UnicodeScript.HEBREW -> true
        else -> false
    }

    private fun foldArabic(cp: Int): Int = when (cp) {
        0x0622, 0x0623, 0x0625, 0x0671 -> 0x0627 // alef with madda / hamza / wasla -> alef
        0x0649, 0x06CC -> 0x064A // alef maqsura, Persian ya -> ya
        0x06A9 -> 0x0643 // Persian kaf -> kaf
        0x0629 -> 0x0647 // ta marbuta -> ha
        else -> cp
    }

    fun fold(input: String): String {
        val nfkd = Normalizer.normalize(input, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        val sb = StringBuilder(nfkd.length)
        var lastScript: Character.UnicodeScript? = null
        var i = 0
        while (i < nfkd.length) {
            val cp = nfkd.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp)
            if (type == Character.NON_SPACING_MARK.toInt()) {
                val strip = when {
                    foldsMarksOf(lastScript) -> true
                    lastScript == Character.UnicodeScript.CYRILLIC -> cp != 0x0306 // keep breve (й), drop diaeresis/acute
                    else -> false
                }
                if (!strip) sb.appendCodePoint(cp)
                continue
            }
            if (cp == 0x0640) continue // Arabic tatweel
            val script = try { Character.UnicodeScript.of(cp) } catch (_: IllegalArgumentException) { null }
            if (script != null && script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) lastScript = script
            if (cp < 0x80 || cp > 0xFFFF) { sb.appendCodePoint(cp); continue }
            val ch = cp.toChar()
            val special = latinSpecials[ch]
            when {
                special != null -> sb.append(special)
                type == Character.DECIMAL_DIGIT_NUMBER.toInt() -> sb.append(Character.digit(cp, 10).let { if (it in 0..9) ('0' + it) else ch })
                script == Character.UnicodeScript.ARABIC -> sb.appendCodePoint(foldArabic(cp))
                else -> sb.append(ch)
            }
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
    }

    /** True for a code point that is part of a word in any script: letter, mark that stayed attached, or digit. */
    fun isWordCodePoint(cp: Int): Boolean {
        if (Character.isLetterOrDigit(cp)) return true
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt()
    }
}
