package com.k2fsa.sherpa.ncnn

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@SuppressLint("MissingPermission")
class SherpaHelper(val context: Context) {
    companion object {
        private const val TAG = "SherpaHelper"
    }
    private var model: SherpaNcnn
    private var audioRecord: AudioRecord? = null
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    @Volatile
    private var isRecording: Boolean = false
    private var recordJob: Job? = null
    private var results: MutableList<String> = mutableListOf()

    @Volatile
    private var modelReady: Boolean = false

    init {
        // Initialize model with 1 thread for emulator stability
        model = SherpaNcnn(
            assetManager = context.assets,
            modelConfig = getModelConfig(type = 1, useGPU = false)!!,
            decoderConfig = getDecoderConfig(enableEndpoint = true),
            fbankConfig = getFbankConfig(),
        )
        modelReady = true
    }

    private fun initAudioRecord(): Boolean {
        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) return true

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        val sources = listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION)

        for (source in sources) {
            try {
                val record = AudioRecord(source, sampleRateInHz, channelConfig, audioFormat, minBufferSize * 10)
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    Log.d(TAG, "AudioRecord initialized with source: $source")
                    return true
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Failed source $source: ${e.message}")
            }
        }
        return false
    }

    fun startRecord() {
        if (isRecording) return
        if (!modelReady) {
            Log.e(TAG, "Model not ready yet, cannot start recording")
            return
        }
        if (!initAudioRecord()) {
            Log.e(TAG, "AudioRecord init failed")
            return
        }

        results.clear()
        model.reset()
        isRecording = true
        
        try {
            audioRecord?.startRecording()
            recordJob = CoroutineScope(Dispatchers.IO).launch { processSamples() }
            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording error", e)
            isRecording = false
        }
    }

    private fun processSamples() {
        val buffer = ShortArray(1600) // 100ms buffer for stability
        var maxAmp = 0
        while (isRecording) {
            val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (ret > 0) {
                for (i in 0 until ret) {
                    val a = abs(buffer[i].toInt())
                    if (a > maxAmp) maxAmp = a
                }
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                model.decodeSamples(samples)
                val text = model.text
                if (text.isNotBlank()) {
                    if (results.isEmpty()) results.add("")
                    results[results.size - 1] = text
                    if (model.isEndpoint()) {
                        results.add("")
                        model.reset()
                    }
                }
            }
        }
        Log.d(TAG, "Recording loop ended. Max amplitude: $maxAmp")
    }

    fun stopRecord(recognizeCallback: (result: String) -> Unit) {
        if (!isRecording) {
            recognizeCallback("")
            return
        }
        isRecording = false
        CoroutineScope(Dispatchers.IO).launch {
            delay(400) // Wait longer for native buffers to flush
            try { audioRecord?.stop() } catch (e: Exception) {}
            recordJob?.join()
            
            model.inputFinished()
            // Give decoder extra time to process tail frames
            delay(200)
            val lastText = model.text
            if (lastText.isNotBlank()) {
                if (results.isEmpty()) results.add("")
                results[results.size - 1] = lastText
            }
            
            val finalResult = results.filter { it.isNotBlank() }.joinToString(" ").trim()
            Log.d(TAG, "Final Result: '$finalResult'")
            withContext(Dispatchers.Main) { recognizeCallback(finalResult) }
        }
    }

    fun releaseRecord() {
        isRecording = false
        audioRecord?.release()
        audioRecord = null
    }
}
