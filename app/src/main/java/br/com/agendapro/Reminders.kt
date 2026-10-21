package br.com.agendapro

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

object ReminderScheduler {
    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(ctx: Context, time: String) {
        val m = runCatching { toMin(time) }.getOrDefault(18 * 60)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE)
                if (prefs.getString("rem_on", "0") != "1") return@launch
                val db = AppDb.get(ctx)
                val list = db.appointments().activeByDate(LocalDate.now().plusDays(1).toString()).sortedBy { it.time }
                if (list.isNotEmpty()) {
                    val clients = db.clients().list()
                    val text = list.joinToString("\n") { a ->
                        "${a.time} · ${clients.firstOrNull { it.id == a.clientId }?.name ?: "Cliente"}"
                    }
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.createNotificationChannel(NotificationChannel("lembretes", "Lembretes", NotificationManager.IMPORTANCE_DEFAULT))
                    val open = PendingIntent.getActivity(
                        ctx, 2, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val n = Notification.Builder(ctx, "lembretes")
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Amanhã: ${list.size} atendimento(s)")
                        .setContentText("Toque para enviar os lembretes aos clientes")
                        .setStyle(Notification.BigTextStyle().bigText(text))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(100, n)
                }
                ReminderScheduler.schedule(ctx, prefs.getString("rem_time", "18:00") ?: "18:00")
            } finally {
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = ctx.getSharedPreferences("agenda", Context.MODE_PRIVATE)
        if (prefs.getString("rem_on", "0") == "1") {
            ReminderScheduler.schedule(ctx, prefs.getString("rem_time", "18:00") ?: "18:00")
        }
    }
}
