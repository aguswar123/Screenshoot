package com.glowshot.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class ShotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: ShotAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var fx: FxView? = null
    private var savedUri: Uri? = null
    private var thumb: Bitmap? = null
    private var saveDone = false
    private var fxReady = false

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    /** Take a screenshot, save it, play the animation, then post the notification. */
    fun capture() {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val hb = screenshot.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hb.close()
                if (bmp == null) { toast("Screenshot failed"); return }
                onShot(bmp)
            }
            override fun onFailure(errorCode: Int) { toast("Screenshot failed (code $errorCode)") }
        })
    }

    /** Play only the animation (used by the Test button). */
    fun testFx() { showFx(Cfg.load(this)) {} }

    private fun onShot(bmp: Bitmap) {
        val cfg = Cfg.load(this)
        saveDone = false; fxReady = false; savedUri = null
        thumb = Bitmap.createScaledBitmap(bmp, 540, (540f * bmp.height / bmp.width).toInt(), true)
        Thread {
            val u = Store.save(applicationContext, bmp)
            main.post { savedUri = u; saveDone = true; tryNotify() }
        }.start()
        showFx(cfg) { fxReady = true; tryNotify() }
    }

    private fun tryNotify() {
        if (!(fxReady && saveDone)) return
        fxReady = false
        val u = savedUri
        if (u != null) Notifier.post(this, u, thumb) else toast("Couldn't save screenshot")
    }

    private fun showFx(cfg: Cfg, onNotify: () -> Unit) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        fx?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        val v = FxView(this, cfg, onNotify) { view ->
            try { wm.removeView(view) } catch (_: Exception) {}
            if (fx === view) fx = null
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        wm.addView(v, lp)
        fx = v
    }
}
