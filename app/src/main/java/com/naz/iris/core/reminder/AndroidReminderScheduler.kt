package com.naz.iris.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidReminderScheduler(
    private val context: Context
) : ReminderScheduler {

    override fun schedule(request: ReminderRequest): ReminderScheduleResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, request.reminderId)
            putExtra(ReminderReceiver.EXTRA_TITLE, request.title)
            putExtra(ReminderReceiver.EXTRA_BODY, request.body)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = canScheduleExactAlarms(alarmManager)

        return try {
            when {
                canExact -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        request.triggerAtMillis,
                        pendingIntent
                    )

                    ReminderScheduleResult(
                        success = true,
                        reminderId = request.reminderId,
                        exact = true,
                        message = "Reminder exact alarm ile planlandı."
                    )
                }

                else -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        request.triggerAtMillis,
                        pendingIntent
                    )

                    ReminderScheduleResult(
                        success = true,
                        reminderId = request.reminderId,
                        exact = false,
                        message = "Reminder fallback alarm ile planlandı. Exact izin kapalı olabilir."
                    )
                }
            }
        } catch (e: Exception) {
            ReminderScheduleResult(
                success = false,
                reminderId = request.reminderId,
                exact = false,
                message = "Reminder planlanamadı: ${e.message ?: "bilinmeyen hata"}"
            )
        }
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}