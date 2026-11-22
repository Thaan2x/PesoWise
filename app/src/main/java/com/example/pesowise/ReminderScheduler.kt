package com.example.pesowise

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun schedule(
        context: Context,
        id: String,
        title: String,
        dueDate: LocalDate,
        daysBefore: Int
    ): Boolean {

        val notificationDate = dueDate.minusDays(
            daysBefore.toLong()
        )

        // Default reminder time: 9:00 AM
        val notificationTime = ZonedDateTime.of(
            notificationDate,
            LocalTime.of(9, 0),
            ZoneId.systemDefault()
        )

        val now = ZonedDateTime.now()

        // Do not schedule dates already in the past
        if (!notificationTime.isAfter(now)) {
            return false
        }

        val delay = Duration.between(
            now.toInstant(),
            notificationTime.toInstant()
        ).toMillis()

        val inputData = Data.Builder()
            .putString(ReminderWorker.KEY_ID, id)
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(
                ReminderWorker.KEY_DATE,
                dueDate.toString()
            )
            .build()

        val workRequest =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(
                    delay,
                    TimeUnit.MILLISECONDS
                )
                .setInputData(inputData)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "reminder_$id",
                ExistingWorkPolicy.KEEP,
                workRequest
            )

        return true
    }
}