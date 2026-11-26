package com.tikigsaver.app

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EngineInitState {
    data object Loading : EngineInitState
    data object Ready : EngineInitState
    data class Error(val message: String) : EngineInitState
}

class TikIGApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineState = MutableStateFlow<EngineInitState>(EngineInitState.Loading)
    val engineState: StateFlow<EngineInitState> = _engineState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        initEngine()
    }

    fun initEngine() {
        _engineState.value = EngineInitState.Loading
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@TikIGApp)
                FFmpeg.getInstance().init(this@TikIGApp)
                _engineState.value = EngineInitState.Ready
            } catch (e: Exception) {
                Log.e("TikIGApp", "Failed to initialize yt-dlp/ffmpeg", e)
                _engineState.value = EngineInitState.Error(
                    e.message ?: "Gagal menginisialisasi engine yt-dlp / FFmpeg"
                )
            }
        }
    }

    companion object {
        lateinit var instance: TikIGApp
            private set
    }
}
