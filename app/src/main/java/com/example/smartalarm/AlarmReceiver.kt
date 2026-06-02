package com.example.smartalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat

// BroadcastReceiver - компонент Android, который реагирует на системные события
// AlarmReceiver срабатывает, когда будильник (AlarmManager) отправляет сигнал
class AlarmReceiver : BroadcastReceiver() {

    // Этот метод вызывается, когда AlarmManager срабатывает в нужное время
    // context - контекст приложения
    // intent - данные от будильника (может содержать доп. параметры)
    override fun onReceive(context: Context, intent: Intent) {

        // 1. ПРОБУЖДАЕМ УСТРОЙСТВО (WakeLock)
        // Без этого телефон может остаться в спящем режиме
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Создаём блокировку, которая:
        // - PARTIAL_WAKE_LOCK: держит CPU активным (даже если экран выключен)
        // - ACQUIRE_CAUSES_WAKEUP: включает экран (важно для будильника!)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AlarmReceiver:WakeLock"  // Тег для отладки
        )

        // Захватываем блокировку на 60 секунд максимум
        // Через 60 секунд телефон снова может уснуть (если не выключили будильник)
        wakeLock.acquire(60 * 1000L)

        // 2. ЗАПУСКАЕМ СЕРВИС СО ЗВУКОМ (AlarmSoundService)
        val soundIntent = Intent(context, AlarmSoundService::class.java)

        // Для Android 8+ (Oreo и новее) нужен foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // startForegroundService - для запуска сервиса, который покажет уведомление
            context.startForegroundService(soundIntent)
        } else {
            // Для старых версий можно обычный startService
            context.startService(soundIntent)
        }

        // 3. ПОКАЗЫВАЕМ УВЕДОМЛЕНИЕ В ШТОРКЕ
        showNotification(context)

        // Примечание: WakeLock НЕ освобождаем здесь, потому что он нужен для работы
        // сервиса и Activity. Освободится автоматически через 60 секунд.
    }

    // Метод для создания и отображения уведомления
    private fun showNotification(context: Context) {
        val channelId = "alarm_channel"  // Уникальный ID канала уведомлений

        // Получаем системный сервис уведомлений
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. СОЗДАЁМ КАНАЛ УВЕДОМЛЕНИЯ (нужен для Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,                    // ID канала
                "Будильник",                 // Название для пользователя
                NotificationManager.IMPORTANCE_HIGH  // Высокий приоритет (будет звук/вибрация)
            ).apply {
                description = "Будильник"    // Описание канала
                setShowBadge(true)           // Показывать значок на иконке приложения
            }
            notificationManager.createNotificationChannel(channel)  // Регистрируем канал
        }

        // 2. СОЗДАЁМ INTENT ДЛЯ ОТКРЫТИЯ AlarmShakeActivity
        // Когда пользователь нажмёт на уведомление - откроется экран с тряской
        val intent = Intent(context, AlarmShakeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)     // Открыть в новой задаче
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)    // Очистить стек Activity сверху
        }

        // PendingIntent - "отложенный" Intent, который выполнится при нажатии
        val pendingIntent = PendingIntent.getActivity(
            context,               // Контекст
            0,                     // Код запроса (0 = не используется)
            intent,                // Intent для запуска Activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0  // Флаг неизменяемости (требование Android 12+)
        )

        // 3. СОЗДАЁМ САМО УВЕДОМЛЕНИЕ
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // Иконка будильника
            .setContentTitle("⏰ БУДИЛЬНИК!")                     // Заголовок
            .setContentText("Нажмите, чтобы выключить")           // Текст
            .setPriority(NotificationCompat.PRIORITY_MAX)        // Максимальный приоритет
            .setContentIntent(pendingIntent)                     // Что делать при нажатии
            .setAutoCancel(true)                                 // Удалить после нажатия
            // Добавляем кнопку прямо в уведомлении
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,  // Иконка закрытия
                "ВЫКЛЮЧИТЬ",                                     // Текст кнопки
                pendingIntent                                    // То же действие, что и при нажатии
            )
            .build()

        // 4. ПОКАЗЫВАЕМ УВЕДОМЛЕНИЕ (ID 1001 - уникальный для отмены/обновления)
        notificationManager.notify(1001, notification)
    }
}