package com.example.smartalarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

// Главная Activity приложения - интерфейс для установки будильника
class MainActivity : AppCompatActivity() {

    // --- ОБЪЯВЛЕНИЕ ПЕРЕМЕННЫХ ---
    private lateinit var alarmManager: AlarmManager    // Системный сервис для будильников
    private lateinit var timePicker: TimePicker        // Виджет выбора времени (часы/минуты)
    private lateinit var tvStatus: TextView            // Текст статуса (когда установлен будильник)
    private lateinit var tvCurrentTime: TextView       // Текст с текущим временем

    // --- ЖИЗНЕННЫЙ ЦИКЛ АКТИВИТИ ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. НАХОДИМ ЭЛЕМЕНТЫ ИНТЕРФЕЙСА
        timePicker = findViewById(R.id.time_picker)
        tvStatus = findViewById(R.id.tv_status)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        val btnSetAlarm = findViewById<Button>(R.id.btn_set_alarm)

        // 2. ПОЛУЧАЕМ СИСТЕМНЫЙ СЕРВИС БУДИЛЬНИКОВ
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 3. ЗАПУСКАЕМ ОБНОВЛЕНИЕ ТЕКУЩЕГО ВРЕМЕНИ (каждую секунду)
        updateTimeDisplay()

        // 4. НАСТРАИВАЕМ КНОПКУ УСТАНОВКИ БУДИЛЬНИКА
        btnSetAlarm.setOnClickListener {
            setAlarm()  // Вызываем метод установки будильника
        }
    }

    // --- МЕТОД ДЛЯ ОТОБРАЖЕНИЯ ТЕКУЩЕГО ВРЕМЕНИ В РЕАЛЬНОМ ВРЕМЕНИ ---
    private fun updateTimeDisplay() {
        // Handler позволяет выполнять код с задержкой/повторением
        val handler = Handler(Looper.getMainLooper())  // Looper.getMainLooper() - главный поток UI

        // Создаём задачу для повторного выполнения
        val runnable = object : Runnable {
            override fun run() {
                // Получаем текущие дату и время
                val calendar = Calendar.getInstance()
                // Форматируем время как "ЧЧ:ММ:СС" (например, "14:25:30")
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                tvCurrentTime.text = timeFormat.format(calendar.time)

                // Запускаем задачу снова через 1000 миллисекунд (1 секунду)
                handler.postDelayed(this, 1000)
            }
        }

        // Запускаем задачу в первый раз
        handler.post(runnable)
    }

    // --- ГЛАВНЫЙ МЕТОД: УСТАНОВКА БУДИЛЬНИКА ---
    private fun setAlarm() {
        // 1. ПОЛУЧАЕМ ВЫБРАННОЕ ВРЕМЯ ИЗ TimePicker
        val calendar = Calendar.getInstance()

        // Получаем час (в зависимости от версии Android)
        val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour                    // Android 6+ (новый способ)
        } else {
            timePicker.currentHour             // Старые версии
        }

        // Получаем минуты
        val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.minute
        } else {
            timePicker.currentMinute
        }

        // Устанавливаем в календарь выбранное время
        calendar.set(Calendar.HOUR_OF_DAY, hour)   // Час (0-23)
        calendar.set(Calendar.MINUTE, minute)      // Минуты (0-59)
        calendar.set(Calendar.SECOND, 0)           // Секунды = 0 (ровно в эту минуту)

        // 2. ПРОВЕРЯЕМ: ВРЕМЯ УЖЕ ПРОШЛО ИЛИ НЕТ?
        var alarmTime = calendar.timeInMillis      // Время будильника в миллисекундах

        // Если выбранное время уже прошло сегодня - добавляем 24 часа (на завтра)
        if (alarmTime < System.currentTimeMillis()) {
            alarmTime += 24 * 60 * 60 * 1000       // Прибавляем миллисекунды в сутках
        }

        // 3. СОЗДАЁМ PendingIntent ДЛЯ ЗАПУСКА AlarmReceiver
        // Intent говорит: "запусти AlarmReceiver, когда настанет время"
        val intent = Intent(this, AlarmReceiver::class.java)

        // PendingIntent - "обёртка" для Intent, которая сработает в будущем
        // getBroadcast - потому что AlarmReceiver это BroadcastReceiver
        val pendingIntent = PendingIntent.getBroadcast(
            this,                                    // Контекст
            0,                                       // Код запроса (0 = уникальный)
            intent,                                  // Intent для запуска
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0  // Флаг для Android 12+
        )

        // 4. УСТАНАВЛИВАЕМ БУДИЛЬНИК В СИСТЕМЕ
        // Используем setAlarmClock - самый надёжный способ для всех версий Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Для Android 6+ (самый надёжный способ)
            // AlarmClockInfo - специальная информация для часов/будильника
            val alarmInfo = AlarmManager.AlarmClockInfo(alarmTime, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)  // Система покажет иконку часов
        } else {
            // Для старых версий Android
            // RTC_WAKEUP - будить телефон в указанное время
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        }

        // 5. ПОКАЗЫВАЕМ УВЕДОМЛЕНИЕ (подтверждение установки)
        showAlarmSetNotification(hour, minute)

        // 6. ОБНОВЛЯЕМ ИНТЕРФЕЙС
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvStatus.text = "Будильник установлен на ${dateFormat.format(Date(alarmTime))}"
        Toast.makeText(this, "Будильник установлен!", Toast.LENGTH_SHORT).show()
    }

    // --- МЕТОД ДЛЯ ПОКАЗА УВЕДОМЛЕНИЯ (ПОДТВЕРЖДЕНИЕ УСТАНОВКИ) ---
    private fun showAlarmSetNotification(hour: Int, minute: Int) {
        // 1. ПРОВЕРЯЕМ РАЗРЕШЕНИЕ НА УВЕДОМЛЕНИЯ (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // Android 13+
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                // Если разрешения нет - запрашиваем у пользователя
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                return  // Выходим, уведомление не покажем до получения разрешения
            }
        }

        // 2. СОЗДАЁМ КАНАЛ УВЕДОМЛЕНИЙ (Android 8+)
        val channelId = "alarm_channel"  // ID канала
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,                    // ID
                "Будильники",                // Название для пользователя
                NotificationManager.IMPORTANCE_HIGH  // Важный канал (звук/вибрация)
            ).apply {
                description = "Уведомления о установленных будильниках"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 3. ФОРМАТИРУЕМ ВРЕМЯ ДЛЯ ОТОБРАЖЕНИЯ
        val timeFormatted = String.format("%02d:%02d", hour, minute)  // Например, "08:30"

        // 4. СОЗДАЁМ УВЕДОМЛЕНИЕ
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // Иконка будильника
            .setContentTitle("⏰ Будильник установлен")
            .setContentText("Будильник установлен на $timeFormatted")
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // Высокий приоритет
            .setAutoCancel(true)  // Удалить при нажатии
            .build()

        // 5. ОТОБРАЖАЕМ УВЕДОМЛЕНИЕ (ID = 1)
        notificationManager.notify(1, notification)
    }
}