package com.davide.salvacibo.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.davide.salvacibo.MainActivity
import com.davide.salvacibo.R

object Notifier {

    const val CHANNEL_ID = "scadenze"
    const val EXTRA_PRODUCT_ID = "product_id"
    private const val TEST_ID = 999_999

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scadenze",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avvisi per i prodotti che stanno per scadere"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showProduct(context: Context, productId: Long, title: String, body: String) {
        if (!canNotify(context)) return
        val notificationId = productId.toInt()

        val open = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PRODUCT_ID, productId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val consumed = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_CONSUMED)
                .putExtra(EXTRA_PRODUCT_ID, productId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Consumato ✓", consumed)
            .build()

        notify(context, notificationId, notification)
    }

    fun showTest(context: Context) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle("🍗 Pollo scade domani")
            .setContentText("Questa è una notifica di prova: gli avvisi funzionano!")
            .setAutoCancel(true)
            .build()
        notify(context, TEST_ID, notification)
    }

    fun cancel(context: Context, productId: Long) {
        NotificationManagerCompat.from(context).cancel(productId.toInt())
    }

    @SuppressLint("MissingPermission") // controllato in canNotify()
    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
