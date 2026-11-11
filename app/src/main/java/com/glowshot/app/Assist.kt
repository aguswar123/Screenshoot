package com.glowshot.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.widget.Toast

/** Registers the app as a selectable "Digital assistant app". */
class GlowAssistService : VoiceInteractionService()

class GlowSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = GlowSession(this)
}

/** Runs when the power button is long-pressed. Hides itself, then asks the accessibility service to capture. */
class GlowSession(private val svc: GlowSessionService) : VoiceInteractionSession(svc) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        hide()
        Handler(Looper.getMainLooper()).postDelayed({
            val s = ShotAccessibilityService.instance
            if (s == null) {
                Toast.makeText(svc, "GlowShot: enable the accessibility service first", Toast.LENGTH_LONG).show()
            } else {
                s.capture()
            }
        }, 250)
    }
}

/** Required by some Android versions for an assistant to be listed; does nothing. */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: RecognitionService.Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: RecognitionService.Callback?) {}
    override fun onStopListening(listener: RecognitionService.Callback?) {}
}
