package com.che2n3jigw.android.app.screen.capture

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            // 1. 直接从 resources.displayMetrics 获取屏幕密度
            val density = resources.displayMetrics.densityDpi
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels

            // 3. 启动屏幕录制服务
            val intent = Intent(this, ScreenCaptureForegroundService::class.java)
            intent.putExtra(ScreenCaptureForegroundService.EXTRA_CODE, result.resultCode)
            intent.putExtra(ScreenCaptureForegroundService.EXTRA_DATA, data)
            intent.putExtra(ScreenCaptureForegroundService.EXTRA_WIDTH, width)
            intent.putExtra(ScreenCaptureForegroundService.EXTRA_HEIGHT, height)
            intent.putExtra(ScreenCaptureForegroundService.EXTRA_DENSITY, density)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initData()
        initListener()
    }

    private fun initView() {
    }

    private fun initListener() {
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startCapture()
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            val intent = Intent(this, ScreenCaptureForegroundService::class.java)
            stopService(intent)
        }
    }

    private fun initData() {
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun startCapture() {
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }
}