package com.cjiejie.simplecountdown

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class CountdownService : Service() {

    companion object {
        const val CHANNEL_ID = "CountdownChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.cjiejie.simplecountdown.ACTION_START"
        const val ACTION_STOP = "com.cjiejie.simplecountdown.ACTION_STOP"
        const val ACTION_STOP_ALARM = "com.cjiejie.simplecountdown.ACTION_STOP_ALARM"
        const val ACTION_TICK = "com.cjiejie.simplecountdown.ACTION_TICK"
        const val ACTION_FINISH = "com.cjiejie.simplecountdown.ACTION_FINISH"
        const val ACTION_QUERY_STATUS = "com.cjiejie.simplecountdown.ACTION_QUERY_STATUS"
        const val ACTION_STATUS_RESULT = "com.cjiejie.simplecountdown.ACTION_STATUS_RESULT"
        const val EXTRA_TIME = "com.cjiejie.simplecountdown.EXTRA_TIME"
        const val EXTRA_TIME_REMAINING = "com.cjiejie.simplecountdown.EXTRA_TIME_REMAINING"
        const val EXTRA_IS_RUNNING = "com.cjiejie.simplecountdown.EXTRA_IS_RUNNING"
    }

    private var countDownTimer: CountDownTimer? = null
    private var ringtone: Ringtone? = null
    private var isRunning = false
    private var timeRemaining = 0L
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val timeInSeconds = intent.getLongExtra(EXTRA_TIME, 0)
                startTimer(timeInSeconds * 1000)
            }
            ACTION_STOP -> {
                stopTimer()
            }
            ACTION_STOP_ALARM -> {
                stopTimer()
            }
            ACTION_QUERY_STATUS -> {
                sendStatus()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(timeInMillis: Long) {
        countDownTimer?.cancel()
        isRunning = true
        
        // 创建初始通知
        val notification = createNotification("倒计时开始", false)
        startForeground(NOTIFICATION_ID, notification)

        countDownTimer = object : CountDownTimer(timeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                // 更新通知
                val timeText = formatTime(millisUntilFinished)
                updateNotification("剩余时间: $timeText", false)
                
                // 发送广播给 Activity 更新 UI
                val intent = Intent(ACTION_TICK)
                intent.setPackage(packageName) // 明确指定包名，确保安全性和接收器能收到
                intent.putExtra(EXTRA_TIME_REMAINING, millisUntilFinished)
                sendBroadcast(intent)
            }

            override fun onFinish() {
                timeRemaining = 0
                isRunning = false
                updateNotification("倒计时结束！", true)
                playAlarm()
                
                // 发送广播给 Activity
                val intent = Intent(ACTION_FINISH)
                intent.setPackage(packageName) // 明确指定包名
                sendBroadcast(intent)
                
                stopForeground(false) // 保持通知显示，直到用户处理
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        isRunning = false
        stopAlarm()
        stopForeground(true)
        stopSelf()
    }

    private fun sendStatus() {
        val intent = Intent(ACTION_STATUS_RESULT)
        intent.setPackage(packageName) // 明确指定包名
        intent.putExtra(EXTRA_IS_RUNNING, isRunning)
        intent.putExtra(EXTRA_TIME_REMAINING, timeRemaining)
        sendBroadcast(intent)
    }


    private fun playAlarm() {
        try {
            // 1. Play Sound
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.play()
            
            // 2. Vibrate
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 500, 500) // wait 0ms, vibrate 500ms, sleep 500ms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 0 means repeat indefinitely
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        if (ringtone != null && ringtone!!.isPlaying) {
            ringtone?.stop()
        }
        vibrator?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "倒计时服务",
                NotificationManager.IMPORTANCE_LOW // LOW 避免每次更新都发出声音/震动
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String, showAction: Boolean): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("倒计时")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // 使用系统图标，实际项目中应替换为 app 图标
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // 避免每次更新都打扰用户

        if (showAction) {
            val stopIntent = Intent(this, CountdownService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_media_pause, "关闭闹钟", stopPendingIntent)
            builder.setOngoing(true) // 让通知常驻，防止误删
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String, showAction: Boolean) {
        val notification = createNotification(contentText, showAction)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        stopAlarm()
    }
}
