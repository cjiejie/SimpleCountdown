package com.cjiejie.simplecountdown

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etTimeInput: EditText
    private lateinit var tvCountdown: TextView
    private lateinit var btnAction: Button

    private var isTimerRunning = false

    private val countdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CountdownService.ACTION_TICK -> {
                    val millisUntilFinished = intent.getLongExtra(CountdownService.EXTRA_TIME_REMAINING, 0)
                    updateCountDownText(millisUntilFinished)
                    isTimerRunning = true
                    updateButtons()
                }
                CountdownService.ACTION_FINISH -> {
                    isTimerRunning = false
                    updateButtons()
                    tvCountdown.text = "00:00"
                    triggerAlarm()
                }
                CountdownService.ACTION_STATUS_RESULT -> {
                    val isRunning = intent.getBooleanExtra(CountdownService.EXTRA_IS_RUNNING, false)
                    val millisUntilFinished = intent.getLongExtra(CountdownService.EXTRA_TIME_REMAINING, 0)
                    
                    isTimerRunning = isRunning
                    if (isRunning) {
                        updateCountDownText(millisUntilFinished)
                    } else if (millisUntilFinished == 0L && tvCountdown.text != "00:00") {
                        // If finished but we missed the broadcast (Activity was closed), just show 00:00
                        tvCountdown.text = "00:00"
                    }
                    updateButtons()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etTimeInput = findViewById(R.id.etTimeInput)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnAction = findViewById(R.id.btnAction)

        loadLastTime()

        btnAction.setOnClickListener {
            if (isTimerRunning) {
                stopTimer()
            } else {
                startTimer()
            }
        }
        
        checkNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(CountdownService.ACTION_TICK)
            addAction(CountdownService.ACTION_FINISH)
            addAction(CountdownService.ACTION_STATUS_RESULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(countdownReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(countdownReceiver, filter)
        }
        
        // Query status in case service is already running
        val serviceIntent = Intent(this, CountdownService::class.java).apply {
            action = CountdownService.ACTION_QUERY_STATUS
        }
        startService(serviceIntent)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(countdownReceiver)
    }

    private fun startTimer() {
        val input = etTimeInput.text.toString()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入时间", Toast.LENGTH_SHORT).show()
            return
        }

        val timeInSeconds = input.toLongOrNull() ?: 0
        val timeInMillis = timeInSeconds * 1000

        if (timeInMillis == 0L) {
            Toast.makeText(this, "时间不能为0", Toast.LENGTH_SHORT).show()
            return
        }

        saveLastTime(input)

        val serviceIntent = Intent(this, CountdownService::class.java).apply {
            action = CountdownService.ACTION_START
            putExtra(CountdownService.EXTRA_TIME, timeInSeconds)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isTimerRunning = true
        updateButtons()
    }

    private fun stopTimer() {
        val serviceIntent = Intent(this, CountdownService::class.java).apply {
            action = CountdownService.ACTION_STOP
        }
        startService(serviceIntent)
        
        isTimerRunning = false
        updateButtons()
        tvCountdown.text = "00:00"
    }

    private fun updateCountDownText(millisUntilFinished: Long) {
        val minutes = (millisUntilFinished / 1000) / 60
        val seconds = (millisUntilFinished / 1000) % 60
        tvCountdown.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateButtons() {
        if (isTimerRunning) {
            btnAction.text = "停止"
            etTimeInput.isEnabled = false
        } else {
            btnAction.text = "开始倒计时"
            etTimeInput.isEnabled = true
        }
    }

    private fun saveLastTime(time: String) {
        val sharedPref = getSharedPreferences("CountdownPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("LAST_TIME", time)
            apply()
        }
    }

    private fun loadLastTime() {
        val sharedPref = getSharedPreferences("CountdownPrefs", Context.MODE_PRIVATE)
        val lastTime = sharedPref.getString("LAST_TIME", "")
        if (!lastTime.isNullOrEmpty()) {
            etTimeInput.setText(lastTime)
            etTimeInput.setSelection(lastTime.length)
        }
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    private fun triggerAlarm() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_alarm, null)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        builder.setCancelable(false)

        val dialog = builder.create()
        
        // 设置背景透明，以便显示圆角
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // 尝试设置背景模糊 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            dialog.window?.attributes?.blurBehindRadius = 60
        }
        
        dialog.show()

        val handler = Handler(Looper.getMainLooper())
        val autoCloseRunnable = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
                Toast.makeText(this, "闹钟已自动关闭", Toast.LENGTH_SHORT).show()
            }
        }

        handler.postDelayed(autoCloseRunnable, 10000)

        // 按钮点击事件
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            // When dialog dismissed, stop the sound in Service
            val serviceIntent = Intent(this, CountdownService::class.java).apply {
                action = CountdownService.ACTION_STOP
            }
            startService(serviceIntent)
            
            handler.removeCallbacks(autoCloseRunnable)
            
            isTimerRunning = false
            updateButtons()
        }
    }
}
