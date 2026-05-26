package com.ascese.urwallpaper

import android.content.Context
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var exoPlayer: ExoPlayer? = null
        private var lastTapTime: Long = 0
        private var isMuted = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // THIS is where the touch permission must be enabled
            setTouchEventsEnabled(true)

            exoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            exoPlayer?.setVideoSurface(holder.surface)

            val sharedPrefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            val savedUriString = sharedPrefs.getString("video_uri", null)
            val playAudio = sharedPrefs.getBoolean("play_sound", false)
            val speed = sharedPrefs.getFloat("playback_speed", 1.0f)

            isMuted = !playAudio
            exoPlayer?.volume = if (isMuted) 0f else 1f
            exoPlayer?.setPlaybackSpeed(speed)

            if (savedUriString != null) {
                exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(savedUriString)))
                exoPlayer?.prepare()
                exoPlayer?.play()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) {
                    isMuted = !isMuted
                    exoPlayer?.volume = if (isMuted) 0f else 1f
                    lastTapTime = 0
                } else {
                    lastTapTime = currentTime
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) exoPlayer?.play() else exoPlayer?.pause()
        }

        override fun onDestroy() {
            super.onDestroy()
            exoPlayer?.release()
            exoPlayer = null
        }
    }
}