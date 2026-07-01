package com.yuchen.ailedger.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import com.yuchen.ailedger.data.PlanTaskStore
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskType

class PlanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(PlanScheduler.EXTRA_TASK_ID).orEmpty()
        if (taskId.isBlank()) return

        val store = PlanTaskStore(context)
        val task = store.loadTasks().firstOrNull { it.id == taskId } ?: return
        if (!task.enabled) return

        PlanNotificationPublisher.show(context, task)

        val now = System.currentTimeMillis()
        val nextRun = if (task.repeatMode == PlanRepeatMode.Once) {
            null
        } else {
            PlanScheduleCalculator.nextOccurrence(task, now + 1_000L)
        }
        val updated = store.updateTask(task.id) {
            it.copy(
                enabled = nextRun != null,
                nextRunAtMillis = nextRun,
                lastRunAtMillis = now,
                lastResult = "已提醒",
            )
        } ?: return

        val scheduler = PlanScheduler(context)
        if (updated.enabled && updated.nextRunAtMillis != null) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.id)
        }
    }
}

private object PlanNotificationPublisher {
    private const val CHANNEL_ID = "ai_ledger_plan_reminders"

    fun show(context: Context, task: PlanTask) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode() and Int.MAX_VALUE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val detail = task.note.ifBlank {
            if (task.type == PlanTaskType.Alarm) "闹钟时间到了" else "计划提醒时间到了"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(task.title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (task.type == PlanTaskType.Alarm) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_REMINDER
                },
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "计划提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "计划、提醒与闹钟通知"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
