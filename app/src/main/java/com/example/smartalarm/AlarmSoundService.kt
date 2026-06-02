package com.example.smartalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

// Сервис для воспроизведения звука и вибрации будильника в фоне
// Service - компонент Android для выполнения долгих операций в фоне
class AlarmSoundService : Service() {

    // MediaPlayer - проигрыватель аудио (поддерживает mp3, wav и т.д.)
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator  // Для вибрации телефона

    // Основной метод сервиса, вызывается при запуске (startService)
    // intent - данные отправителя
    // flags - флаги запуска
    // startId - уникальный ID запуска
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 1. СОЗДАЁМ ФОНОВОЕ УВЕДОМЛЕНИЕ
        // Без этого Android убьёт сервис через несколько секунд на новых версиях
        createNotificationChannel()     // Создаём канал уведомления (нужен для Android 8+)
        startForeground(1002, createNotification())  // Запускаем foreground-сервис

        // 2. ЗАПУСКАЕМ ЗВУК БУДИЛЬНИКА
        try {
            // Пробуем найти звук с именем "alarm" в папке res/raw/
            val soundId = resources.getIdentifier("alarm", "raw", packageName)

            if (soundId != 0) {
                // Если нашли свой звук - используем его
                mediaPlayer = MediaPlayer.create(this, soundId)
            } else {
                // Если нет - берём стандартный звук будильника системы
                mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
            }

            mediaPlayer?.isLooping = true   // Зацикливаем звук (повторять пока не выключат)
            mediaPlayer?.start()            // Начинаем проигрывание
        } catch (e: Exception) {
            // Если что-то пошло не так - используем системный звук как запасной вариант
            mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }

        // 3. ЗАПУСКАЕМ ВИБРАЦИЮ
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Для Android 8+ (более современный API)
            // createWaveform(массив_длительностей, повтор)
            // [500, 500, 500, 500] = вибрировать 500мс, пауза 500мс, вибрировать 500мс...
            // 0 = повторять с начала бесконечно
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(500, 500, 500, 500), 0))
        } else {
            // Для старых версий Android
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(500, 500, 500, 500), 0)  // 0 = повторять бесконечно
        }

        // START_STICKY - если сервис убьют, система попробует перезапустить его
        return START_STICKY
    }

    // Метод для создания канала уведомления (обязательно для Android 8+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Создаём канал с ID "alarm_sound"
            val channel = NotificationChannel(
                "alarm_sound",                    // ID канала (должен совпадать с уведомлением)
                "Звук будильника",                // Имя канала (видит пользователь)
                NotificationManager.IMPORTANCE_HIGH  // Высокий приоритет (важное уведомление)
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)  // Регистрируем канал в системе
        }
    }

    // Создаём само уведомление, которое будет висеть в шторке
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "alarm_sound")  // Указываем ID канала
            .setContentTitle("🔔 Будильник")        // Заголовок уведомления
            .setContentText("Звучит будильник")     // Текст уведомления
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // Иконка будильника
            .setPriority(NotificationCompat.PRIORITY_MAX)  // Максимальный приоритет
            .build()
    }

    // Публичный метод для остановки будильника (вызывается из AlarmShakeActivity)
    fun stopAlarm() {
        // Останавливаем и освобождаем MediaPlayer
        mediaPlayer?.apply {
            if (isPlaying) stop()  // Если играет - остановить
            release()              // Освободить ресурсы (важно для памяти)
        }
        mediaPlayer = null          // Обнуляем ссылку

        vibrator.cancel()          // Останавливаем вибрацию
        stopSelf()                 // Останавливаем сам сервис
    }

    // Вызывается при уничтожении сервиса
    override fun onDestroy() {
        super.onDestroy()
        // Освобождаем ресурсы, чтобы не было утечек памяти
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Для этого типа сервиса не нужно привязываться к Activity (не используем)
    override fun onBind(intent: Intent?): IBinder? = null
}