// =================================================
// 作者：che2n3jigw
// 邮箱: che2n3jigw@163.com
// 博客: che2n3jigw.github.io
// 日期：2025/12/24 17:30  
// =================================================
package com.che2n3jigw.android.app.screen.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException

/**
 * 封装 MediaRecorder 逻辑的屏幕录制器类。
 *
 * @param context   上下文对象，用于 API 31+ 的 MediaRecorder 构造函数。
 * @param width     视频宽度。
 * @param height    视频高度。
 */
class ScreenRecorder(
    private val context: Context,
    private val width: Int,
    private val height: Int
) {
    private var mediaRecorder: MediaRecorder? = null
    private var _surface: Surface? = null

    // 提供一个公开的、只读的 Surface 属性
    val surface: Surface?
        get() = _surface

    init {
        // 在构造时就初始化 MediaRecorder
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    /**
     * 准备录制器，但不开始。
     * 此方法配置所有参数并调用 prepare()。
     * @throws IOException 如果 prepare() 或文件操作失败。
     */
    @Throws(IOException::class)
    fun prepare() {
        mediaRecorder?.apply {
            // 如果需要录音，请取消下面的注释并确保有 RECORD_AUDIO 权限
            // setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            val file = File(
                // 使用 context 来获取外部文件目录，更健壮
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "screen-capture-${System.currentTimeMillis()}.mp4"
            )
            setOutputFile(file.absolutePath)
            Log.d(TAG, "Output file: ${file.absolutePath}")

            setVideoSize(width, height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            // 如果需要录音
            // setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(5 * 1024 * 1024) // 5 Mbps
            setVideoFrameRate(10)

            prepare()
            _surface = this.surface
        }
    }

    /**
     * 开始录制。
     * 必须在 prepare() 成功调用后才能调用此方法。
     */
    fun start() {
        try {
            mediaRecorder?.start()
            Log.d(TAG, "ScreenRecorder started.")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start MediaRecorder. Did you call prepare()?", e)
        }
    }

    /**
     * 停止录制并释放所有相关资源。
     */
    fun stop() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            Log.d(TAG, "ScreenRecorder stopped and released.")
        } catch (e: Exception) {
            // 在已经停止或从未开始时调用 stop() 可能抛出异常
            Log.e(TAG, "Error stopping or releasing MediaRecorder", e)
        } finally {
            mediaRecorder = null
            _surface?.release()
            _surface = null
        }
    }

    companion object {
        private const val TAG = "ScreenRecorder"
    }
}