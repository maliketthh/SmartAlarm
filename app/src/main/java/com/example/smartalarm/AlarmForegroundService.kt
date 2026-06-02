package com.example.smartalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Foreground Service - сервис, который работает в фоне с постоянным уведомлением
// НУЖЕН для Android 8+ (Oreo и новее), чтобы приложение не убивали в фоне
// Этот сервис показывает иконку в статус-баре пока приложение активно
class AlarmForegroundService : Service() {

    // --- ОСНОВНОЙ МЕТОД ЗАПУСКА СЕРВИСА ---
    // Вызывается когда сервис запускается через startService() или startForegroundService()
    // intent - данные отправителя (может быть null)
    // flags - флаги запуска (обычно 0)
    // startId - уникальный идентификатор этого запуска
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 1. СОЗДАЁМ КАНАЛ ДЛЯ УВЕДОМЛЕНИЯ (нужен для Android 8+)
        createNotificationChannel()

        // 2. ЗАПУСКАЕМ СЕРВИС КАК FOREGROUND
        // startForeground() - делает сервис "важным" (система не убьёт его при нехватке памяти)
        // 1002 - уникальный ID уведомления
        // createNotification() - уведомление, которое будет висеть в шторке
        startForeground(1002, createNotification())

        // 3. ВОЗВРАЩАЕМ START_STICKY
        // START_STICKY означает: если систему убьёт сервис (из-за нехватки памяти),
        // то она попробует перезапустить его, когда станет больше ресурсов
        // При перезапуске intent будет null (нужно обрабатывать этот случай)
        return START_STICKY
    }

    // --- МЕТОД ДЛЯ СОЗДАНИЯ КАНАЛА УВЕДОМЛЕНИЙ (Android 8+ и выше) ---
    private fun createNotificationChannel() {
        // Проверяем версию Android (каналы появились в Android 8 - API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Создаём канал с ID "alarm_foreground"
            val channel = NotificationChannel(
                "alarm_foreground",              // Уникальный ID канала (должен совпадать с уведомлением)
                "Будильник",                     // Название канала (видит пользователь в настройках)
                NotificationManager.IMPORTANCE_HIGH  // Высокий приоритет (важное уведомление)
            )
            // Получаем системный сервис уведомлений
            val manager = getSystemService(NotificationManager::class.java)
            // Регистрируем канал в системе
            manager.createNotificationChannel(channel)
        }
    }

    // --- МЕТОД ДЛЯ СОЗДАНИЯ САМОГО УВЕДОМЛЕНИЯ ---
    private fun createNotification(): Notification {
        // Создаём Intent для открытия MainActivity при нажатии на уведомление
        val intent = Intent(this, MainActivity::class.java)

        // PendingIntent - "отложенный" Intent, который выполнится при нажатии
        // getActivity - потому что открываем Activity
        val pendingIntent = PendingIntent.getActivity(
            this,                    // Контекст
            0,                       // Код запроса (0 = не используется)
            intent,                  // Intent для запуска MainActivity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE  // Флаг для безопасности (Android 12+)
            else 0
        )

        // Строим уведомление с помощью билдера
        return NotificationCompat.Builder(this, "alarm_foreground")  // Указываем ID канала
            .setContentTitle("⏰ Будильник активен")     // Заголовок уведомления
            .setContentText("Приложение работает в фоне") // Текст уведомления
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // Иконка будильника
            .setContentIntent(pendingIntent)            // Что делать при нажатии
            // .setOngoing(true)  // Можно добавить, чтобы нельзя было смахнуть уведомление
            .build()
    }

    // --- МЕТОД ДЛЯ ПРИВЯЗКИ К АКТИВНОСТИ (НЕ ИСПОЛЬЗУЕТСЯ) ---
    // onBind нужен только если сервис будет привязываться к Activity через bindService()
    // Для AlarmForegroundService это не нужно, возвращаем null
    override fun onBind(intent: Intent?): IBinder? = null
}