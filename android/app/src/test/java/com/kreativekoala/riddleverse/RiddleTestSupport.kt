package com.kreativekoala.riddleverse

import java.io.File

internal object RiddleTestSupport {
    private val file: File by lazy {
        listOf("src/main/assets/riddles", "app/src/main/assets/riddles", "android/app/src/main/assets/riddles")
            .map { File(it, "riddles_v1.json") }.first { it.isFile }
    }
    val raw: String by lazy { file.readText(Charsets.UTF_8) }
    val bank: List<Riddle> by lazy { RiddleBank.parse(raw) }
    fun byId(id: String) = bank.first { it.id == id }
}
