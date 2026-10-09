package dev.thirty.app.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import dev.thirty.app.data.local.DayStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a minimal black/white journey card (1080x1400) that the user
 * can save or share. Pure Canvas — no extra dependencies.
 */
object JourneyImage {

    private const val W = 1080
    private val BLACK = Color.parseColor("#000000")
    private val WHITE = Color.WHITE
    private val GRAY = Color.parseColor("#8A8A8A")
    private val SURFACE = Color.parseColor("#151515")
    private val BORDER = Color.parseColor("#292929")

    fun render(
        title: String,
        statuses: List<DayStatus>,
        todayDay: Int,
        streak: Int,
        best: Int,
        lengthDays: Int
    ): Bitmap {
        val n = lengthDays.coerceIn(1, statuses.size.coerceAtLeast(1))
        val compact = n > 90
        val cols = if (compact) 10 else 6
        val cell = if (compact) 84f else 130f
        val gap = if (compact) 16f else 26f
        val rows = (n + cols - 1) / cols
        val h = (660 + rows * (cell + gap) + 150).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BLACK)

        var y = 130f
        // Brand
        c.drawText("THIRTY", 90f, y, paint(WHITE, 72f, true))
        y += 12f
        c.drawCircle(108f, y + 34f, 12f, paint(WHITE, 12f))
        c.drawText("30 DAYS. ONE CHANGE.", 140f, y + 52f, paint(WHITE, 34f))
        y += 130f

        // Title (manual wrap, max 2 lines)
        val titlePaint = paint(WHITE, 52f, true)
        val words = title.trim().split("\\s+".toRegex())
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (titlePaint.measureText(trial) > W - 180) {
                lines += cur
                cur = w
                if (lines.size == 2) break
            } else {
                cur = trial
            }
        }
        if (cur.isNotEmpty() && lines.size < 2) lines += cur
        if (lines.size == 2 && words.joinToString(" ").length > lines.joinToString(" ").length) {
            lines[1] = lines[1].trimEnd() + "…"
        }
        lines.forEach { line ->
            c.drawText(line, 90f, y, titlePaint)
            y += 66f
        }
        y += 30f

        // Big progress
        val done = statuses.take(n).count { it == DayStatus.COMPLETED || it == DayStatus.REST }
        c.drawText("$done / $n", 90f, y, paint(WHITE, 120f, true))
        y += 90f
        c.drawText(
            "Streak $streak   ·   Best $best",
            90f, y, paint(GRAY, 38f)
        )
        y += 90f

        // Journey grid
        val gridW = cols * cell + (cols - 1) * gap
        var gx = (W - gridW) / 2f
        var gy = y
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val dayText = paint(GRAY, 34f, true)
        val restText = paint(WHITE, 44f, true)
        statuses.take(n).forEachIndexed { i, s ->
            val col = i % cols
            val row = i / cols
            val left = gx + col * (cell + gap)
            val top = gy + row * (cell + gap)
            val cx = left + cell / 2f
            val cy = top + cell / 2f
            when (s) {
                DayStatus.COMPLETED -> {
                    fill.color = WHITE
                    fill.style = Paint.Style.FILL
                    c.drawRoundRect(left, top, left + cell, top + cell, 24f, 24f, fill)
                    // check mark
                    stroke.color = BLACK
                    stroke.strokeWidth = 10f
                    stroke.strokeCap = Paint.Cap.ROUND
                    c.drawLine(cx - 28, cy + 2, cx - 8, cy + 22, stroke)
                    c.drawLine(cx - 8, cy + 22, cx + 30, cy - 24, stroke)
                }
                DayStatus.REST -> {
                    fill.color = SURFACE
                    fill.style = Paint.Style.FILL
                    c.drawRoundRect(left, top, left + cell, top + cell, 24f, 24f, fill)
                    stroke.color = WHITE
                    stroke.strokeWidth = 6f
                    c.drawRoundRect(left, top, left + cell, top + cell, 24f, 24f, stroke)
                    c.drawText("R", cx - 16f, cy + 16f, restText)
                }
                DayStatus.MISSED -> {
                    fill.color = BORDER
                    fill.style = Paint.Style.FILL
                    c.drawRoundRect(left, top, left + cell, top + cell, 24f, 24f, fill)
                }
                else -> {
                    fill.color = SURFACE
                    fill.style = Paint.Style.FILL
                    c.drawRoundRect(left, top, left + cell, top + cell, 24f, 24f, fill)
                    if (i + 1 == todayDay) {
                        stroke.color = WHITE
                        stroke.strokeWidth = 6f
                        c.drawRoundRect(left, top, left + cell, top + cell, 24f, 24f, stroke)
                    }
                    val num = String.format("%02d", i + 1)
                    c.drawText(num, cx - dayText.measureText(num) / 2f, cy + 12f, dayText)
                }
            }
        }
        y = gy + rows * (cell + gap) + 70f

        // Footer
        c.drawText("Show up every day.", 90f, y, paint(GRAY, 36f))
        return bmp
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    /**
     * Milestone card (day 7 / 15 / finish): huge number, black & white,
     * same width as the journey card so both feel like one set.
     */
    fun renderMilestone(
        title: String,
        day: Int,
        lengthDays: Int,
        streak: Int,
        best: Int
    ): Bitmap {
        val h = 1400
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BLACK)

        var y = 170f
        c.drawText("THIRTY", 90f, y, paint(WHITE, 72f, true))
        y += 110f
        c.drawText("MILESTONE", 90f, y, paint(GRAY, 40f, true))
        y += 210f
        c.drawText("DAY $day", 90f, y, paint(WHITE, 170f, true))
        y += 90f
        c.drawText("of $lengthDays", 90f, y, paint(GRAY, 64f))
        y += 150f

        // Title on one line, ellipsized if it runs long.
        val titlePaint = paint(WHITE, 52f, true)
        var t = title.trim()
        while (t.isNotEmpty() && titlePaint.measureText(t) > W - 180f) t = t.dropLast(1)
        if (t != title.trim()) t = t.trimEnd() + "…"
        c.drawText(t, 90f, y, titlePaint)
        y += 90f
        c.drawText("$streak day streak  ·  best $best", 90f, y, paint(GRAY, 38f))
        c.drawText("Show up every day.", 90f, h - 110f, paint(GRAY, 36f))
        return bmp
    }

    suspend fun writeToUri(context: Context, uri: Uri, bmp: Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                } ?: return@withContext false
                true
            } catch (_: Exception) {
                false
            }
        }
}
