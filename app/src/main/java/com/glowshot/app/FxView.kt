package com.glowshot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Full-screen overlay that plays the animation:
 *  1. power button is pushed into the screen and held for 1s (bulge + ripples),
 *  2. an Apple-Intelligence style glow flows from the button around the screen edge, wobbling,
 *  3. a white flash, then [onNotify] fires so the notification can appear.
 */
class FxView(
    ctx: Context,
    private val cfg: Cfg,
    private val onNotify: () -> Unit,
    private val onDone: (FxView) -> Unit
) : View(ctx) {

    private val n = 260
    private val px = FloatArray(n); private val py = FloatArray(n)
    private val nx = FloatArray(n); private val ny = FloatArray(n)
    private val uu = FloatArray(n); private val dep = FloatArray(n)
    private var fw = 0f; private var fh = 0f; private var sc = 1f
    private var u0 = 0f
    private var t0 = 0L
    private var notified = false
    private var finished = false
    private val pi = Math.PI.toFloat()

    private val colors = intArrayOf(
        "#BC82F3", "#F5B9EA", "#8D9FFF", "#AA6EEE", "#FF6778", "#FFBA71", "#C686FF", "#BC82F3"
    ).map { Color.parseColor(it) }.toIntArray()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val bump = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val mat = Matrix()
    private var shader: SweepGradient? = null

    private val totalMs: Long get() = Cfg.HOLD_MS + cfg.durMs + 400

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        fw = w.toFloat(); fh = h.toFloat(); sc = fw / 226f
        buildPerimeter()
        shader = SweepGradient(fw / 2f, fh / 2f, colors, null)
        // glow starts at the point on the edge closest to the power button
        val ex = if (cfg.side == "r") fw else 0f
        val ey = fh * cfg.pos / 100f
        var best = Float.MAX_VALUE
        for (i in 0 until n) {
            val d = abs(px[i] - ex) + abs(py[i] - ey)
            if (d < best) { best = d; u0 = uu[i] }
        }
    }

    /** Points around the rounded screen outline, starting at bottom centre, with outward normals. */
    private fun buildPerimeter() {
        val w = fw; val h = fh
        val r = max(1f, min(cfg.radius * resources.displayMetrics.density, min(w, h) / 2f - 1f))
        val sx = w - 2 * r; val sy = h - 2 * r; val a = pi * r / 2f
        val segs = listOf<Pair<Float, (Float) -> FloatArray>>(
            (sx / 2f) to { d: Float -> floatArrayOf(w / 2f - d, h, 0f, 1f) },
            a to { d: Float -> val q = pi / 2f + d / r; floatArrayOf(r + r * cos(q), h - r + r * sin(q), cos(q), sin(q)) },
            sy to { d: Float -> floatArrayOf(0f, h - r - d, -1f, 0f) },
            a to { d: Float -> val q = pi + d / r; floatArrayOf(r + r * cos(q), r + r * sin(q), cos(q), sin(q)) },
            sx to { d: Float -> floatArrayOf(r + d, 0f, 0f, -1f) },
            a to { d: Float -> val q = 1.5f * pi + d / r; floatArrayOf(w - r + r * cos(q), r + r * sin(q), cos(q), sin(q)) },
            sy to { d: Float -> floatArrayOf(w, r + d, 1f, 0f) },
            a to { d: Float -> val q = d / r; floatArrayOf(w - r + r * cos(q), h - r + r * sin(q), cos(q), sin(q)) },
            (sx / 2f) to { d: Float -> floatArrayOf(w - r - d, h, 0f, 1f) }
        )
        var total = 0f
        for (s in segs) total += s.first
        for (i in 0 until n) {
            var d = i.toFloat() / n * total
            var k = 0
            while (k < segs.size - 1 && d > segs[k].first) { d -= segs[k].first; k++ }
            val q = segs[k].second(d)
            px[i] = q[0]; py[i] = q[1]; nx[i] = q[2]; ny[i] = q[3]; uu[i] = i.toFloat() / n
        }
    }

    private fun wa(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)

    override fun onDraw(c: Canvas) {
        if (fw == 0f) return
        val now = SystemClock.uptimeMillis()
        if (t0 == 0L) t0 = now
        val t = now - t0

        drawPush(c, t)
        val gt = t - Cfg.HOLD_MS
        if (gt > 0) drawGlow(c, gt)

        val flashStart = Cfg.HOLD_MS + cfg.durMs / 2
        if (t in flashStart..(flashStart + 400)) c.drawColor(wa(0.7f * (1f - (t - flashStart) / 400f)))
        if (!notified && t >= flashStart + 250) { notified = true; post { onNotify() } }

        if (t >= totalMs) {
            if (!finished) { finished = true; post { onDone(this) } }
        } else postInvalidateOnAnimation()
    }

    /** Button pushed through the screen: bulge from the edge, held ~1s, plus two shockwave rings. */
    private fun drawPush(c: Canvas, t: Long) {
        val ex = if (cfg.side == "r") fw else 0f
        val ey = fh * cfg.pos / 100f
        val dir = if (cfg.side == "r") -1f else 1f
        val p = t / 1200f
        if (p < 1f) {
            val dx: Float; val a: Float
            when {
                p < .12f -> { val e = p / .12f; dx = 34f * (1f - (1f - e) * (1f - e)); a = e }
                p < .20f -> { dx = 34f - 6f * ((p - .12f) / .08f); a = 1f }
                p < .83f -> { dx = 28f; a = 1f }
                else -> { val e = (p - .83f) / .17f; dx = 28f * (1f - e); a = 1f - e }
            }
            val r = 38f * sc
            c.save()
            c.translate(ex + dir * dx * sc, ey)
            c.scale(1f, 70f / 38f)
            bump.shader = RadialGradient(
                0f, 0f, r, intArrayOf(wa(a * .65f), wa(a * .2f), wa(0f)),
                floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(0f, 0f, r, bump)
            c.restore()
        }
        for (delay in longArrayOf(0L, 140L)) {
            val e = (t - delay) / 900f
            if (e in 0f..1f) {
                ring.strokeWidth = 2f * sc
                ring.alpha = (0.7f * (1f - e) * 255).toInt()
                c.drawCircle(ex, ey, 20f * sc * (.3f + 6.7f * e), ring)
            }
        }
    }

    private fun drawGlow(c: Canvas, gt: Long) {
        val k = gt / cfg.durMs.toFloat()
        if (k >= 1f) return
        val front = min(k / .38f, 1f) * .68f
        val fade = if (k > .7f) max(0f, 1f - (k - .7f) / .3f) else 1f
        val thick = cfg.thick.toFloat(); val wob = cfg.wob.toFloat()
        for (i in 0 until n) {
            val d0 = abs(uu[i] - u0); val d = min(d0, 1f - d0)
            var g = ((front - d) / .14f).coerceIn(0f, 1f); g = g * g * (3f - 2f * g)
            val wv = sin(uu[i] * pi * 10f + gt / 260f) * .6f + sin(uu[i] * pi * 22f - gt / 190f) * .4f
            dep[i] = max(0f, g * (thick * 2f + wob * wv * .9f + 4f)) * sc
        }
        mat.setRotate(gt / 900f * 57.3f, fw / 2f, fh / 2f)
        shader?.setLocalMatrix(mat)
        fill.shader = shader
        fill.alpha = (fade * .30f * 255).toInt()
        // several nested bands = soft falloff without blur filters
        for (s in floatArrayOf(2.2f, 1.7f, 1.25f, .9f, .55f, .3f)) {
            path.rewind()
            for (i in 0 until n) {
                val x = px[i] + nx[i] * 3f * sc; val y = py[i] + ny[i] * 3f * sc
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in n - 1 downTo 0) path.lineTo(px[i] - nx[i] * dep[i] * s, py[i] - ny[i] * dep[i] * s)
            path.close()
            c.drawPath(path, fill)
        }
    }
}
