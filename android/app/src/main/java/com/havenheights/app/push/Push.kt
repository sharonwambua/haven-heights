package com.havenheights.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.havenheights.app.HavenApplication
import com.havenheights.app.MainActivity
import com.havenheights.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps the server's list of this phone up to date. Does nothing when Firebase is not configured. */
class PushRegistrar(private val app: HavenApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun available() = FirebaseApp.getApps(app).isNotEmpty()

    fun register() {
        if (!available() || app.session.token == null) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> registerToken(token) }
    }

    fun registerToken(token: String) {
        app.session.pushToken = token
        if (app.session.token == null) return
        scope.launch { app.repository.registerDevice(token) }
    }

    suspend fun unregister() {
        val token = app.session.pushToken ?: return
        app.repository.unregisterDevice(token)
    }
}

class HavenMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (application as HavenApplication).push.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: return
        (application as HavenApplication).refreshSignal.tryEmit(Unit)
        val body = message.data["body"].orEmpty()
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF6B1E2E.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Estate updates", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Notices, repair updates and water readings" }
            )
        }
    }

    private companion object { const val CHANNEL = "haven_updates" }
}
