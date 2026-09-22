package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        if (action == "com.example.ACTION_DAILY_REMINDER" || 
            action == "com.example.ACTION_PLAN_REMINDER" ||
            action == "com.example.ACTION_TEST_REMINDER" || 
            action == "com.example.ACTION_TEST_PLAN_REMINDER" || 
            action == Intent.ACTION_BOOT_COMPLETED) {

            val prefs = context.getSharedPreferences("family_prefs", Context.MODE_PRIVATE)
            val isDailyEnabled = prefs.getBoolean("daily_reminder_enabled", true)
            val isPlanEnabled = prefs.getBoolean("plan_reminder_enabled", true)
            
            if (action == "com.example.ACTION_DAILY_REMINDER" && !isDailyEnabled) {
                cancelDailyReminder(context)
                return
            }
            if (action == "com.example.ACTION_PLAN_REMINDER" && !isPlanEnabled) {
                cancelPlanReminder(context)
                return
            }

            // Reschedule next repeating alarm if it's the reminder trigger
            if (action == "com.example.ACTION_DAILY_REMINDER" && isDailyEnabled) {
                scheduleDailyReminder(context)
            }
            if (action == "com.example.ACTION_PLAN_REMINDER" && isPlanEnabled) {
                schedulePlanReminder(context)
            }

            if (action == Intent.ACTION_BOOT_COMPLETED) {
                if (isDailyEnabled) scheduleDailyReminder(context)
                if (isPlanEnabled) schedulePlanReminder(context)
            }

            val isPlanType = (action == "com.example.ACTION_PLAN_REMINDER" || action == "com.example.ACTION_TEST_PLAN_REMINDER")
            val isTestType = (action == "com.example.ACTION_TEST_REMINDER" || action == "com.example.ACTION_TEST_PLAN_REMINDER")
            
            // Read settings and trigger notification
            triggerNotification(context, isTest = isTestType, isPlan = isPlanType)
        }
    }

    private fun triggerNotification(context: Context, isTest: Boolean, isPlan: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "family_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Family Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for daily task reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val prefs = context.getSharedPreferences("family_prefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("app_language", "EN") ?: "EN"

        val title = if (currentLang == "HE") {
            if (isPlan) {
                if (isTest) "בדיקת תזכורת תכנון יום" else "תזכורת בוקר: תכנון וארגון היום"
            } else {
                if (isTest) "בדיקת תזכורת סיום משימות" else "תזכורת ערב: השלמת משימות"
            }
        } else {
            if (isPlan) {
                if (isTest) "Test Plan Day Reminder" else "Morning Reminder: Plan & Organize Today"
            } else {
                if (isTest) "Test Finish Tasks Reminder" else "Evening Reminder: Complete Chores & Tasks"
            }
        }

        val text = if (currentLang == "HE") {
            if (isPlan) {
                if (isTest) "היי! בדיקת תזכורת לתכנון היום עובדת בהצלחה." else "בוקר טוב! קחו 2 דקות לתכנן ולארגן את המשימות שלכם ליום מוצלח."
            } else {
                if (isTest) "היי! בדיקת תזכורת לסיום משימות עובדת בהצלחה." else "אל תשכחו לבדוק את המטלות שלכם לפני השינה!"
            }
        } else {
            if (isPlan) {
                if (isTest) "Hey! Your day-planning reminder test is complete and successful." else "Good morning! Let's take 2 minutes to organize and optimize your tasks for a great day."
            } else {
                if (isTest) "Hey! Your task completion reminder test is complete and successful." else "Don't forget to complete and check off your tasks for today!"
            }
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isPlan) 2 else 1,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notifyId = if (isPlan) {
            if (isTest) 1104 else 1103
        } else {
            if (isTest) 1102 else 1101
        }
        notificationManager.notify(notifyId, notification)
    }

    companion object {
        fun scheduleDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.ACTION_DAILY_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }

        fun cancelDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.ACTION_DAILY_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun schedulePlanReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.ACTION_PLAN_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1103,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }

        fun cancelPlanReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.ACTION_PLAN_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1103,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
