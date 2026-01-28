package com.che2n3jigw.android.app.screen.capture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat

/**
 * 屏幕采集前台服务
 */
class ScreenCaptureForegroundService : Service() {

    companion object {
        const val EXTRA_DATA = "media_projection"
        const val EXTRA_CODE = "result_code"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DENSITY = "density"
        private const val TAG = "ScreenService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 100
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenRecorder: ScreenRecorder? = null
    private var audioCaptureController: AudioCaptureController? = null
    private var mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w(TAG, "MediaProjection session stopped by user.")
            stopSelf() // 停止服务
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_STICKY

        if (screenRecorder != null) {
            Log.w(TAG, "Recording is already in progress.")
            return START_STICKY
        }

        // --- 1. 获取参数 ---
        val width = intent.getIntExtra(EXTRA_WIDTH, 720)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 1280)
        val screenDensity = intent.getIntExtra(EXTRA_DENSITY, 0)
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)

        if (data == null || screenDensity == 0) {
            Log.e(TAG, "Invalid intent data for screen capture.")
            stopSelf()
            return START_STICKY
        }

        // 创建通知
        val notification = createNotification()
        // 不需要判断权限,因为是从屏幕采集进来的
        @SuppressLint("MissingPermission")
        // 发送通知
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)

        // 媒体投屏管理
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        if (null == manager) {
            Log.w(TAG, "get media projection manager failed")
            return START_STICKY
        }

        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }

        // 获取媒体投影
        mediaProjection = manager.getMediaProjection(code, data)
        if (null == mediaProjection) {
            Log.w(TAG, "get media projection failed")
            return START_STICKY
        }

        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        // --- 4. 初始化和准备 ScreenRecorder ---
        audioCaptureController = AudioCaptureController(this)
        try {
            screenRecorder = ScreenRecorder(this, width, height)
            screenRecorder?.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare ScreenRecorder", e)
            releaseResources()
            return START_STICKY
        }

        // --- 5. 创建 VirtualDisplay ---
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            screenRecorder?.surface, // 从 ScreenRecorder 获取 Surface
            null,
            null
        )

        // --- 6. 开始录制 ---
        screenRecorder?.start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioCaptureController?.start(mediaProjection)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        val name = "前台服务"
        val description = "屏幕采集的前台服务"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, importance).apply {
            setName(name)
            setDescription(description)
        }.build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // 图标
            .setSmallIcon(R.mipmap.ic_launcher)
            // 标题
            .setContentTitle("屏幕采集中")
            // 重要性
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // 点击后自动移除
            .setAutoCancel(false)

        return builder.build()
    }

    /**
     * 统一的资源释放方法。
     */
    private fun releaseResources() {
        audioCaptureController?.stop()
        audioCaptureController = null

        // 按相反的顺序释放资源：recorder -> virtualDisplay -> projection
        screenRecorder?.stop()
        screenRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        // 注销回调并停止 projection
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        Log.d(TAG, "All resources released.")
        stopSelf() // 确保服务停止
    }
}