package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

// --- daily puzzle (feat/daily-puzzle-share) --- the shareable image card and the Android share intents.
// The card is drawn only from DailyShareResult (number, game name, time, tries, streak): the puzzle, its seed and
// its solution are never available here, so the card cannot leak the answer.

object DailyShareCard {
    private const val SIZE = 1080

    // Brand colours (ui/theme/Color.kt)
    private val VIOLET = Color.parseColor("#6C4CF1")
    private val NIGHT = Color.parseColor("#1E1B3A")
    private val SUN = Color.parseColor("#FFB020")
    private val MINT = Color.parseColor("#2DC98E")
    private val CORAL = Color.parseColor("#FF6B6B")
    private val FLAME = Color.parseColor("#FF8C00")

    /** Draws the 1080x1080 card. Call off the main thread if you can; it is cheap but allocates ~4.6 MB. */
    fun render(r: DailyShareResult): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = SIZE.toFloat()

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w, w, VIOLET, NIGHT, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, w, bg)

        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) }
        c.drawRoundRect(RectF(72f, 72f, w - 72f, w - 72f), 64f, 64f, panel)

        fun text(s: String, size: Float, color: Int, y: Float, bold: Boolean = true) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            c.drawText(s, w / 2f, y, p)
        }

        text("RiddleVerse", 56f, SUN, 190f)
        text("Daily #${r.number}", 112f, Color.WHITE, 320f)
        text("SOLVED", 64f, MINT, 410f)
        text(DailyShareText.formatTime(r.seconds), 220f, Color.WHITE, 640f)
        text(r.gameName, 52f, Color.argb(220, 255, 255, 255), 725f, bold = false)

        // Tries: one rounded square per try, wrong tries coral, the solving try mint.
        val n = r.attempts.coerceIn(1, 6)
        val box = 84f
        val gap = 20f
        val rowW = n * box + (n - 1) * gap
        var x = (w - rowW) / 2f
        val y = 775f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until n) {
            fill.color = if (i == n - 1) MINT else CORAL
            c.drawRoundRect(RectF(x, y, x + box, y + box), 18f, 18f, fill)
            x += box + gap
        }

        if (r.streak > 0) text("🔥 ${r.streak} day streak", 64f, FLAME, 940f)
        text("Play today's puzzle on Google Play", 40f, Color.argb(200, 255, 255, 255), 1005f, bold = false)
        return bmp
    }

    /** Writes the card to cache/shared_images (already exposed by the app's FileProvider) and returns the file. */
    fun save(context: Context, r: DailyShareResult): File {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "riddleverse_daily_${r.number}.png")
        val bmp = render(r)
        try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }
        return file
    }

    /** Share sheet with the text only. */
    fun shareText(context: Context, r: DailyShareResult) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, DailyShareText.build(r))
        }
        context.startActivity(Intent.createChooser(send, "Share your daily result"))
    }

    /** Share sheet with the image card (plus the same text). Uses a FileProvider content URI: no storage permission. */
    fun shareImage(context: Context, r: DailyShareResult) {
        val file = save(context, r)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, DailyShareText.build(r))
            clipData = android.content.ClipData.newRawUri("RiddleVerse Daily", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your daily result").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
