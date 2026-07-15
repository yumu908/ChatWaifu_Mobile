package com.chatwaifu.mobile.ui.setting

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class SettingFragmentViewModel: ViewModel() {
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    var settingUIState: SettingUIState? by mutableStateOf(null)
        private set

    fun getOrInitState(context: Context): SettingUIState {
        if (settingUIState == null) {
            settingUIState = SettingUIState(loadInitData(context))
        }
        return settingUIState!!
    }

    fun loadInitData(context: Context): SettingUIData {
        val data = SettingUIData()
        sp.getString(Constant.SAVED_CHAT_KEY, null)?.let {
            data.chatGPTAppId = it
        }

        data.yuukaSetting = sp.getString(Constant.SAVED_YUUKA_SETTING, null)
            ?: context.resources.getString(R.string.default_system_yuuka)

        data.amadeusSetting = sp.getString(Constant.SAVED_AMADEUS_SETTING, null)
            ?: context.resources.getString(R.string.default_system_amadeus)

        data.atriSetting = sp.getString(Constant.SAVED_ATRI_SETTING, null)
            ?: context.resources.getString(R.string.default_system_atri)

        sp.getString(Constant.SAVED_EXTERNAL_SETTING, null)?.let {
            data.externalSetting = it
        }

        sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true).let {
            data.translateSwitch = it
        }

        sp.getBoolean(Constant.SAVED_USE_DARKMODE, false).let {
            data.darkModeSwitch = it
        }
        sp.getBoolean(Constant.SAVED_USE_CHATGPT_PROXY, false).let {
            data.gptProxySwitch = it
        }
        sp.getString(Constant.SAVED_USE_CHATGPT_PROXY_URL, null)?.let {
            data.gptProxyUrl = it
        }
        data.vitsModelType = sp.getInt("saved_vits_model_type", 1)
        data.externalModelSpeakerId = sp.getInt(Constant.SAVED_EXTERNAL_MODEL_SPEAKER_ID, 0)
        data.yuukaVitsPath = sp.getString(Constant.SAVED_OVERRIDE_VITS_PATH + "_Yuuka", "") ?: ""
        data.atriVitsPath = sp.getString(Constant.SAVED_OVERRIDE_VITS_PATH + "_ATRI", "") ?: ""
        data.amadeusVitsPath = sp.getString(Constant.SAVED_OVERRIDE_VITS_PATH + "_Amadeus", "") ?: ""
        data.voiceScale = sp.getFloat(Constant.SAVED_VOICE_SCALE, 1.0f)
        
        return data
    }

    fun saveData(saved: SettingUIData?) {
        saved ?: return
        sp.edit().apply {
            putString(Constant.SAVED_CHAT_KEY, saved.chatGPTAppId)
            putString(Constant.SAVED_YUUKA_SETTING, saved.yuukaSetting)
            putString(Constant.SAVED_AMADEUS_SETTING, saved.amadeusSetting)
            putString(Constant.SAVED_ATRI_SETTING, saved.atriSetting)
            putString(Constant.SAVED_EXTERNAL_SETTING, saved.externalSetting)
            putBoolean(Constant.SAVED_USE_TRANSLATE, saved.translateSwitch)
            putBoolean(Constant.SAVED_USE_DARKMODE, saved.darkModeSwitch)
            putInt(Constant.SAVED_EXTERNAL_MODEL_SPEAKER_ID, saved.externalModelSpeakerId)
            putBoolean(Constant.SAVED_USE_CHATGPT_PROXY, saved.gptProxySwitch)
            putString(Constant.SAVED_USE_CHATGPT_PROXY_URL, saved.gptProxyUrl)
            putInt("saved_vits_model_type", saved.vitsModelType)
            putString(Constant.SAVED_OVERRIDE_VITS_PATH + "_Yuuka", saved.yuukaVitsPath)
            putString(Constant.SAVED_OVERRIDE_VITS_PATH + "_ATRI", saved.atriVitsPath)
            putString(Constant.SAVED_OVERRIDE_VITS_PATH + "_Amadeus", saved.amadeusVitsPath)
            putFloat(Constant.SAVED_VOICE_SCALE, saved.voiceScale)
            apply()
        }
    }

    /**
     * 扫描已下载的 VITS 模型目录，返回 (显示名, 绝对路径) 列表
     * 支持选择文件夹内的具体 config_zh.json 或 config_ja.json
     */
    fun getAvailableVitsModels(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        // 先加一个 "跟随角色默认" 的选项
        result.add("跟随角色默认" to "")
        
        fun scanDir(vitsDir: File, isBuiltIn: Boolean) {
            if (vitsDir.exists() && vitsDir.isDirectory) {
                vitsDir.listFiles()?.filter { it.isDirectory }?.forEach { modelDir ->
                    if (modelDir.name.contains("\uFFFD") || modelDir.name.contains("?")) {
                        modelDir.deleteRecursively()
                        return@forEach
                    }
                    
                    val jsonFiles = modelDir.listFiles()?.filter { it.name.endsWith(".json", ignoreCase = true) } ?: emptyList()
                    val hasBin = modelDir.listFiles()?.any { it.name.endsWith(".bin", ignoreCase = true) } ?: false
                    
                    if (jsonFiles.isNotEmpty()) {
                        jsonFiles.forEach { jsonFile ->
                            val fileName = jsonFile.name.lowercase()
                            val langSuffix = when {
                                fileName.contains("_zh") -> " (中文)"
                                fileName.contains("_ja") || fileName.contains("_jp") -> " (日文)"
                                fileName == "config.json" -> "" // 默认 config 不显示后缀
                                else -> " (${jsonFile.name})"
                            }
                            val suffix = if (isBuiltIn) " (built-in)" else ""
                            val label = "${modelDir.name}$langSuffix$suffix"
                            
                            if (result.none { it.second == jsonFile.absolutePath }) {
                                result.add(label to jsonFile.absolutePath)
                            }
                        }
                    } else if (hasBin) {
                        // 如果没有 JSON 但有 BIN，显示文件夹路径（兼容旧逻辑）
                        val suffix = if (isBuiltIn) " (built-in)" else ""
                        val label = "${modelDir.name}$suffix"
                        if (result.none { it.second == modelDir.absolutePath }) {
                            result.add(label to modelDir.absolutePath)
                        }
                    }
                }
            }
        }

        // 扫描内部存储 filesDir/VITSModels
        val vitsDir = File(ChatWaifuApplication.context.filesDir, Constant.VITS_BASE_PATH)
        scanDir(vitsDir, false)
        
        // 扫描 baseAppDir/VITSModels (assets 复制出来的)
        val assetsVitsDir = File(ChatWaifuApplication.baseAppDir, Constant.VITS_BASE_PATH)
        if (assetsVitsDir.absolutePath != vitsDir.absolutePath) {
            scanDir(assetsVitsDir, true)
        }
        
        return result
    }

    private fun getModelName(type: Int): String {
        return when (type) {
            1 -> "nanoka"
            2 -> "anan"
            3 -> "arisa"
            4 -> "ema"
            5 -> "gokucho"
            6 -> "hanna"
            7 -> "hiro"
            8 -> "koko"
            9 -> "mago"
            10 -> "meruru"
            11 -> "miria"
            12 -> "noa"
            13 -> "reia"
            14 -> "sheri"
            else -> "nanoka"
        }
    }

    suspend fun downloadModel(type: Int, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val modelName = getModelName(type)
        // 兼容尝试：全小写和首字母大写
        val namesToTry = listOf(modelName, modelName.replaceFirstChar { it.uppercase() })
        
        var success = false
        for (name in namesToTry) {
            // Updated to GitHub releases URL as requested
            val downloadUrl = "https://github.com/yumu908/Vits-Android-ncnn/releases/download/1.0.0/${name}.zip"
            Log.d("Download", "Attempting download from GitHub: $downloadUrl")
            
            withContext(Dispatchers.Main) { onProgress(0.01f) }
            
            success = performDownloadOkHttp(downloadUrl, modelName, onProgress)
            if (success) break
        }
        
        if (!success) {
            withContext(Dispatchers.Main) {
                Toast.makeText(ChatWaifuApplication.context, "模型下载失败，请检查网络连接或尝试开启加速器", Toast.LENGTH_LONG).show()
            }
        }
        return@withContext success
    }

    private suspend fun performDownloadOkHttp(urlString: String, modelName: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        var zipFile: File? = null
        try {
            val requestBuilder = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
            
            if (urlString.contains("teracloud.jp")) {
                requestBuilder.header("Authorization", Credentials.basic("guest", "121239adfbdea2a7"))
            }
            
            val request = requestBuilder.build()
            
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val fileLength = body.contentLength()
            val inputStream = body.byteStream()
            val vitsDir = File(ChatWaifuApplication.context.filesDir, Constant.VITS_BASE_PATH)
            if (!vitsDir.exists()) vitsDir.mkdirs()
            
            zipFile = File(vitsDir, "${modelName}_temp.zip")
            val output = FileOutputStream(zipFile)
            
            val data = ByteArray(16384)
            var total = 0L
            var count: Int
            var lastUpdate = 0L
            
            while (inputStream.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdate > 100) {
                    val progress = if (fileLength > 0) total.toFloat() / fileLength else (total % 10000000).toFloat() / 10000000f
                    onProgress(progress.coerceIn(0.01f, 0.99f))
                    lastUpdate = currentTime
                }
            }
            
            output.flush()
            output.close()
            inputStream.close()
            
            val destDir = File(vitsDir, modelName)
            if (!destDir.exists()) destDir.mkdirs()
            unzip(zipFile, destDir)
            zipFile.delete()
            
            // 注意：这里不再删除或重命名 config_zh/ja.json，让用户在界面自行选择
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            zipFile?.delete()
            false
        }
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream()), Charset.forName("GBK")).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDirectory, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buffer = ByteArray(16384)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
