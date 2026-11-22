package com.example.pesowise

import android.Manifest
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
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID = "pesowise_reminders"
        const val KEY_TITLE = "reminder_title"
        const val KEY_DATE = "reminder_date"
        const val KEY_ID = "reminder_id"
    }

    override fun doWork(): Result {

        val title = inputData.getString(KEY_TITLE)
            ?: return Result.failure()

        val date = inputData.getString(KEY_DATE)
            ?: return Result.failure()

        val reminderId = inputData.getString(KEY_ID)
            ?: return Result.failure()

        createNotificationChannel()

        // Check Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val notificationId = reminderId.hashCode()

        val intent = Intent(
            applicationContext,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Your bill is due on $date.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId, notification)

            Result.success()
        } catch (e: SecurityException) {
            Result.success()
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Financial Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Notifications for upcoming bills and expenses"
            }

            val manager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }
}