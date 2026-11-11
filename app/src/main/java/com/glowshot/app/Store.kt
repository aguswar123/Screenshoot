package com.glowshot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves screenshots to Pictures/Screenshots through MediaStore (no storage permission needed). */
object Store {
    fun save(ctx: Context, bmp: Bitmap): Uri? {
        val name = "Screenshot_" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        return try {
            r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0)
            r.update(uri, v, null, null)
            uri
        } catch (e: Exception) {
            r.delete(uri, null, null); null
        }
    }
}

/** Heads-up notification at the top of the screen: Share / Delete / Dismiss. */
object Notifier {
    const val CH = "shots"
    const val ID = 4242

    fun post(ctx: Context, uri: Uri, thumb: Bitmap?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Screenshots", NotificationManager.IMPORTANCE_HIGH))
        val fl = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share screenshot").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val sharePi = PendingIntent.getActivity(ctx, 1, chooser, fl)

        val delPi = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, ActionReceiver::class.java).setAction("delete").putExtra("uri", uri.toString()), fl
        )
        val disPi = PendingIntent.getBroadcast(
            ctx, 3, Intent(ctx, ActionReceiver::class.java).setAction("dismiss"), fl
        )
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val viewPi = PendingIntent.getActivity(ctx, 4, view, fl)

        fun act(res: Int, label: String, pi: PendingIntent) =
            Notification.Action.Builder(Icon.createWithResource(ctx, res), label, pi).build()

        val b = Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Screenshot captured")
            .setContentText("Saved to Pictures/Screenshots")
            .setContentIntent(viewPi)
            .setAutoCancel(true)
            .addAction(act(android.R.drawable.ic_menu_share, "Share", sharePi))
            .addAction(act(android.R.drawable.ic_menu_delete, "Delete", delPi))
            .addAction(act(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", disPi))
        if (thumb != null) b.setLargeIcon(thumb).setStyle(Notification.BigPictureStyle().bigPicture(thumb))
        nm.notify(ID, b.build())
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.getSystemService(NotificationManager::class.java).cancel(Notifier.ID)
        if (i.action == "delete") {
            val u = i.getStringExtra("uri") ?: return
            try {
                c.contentResolver.delete(Uri.parse(u), null, null)
                Toast.makeText(c, "Screenshot deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(c, "Couldn't delete screenshot", Toast.LENGTH_SHORT).show()
            }
        }
        // "dismiss": only closes the notification, the screenshot stays in the gallery.
    }
}
