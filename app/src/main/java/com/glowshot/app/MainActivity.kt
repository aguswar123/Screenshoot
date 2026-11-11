package com.glowshot.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var cfg: Cfg
    private lateinit var status: TextView
    private lateinit var preview: PosPreview

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Cfg.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(root); fitsSystemWindows = true })

        root.addView(label("GlowShot", 26f, true))
        root.addView(label("Screenshot with a Siri-style glow. Trigger: power button (assistant).", 13f, false))

        status = label("", 14f, false).apply { setPadding(0, dp(14), 0, dp(6)) }
        root.addView(status)

        root.addView(button("1. Set GlowShot as default assistant") {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        })
        root.addView(button("2. Enable GlowShot accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("3. Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
        })
        root.addView(label(
            "Also make sure your phone's power button long-press is set to open the assistant " +
                "(Pixel: Settings > System > Gestures > Press & hold power button. " +
                "Samsung: Settings > Advanced features > Side button > Press and hold > Wake digital assistant).",
            12f, false
        ).apply { setPadding(0, dp(6), 0, dp(12)) })

        root.addView(label("Power button position", 18f, true))
        root.addView(label("Pick the edge your power button is on, then set its height.", 12f, false))
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val rl = RadioButton(this).apply { text = "Left edge"; id = 1 }
        val rr = RadioButton(this).apply { text = "Right edge"; id = 2 }
        rg.addView(rl); rg.addView(rr)
        rg.check(if (cfg.side == "l") 1 else 2)
        preview = PosPreview(this)
        rg.setOnCheckedChangeListener { _, id ->
            cfg.side = if (id == 1) "l" else "r"; cfg.save(this); preview.invalidate()
        }
        root.addView(rg)
        slider(root, "Button height from top", 8, 92, cfg.pos, { "$it%" }) { cfg.pos = it }
        root.addView(preview, LinearLayout.LayoutParams(dp(110), dp(200)).apply {
            topMargin = dp(8); gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        root.addView(label("Animation", 18f, true).apply { setPadding(0, dp(16), 0, 0) })
        slider(root, "Glow thickness", 3, 18, cfg.thick, { "$it" }) { cfg.thick = it }
        slider(root, "Wobble", 0, 30, cfg.wob, { "$it" }) { cfg.wob = it }
        slider(root, "Duration", 10, 60, cfg.dur, { "%.1f s".format(it / 10f) }) { cfg.dur = it }
        slider(root, "Screen corner radius", 0, 70, cfg.radius, { "$it dp" }) { cfg.radius = it }

        root.addView(button("Test animation") {
            val s = ShotAccessibilityService.instance
            if (s == null) Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
            else { cfg.save(this); s.testFx() }
        }.apply { setPadding(dp(8), dp(14), dp(8), dp(14)) })
    }

    override fun onResume() {
        super.onResume()
        val assistant = try { Settings.Secure.getString(contentResolver, "assistant") } catch (e: Exception) { null }
        val isAssistant = assistant?.startsWith(packageName) == true
        val a11y = ShotAccessibilityService.instance != null
        status.text = (if (isAssistant) "✓" else "✗") + " Default assistant\n" +
            (if (a11y) "✓" else "✗") + " Accessibility service"
    }

    private fun label(s: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(s: String, click: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { click() }
    }

    private fun slider(
        parent: LinearLayout, name: String, min: Int, max: Int, v: Int,
        fmt: (Int) -> String, set: (Int) -> Unit
    ) {
        val tv = TextView(this).apply { text = "$name: ${fmt(v)}"; setPadding(0, dp(10), 0, 0) }
        val sb = SeekBar(this).apply { this.max = max - min; progress = v - min }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val x = p + min
                tv.text = "$name: ${fmt(x)}"; set(x); cfg.save(this@MainActivity)
                preview.invalidate()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(tv); parent.addView(sb)
    }

    /** Tiny phone outline showing where the power button is mapped. */
    inner class PosPreview(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val m = dp(8).toFloat()
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(3).toFloat(); p.color = Color.GRAY
            cv.drawRoundRect(m, m, w - m, h - m, dp(22).toFloat(), dp(22).toFloat(), p)
            p.style = Paint.Style.FILL; p.color = Color.parseColor("#8A5CFF")
            val y = m + (h - 2 * m) * cfg.pos / 100f
            val bw = dp(5).toFloat(); val bh = dp(36).toFloat()
            val x = if (cfg.side == "r") w - m else m
            cv.drawRoundRect(x - bw / 2, y - bh / 2, x + bw / 2, y + bh / 2, bw / 2, bw / 2, p)
        }
    }
}
