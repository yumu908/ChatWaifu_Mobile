package com.k2fsa.sherpa.ncnn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Description: Sherpa Remote Service
 */
class SherpaService : Service() {
    companion object {
        private const val TAG  = "SherpaService"
    }
    
    @Volatile
    private var sherpaHelper: SherpaHelper? = null
    private val initMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        // Proactively initialize helper when service starts
        initHelperAsync()
    }

    private fun initHelperAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            initMutex.withLock {
                if (sherpaHelper == null) {
                    try {
                        Log.d(TAG, "Initializing SherpaHelper...")
                        sherpaHelper = SherpaHelper(this@SherpaService)
                        Log.d(TAG, "SherpaHelper initialized successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize SherpaHelper", e)
                    }
                }
            }
        }
    }

    private val binder: ISherpaAidlInterface.Stub = object : ISherpaAidlInterface.Stub(){
        override fun initSherpa(){
            Log.d(TAG, "initSherpa AIDL call received")
            if (sherpaHelper == null) {
                initHelperAsync()
            }
        }

        override fun startRecord() {
            Log.d(TAG, "startRecord AIDL call")
            val helper = sherpaHelper
            if (helper != null) {
                helper.startRecord()
            } else {
                Log.e(TAG, "Cannot start record: model is still loading, please wait a moment and try again")
                // Try to recover for next time
                initHelperAsync()
            }
        }

        override fun finishRecord(callback: ISherpaResultAidlCallback?) {
            Log.d(TAG, "finishRecord AIDL call")
            val helper = sherpaHelper
            if (helper == null) {
                Log.e(TAG, "Cannot finish record: sherpaHelper is null")
                try {
                    callback?.onResult("")
                } catch (e: Exception) {
                    Log.e(TAG, "Callback error", e)
                }
                return
            }
            helper.stopRecord { result ->
                Log.d(TAG, "Recognition finished with result: $result")
                try {
                    callback?.onResult(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback error", e)
                }
            }
        }
    }

    override fun onDestroy() {
        sherpaHelper?.releaseRecord()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder
}
