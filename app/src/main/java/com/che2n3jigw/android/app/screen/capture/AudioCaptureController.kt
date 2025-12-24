package com.che2n3jigw.android.app.screen.capture

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * 系统音频采集
 */
class AudioCaptureController(context: Context) {
    companion object {
        private const val TAG = "AudioCaptureController"
    }

    // 上下文弱引用
    private val weakReference = WeakReference(context)

    // 数据写入任务
    private var dataWriteJob: Job? = null

    // 文件输出流
    private var pcmOutputStream: FileOutputStream? = null

    // 输出文件
    private var pcmFile: File? = null

    // 是否正在运行标识
    @Volatile
    private var isRunning = false

    // 音频录制
    private var audioRecord: AudioRecord? = null


    @RequiresApi(Build.VERSION_CODES.Q)
    fun start(projection: MediaProjection) {
        if (isRunning) return

        // 1. 创建捕获配置（基于用户授权的 MediaProjection）
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            // 普通媒体音频（音乐、视频等）
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            // 游戏音频
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            // 未明确标记用途的音频
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        // 2. 创建音频格式
        val audioFormat = AudioFormat.Builder()
            // 采样率：44100Hz（CD 标准，兼容性最好）
            .setSampleRate(44100)
            // 立体声输入
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            // PCM 16bit 编码（最常用、最通用）
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        // 3. 创建 AudioRecord
        val permission = Manifest.permission.RECORD_AUDIO
        val ctx = weakReference.get() ?: return
        if (ActivityCompat.checkSelfPermission(ctx, permission) != 0) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return
        }
        val bufferSizeInBytes = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2
        audioRecord = AudioRecord.Builder()
            // 设置系统播放音频捕获配置（基于 MediaProjection）
            .setAudioPlaybackCaptureConfig(config)
            // 设置音频格式
            .setAudioFormat(audioFormat)
            // 设置缓冲区大小（必须 >= 最小缓冲区）
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build()

        // 开始录制
        audioRecord?.startRecording()
        isRunning = true

        dataWriteJob?.cancel()
        dataWriteJob = CoroutineScope(Dispatchers.IO).launch {
            val file = ctx.getExternalFilesDir(null)
            val child = "audio_capture_${System.currentTimeMillis()}.pcm"
            pcmFile = File(file, child)
            pcmOutputStream = FileOutputStream(pcmFile)

            val buffer = ByteArray(bufferSizeInBytes)
            try {
                while (isActive && isRunning) {
                    val record = audioRecord ?: return@launch
                    // 阻塞读取 PCM 数据
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        // 直接写二进制数据
                        pcmOutputStream?.write(buffer, 0, readBytes)
                    } else {
                        Log.w(TAG, "Audio record job error: $readBytes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error closing PCM file", e)
            } finally {
                try {
                    pcmOutputStream?.flush()
                    pcmOutputStream?.close()
                    Log.d(TAG, "PCM file closed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing PCM file", e)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        dataWriteJob?.cancel()
        dataWriteJob = null
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }
}