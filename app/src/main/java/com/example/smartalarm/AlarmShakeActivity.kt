package com.example.smartalarm

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

// Activity для отключения будильника через тряску телефона
class AlarmShakeActivity : AppCompatActivity(), SensorEventListener {

    // --- ОБЪЯВЛЕНИЕ ПЕРЕМЕННЫХ ---
    private lateinit var sensorManager: SensorManager  // Менеджер датчиков
    private var accelerometer: Sensor? = null         // Акселерометр (датчик движения)
    private lateinit var vibrator: Vibrator           // Для вибрации при каждом встряхивании
    private lateinit var tvMessage: TextView          // Текст с инструкцией
    private lateinit var tvShakeCount: TextView       // Счётчик трясок

    // Настройки чувствительности тряски
    private val shakeThreshold = 15f      // Минимальная сила тряски (чем меньше, тем чувствительнее)
    private var lastShakeTime = 0L        // Время последней тряски (для задержки)
    private val shakeCooldownMs = 500L    // Задержка между трясками (0.5 сек)
    private var shakeCount = 0             // Счётчик успешных трясок
    private val requiredShakes = 10       // Сколько нужно потрясти для выключения

    // --- ЖИЗНЕННЫЙ ЦИКЛ АКТИВИТИ ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_shake)

        // 1. ВКЛЮЧАЕМ ЭКРАН (даже если телефон заблокирован)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Для Android 8+ (новый способ)
            setShowWhenLocked(true)      // Показывать когда заблокирован
            setTurnScreenOn(true)        // Включить экран
        } else {
            // Для старых версий Android
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }

        // 2. НАХОДИМ ЭЛЕМЕНТЫ ИНТЕРФЕЙСА
        tvMessage = findViewById(R.id.tv_message)
        tvShakeCount = findViewById(R.id.tv_shake_count)

        // 3. НАСТРАИВАЕМ КНОПКУ РУЧНОГО ВЫКЛЮЧЕНИЯ
        val btnDismiss = findViewById<Button>(R.id.btn_dismiss)
        btnDismiss.setOnClickListener {
            dismissAlarm()  // Выключить будильник вручную
        }

        // 4. НАСТРАИВАЕМ ВИБРАЦИЮ
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // 5. НАСТРАИВАЕМ ДАТЧИК ТРЯСКИ (акселерометр)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 6. ВЫВОДИМ НАЧАЛЬНЫЙ ТЕКСТ И УВЕДОМЛЕНИЕ
        tvMessage.text = "Доброе утро!\nПотряси телефон $requiredShakes раз"
        updateShakeDisplay()  // Показываем счётчик "0/10"

        Toast.makeText(this, "ТРЯСИТЕ ТЕЛЕФОН, ЧТОБЫ ВЫКЛЮЧИТЬ!", Toast.LENGTH_LONG).show()
    }

    // Обновление текста счётчика на экране
    private fun updateShakeDisplay() {
        tvShakeCount.text = "Тряска: $shakeCount / $requiredShakes"
    }

    // Когда Activity становится видимой - включаем слежение за датчиками
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            // this - слушатель событий (сама Activity)
            // it - датчик акселерометра
            // SENSOR_DELAY_UI - частота обновления (подходит для UI)
        }
    }

    // Когда Activity уходит на фон - отключаем датчик (экономия батареи)
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)  // Отключаем слушатель
    }

    // --- ОБРАБОТКА СОБЫТИЙ ДАТЧИКА ---
    // Вызывается при каждом изменении показаний датчика (много раз в секунду)
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Получаем ускорение по трём осям (X, Y, Z)
            val x = event.values[0]  // Влево-вправо
            val y = event.values[1]  // Вперёд-назад
            val z = event.values[2]  // Вверх-вниз (гравитация)

            // Вычисляем общую силу ускорения (теорема Пифагора в 3D)
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            val now = System.currentTimeMillis()  // Текущее время в миллисекундах

            // Если тряска сильная И прошло достаточно времени с прошлой тряски
            if (acceleration > shakeThreshold && now - lastShakeTime > shakeCooldownMs) {
                lastShakeTime = now    // Запоминаем время этой тряски
                onShakeDetected()      // Обрабатываем тряску
            }
        }
    }

    // Что делаем при обнаружении тряски
    private fun onShakeDetected() {
        shakeCount++  // Увеличиваем счётчик

        // ВИБРАЦИЯ в ответ на тряску (100 миллисекунд)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Для Android 8+ (новый API)
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Для старых версий
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }

        updateShakeDisplay()  // Обновляем счётчик на экране

        // Если потрясли нужное количество раз - выключаем будильник
        if (shakeCount >= requiredShakes) {
            tvMessage.text = "🎉 МОЛОДЕЦ! ВЫКЛЮЧАЮ... 🎉"
            dismissAlarm()  // Функция выключения
        }
    }

    // --- ВЫКЛЮЧЕНИЕ БУДИЛЬНИКА ---
    private fun dismissAlarm() {
        // Останавливаем сервис, который проигрывает звук будильника
        val soundIntent = Intent(this, AlarmSoundService::class.java)
        stopService(soundIntent)  // Останавливаем фоновую музыку

        vibrator.cancel()  // Останавливаем вибрацию (если была)
        finish()  // Закрываем это Activity (возвращаемся на экран блокировки)
    }

    // Вызывается при изменении точности датчика (не нужно, но интерфейс требует)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Когда Activity закрывается (например, системой) - всё равно выключаем звук
    override fun onDestroy() {
        super.onDestroy()
        val soundIntent = Intent(this, AlarmSoundService::class.java)
        stopService(soundIntent)  // Защита от "вечного" звука
    }

    // Блокируем кнопку "Назад" - чтобы нельзя было случайно выйти
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Пустое тело = ничего не делаем, кнопка "Назад" не работает
        return
    }
}