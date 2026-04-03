package com.naz.iris.core.reminder

data class ReminderRequest(
    val reminderId: Int,
    val title: String,
    val body: String,
    val triggerAtMillis: Long
)

data class ReminderScheduleResult(
    val success: Boolean,
    val reminderId: Int,
    val exact: Boolean,
    val message: String
)

interface ReminderScheduler {
    fun schedule(request: ReminderRequest): ReminderScheduleResult
}