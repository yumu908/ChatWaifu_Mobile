package com.chatwaifu.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.chatgpt.ChatGPTNetService
import com.chatwaifu.chatgpt.ChatGPTResponseData
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.VITSLoadStatus
import com.chatwaifu.mobile.ui.channellist.ChannelListBean
import com.chatwaifu.mobile.ui.common.ChatDialogContentUIState
import com.chatwaifu.mobile.utils.AssistantMessageManager
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.mobile.utils.LocalModelManager
import com.chatwaifu.translate.ITranslate
import com.chatwaifu.translate.baidu.BaiduTranslateService
import com.chatwaifu.translate.bing.BingTranslateService
import com.chatwaifu.vits.utils.SoundGenerateHelper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ChatActivityViewModel : ViewModel() {
    companion object {
        private const val TAG = "ChatActivityViewModel"
    }

    enum class ChatStatus {
        DEFAULT,
        FETCH_INPUT,
        SEND_REQUEST,
        TRANSLATE,
        GENERATE_SOUND,
    }

    val drawerShouldBeOpened = MutableLiveData<Boolean>()
    val chatStatusLiveData = MutableLiveData<ChatStatus>().apply { value = ChatStatus.DEFAULT }

    private val _loadVITSModelLiveData = MutableSharedFlow<VITSLoadStatus>()
    val loadVITSModelLiveData = _loadVITSModelLiveData.asSharedFlow()
    private val _chatContentUIFlow = MutableSharedFlow<ChatDialogContentUIState>()
    val chatContentUIFlow = _chatContentUIFlow.asSharedFlow()

    val generateSoundLiveData = MutableLiveData<Boolean>()
    val initModelResultLiveData = MutableLiveData<List<ChannelListBean>>()
    val loadingUILiveData = MutableLiveData<Pair<Boolean, String>>()

    var currentLive2DModelPath: String = ""
    var currentLive2DModelName: String = ""
    var currentVITSModelName: String = ""
    var currentVITSModelPath: String = ""
    var needTranslate: Boolean = true
    var needChatGPTProxy: Boolean = false

    private var inputFunc: ((input: String) -> Unit)? = null
    private val chatGPTNetService: ChatGPTNetService? by lazy {
        ChatGPTNetService(ChatWaifuApplication.context)
    }
    private val vitsHelper: SoundGenerateHelper by lazy {
        SoundGenerateHelper(ChatWaifuApplication.context)
    }
    private val localModelManager: LocalModelManager by lazy {
        LocalModelManager()
    }
    val lipsValueHandler: LipsValueHandler by lazy {
        LipsValueHandler()
    }
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE
        )
    }
    private val assistantMsgManager: AssistantMessageManager by lazy {
        AssistantMessageManager(ChatWaifuApplication.context)
    }
    private var translate: ITranslate? = null

    fun refreshAllKeys() {
        sp.getString(Constant.SAVED_CHAT_KEY, null)?.let {
            chatGPTNetService?.setPrivateKey(it)
        }
        
        // 核心修复：根据已保存的百度 Key 决定使用哪个翻译服务
        val appId = sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null)
        val appKey = sp.getString(Constant.SAVED_TRANSLATE_KEY, null)
        
        if (!appId.isNullOrBlank() && !appKey.isNullOrBlank()) {
            translate = BaiduTranslateService(
                context = ChatWaifuApplication.context,
                appid = appId,
                privateKey = appKey
            )
            Log.d(TAG, "Use Baidu Translate")
        } else {
            // 如果百度 Key 为空，回退到 Bing 翻译
            translate = BingTranslateService(ChatWaifuApplication.context)
            Log.d(TAG, "Use Bing Translate")
        }

        needTranslate = sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true)
        needChatGPTProxy = sp.getBoolean(Constant.SAVED_USE_CHATGPT_PROXY, false)
        val proxyUrl = if(needChatGPTProxy) sp.getString(Constant.SAVED_USE_CHATGPT_PROXY_URL, null) else null
        chatGPTNetService?.updateRetrofit(proxyUrl)

        // Apply voice scale
        vitsHelper.lengthScale = sp.getFloat(Constant.SAVED_VOICE_SCALE, 1.0f)
        Log.d(TAG, "refreshAllKeys: voice lengthScale set to: ${vitsHelper.lengthScale}")

        // Set translate target language based on current VITS model language
        val modelLang = vitsHelper.getModelLanguage()
        val targetLang = if (modelLang == "zh") "zh" else "jp"
        translate?.toLanguage = targetLang
        Log.d(TAG, "refreshAllKeys: translate target language set to: $targetLang")

        // Reload VITS model if path changed (e.g. settings saved)
        if (currentLive2DModelName.isNotEmpty()) {
            val targetPath = getVitsPathForCharacter(currentLive2DModelName)
            if (targetPath != currentVITSModelPath) {
                Log.d(TAG, "VITS path changed from $currentVITSModelPath to $targetPath, reloading...")
                loadVitsModel(targetPath)
            }
        }
    }

    fun getVitsPathForCharacter(characterName: String): String {
        val overrideKey = "${Constant.SAVED_OVERRIDE_VITS_PATH}_$characterName"
        val overridePath = sp.getString(overrideKey, "") ?: ""
        return if (overridePath.isNotBlank() && File(overridePath).exists()) {
            overridePath
        } else {
            ChatWaifuApplication.baseAppDir + File.separator + Constant.VITS_BASE_PATH + File.separator + characterName
        }
    }

    fun mainLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                chatStatusLiveData.postValue(ChatStatus.FETCH_INPUT)
                val input = fetchInput()
                assistantMsgManager.insertUserMessage(input)

                chatStatusLiveData.postValue(ChatStatus.SEND_REQUEST)
                val response = sendChatGPTRequest(input, assistantMsgManager.getSendAssistantList())
                assistantMsgManager.insertGPTMessage(response)
                Log.d(TAG, "get response $response")
                _chatContentUIFlow.emit(constructUIStateFromResponse(response))

                val responseText = response?.choices?.firstOrNull()?.message?.content
                val translateText = fetchTranslateIfNeed(responseText)
                Log.d(TAG, "translate result: $translateText")
                chatStatusLiveData.postValue(ChatStatus.GENERATE_SOUND)
                generateAndPlaySound(translateText)
            }
        }
    }

    fun sendMessage(input: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (inputFunc != null) {
                inputFunc?.invoke(input)
            }
        }
    }

    fun initModel(context: Context) {
        initModelResultLiveData.postValue(emptyList())
        loadingUILiveData.postValue(Pair(true, "Init Models...."))
        viewModelScope.launch(Dispatchers.IO) {
            val finalModelList = mutableListOf<ChannelListBean>()
            localModelManager.initInnerModel(context, finalModelList)
            localModelManager.initExternalModel(context, finalModelList)
            initModelResultLiveData.postValue(finalModelList)
            loadingUILiveData.postValue(Pair(false, ""))
        }
        lipsValueHandler.initLipSync()
    }

    fun loadVitsModel(path: String) {
        currentVITSModelPath = path
        loadingUILiveData.postValue(Pair(true, "Load VITS Model...."))
        
        val isJsonPath = path.endsWith(".json", ignoreCase = true)
        val configPath = if (isJsonPath) path else {
            val rootFiles = localModelManager.getVITSModelFiles(path)
            rootFiles?.find { it.name.endsWith("json", ignoreCase = true) }?.absolutePath ?: ""
        }
        val modelFolder = if (isJsonPath) File(path).parentFile?.absolutePath ?: "" else path
        
        // Find bin file path
        var binPath = ""
        val files = File(modelFolder).listFiles()
        if (!files.isNullOrEmpty()) {
            binPath = files.find { it.name.endsWith("bin", ignoreCase = true) }?.absolutePath ?: ""
        }
        if (binPath.isEmpty()) {
            val fallbackBins = listOf("dec.ncnn.bin", "dp.ncnn.bin", "flow.ncnn.bin", "flow.reverse.ncnn.bin", "emb_g.bin", "emb_t.bin", "enc_p.ncnn.bin", "enc_q.ncnn.bin")
            val binFile = fallbackBins.map { File(modelFolder, it) }.find { it.exists() }
            binPath = binFile?.absolutePath ?: ""
        }

        viewModelScope.launch(Dispatchers.IO) {
            val configResult = suspendCancellableCoroutine<Boolean> {
                vitsHelper.loadConfigs(configPath) { isSuccess ->
                    it.safeResume(isSuccess)
                }
            }

            val binResult = suspendCancellableCoroutine<Boolean> {
                vitsHelper.loadModel(binPath) { isSuccess ->
                    it.safeResume(isSuccess)
                }
            }

            // Update translate target language dynamically on model load
            if (configResult) {
                val modelLang = vitsHelper.getModelLanguage()
                val targetLang = if (modelLang == "zh") "zh" else "jp"
                translate?.toLanguage = targetLang
                Log.d(TAG, "loadVitsModel: translate target language set to: $targetLang")
            }

            _loadVITSModelLiveData.emit(if (binResult && configResult) VITSLoadStatus.STATE_SUCCESS else VITSLoadStatus.STATE_FAILED)
            loadingUILiveData.postValue(Pair(false, ""))
        }
    }

    fun loadChatListCache(characterName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            assistantMsgManager.loadChatListCache(characterName)
        }
    }

    fun loadModelSystemSetting(modelName: String) {
        chatGPTNetService?.setSystemRole(
            localModelManager.getModelSystemSetting(modelName) ?: return
        )
    }

    private suspend fun fetchInput(): String {
        return suspendCancellableCoroutine {
            inputFunc = { input ->
                it.safeResume(input)
            }
        }
    }

    private suspend fun sendChatGPTRequest(
        msg: String,
        assistantList: List<String>
    ): ChatGPTResponseData? {
        return suspendCancellableCoroutine {
            chatGPTNetService?.setAssistantList(assistantList)
            chatGPTNetService?.sendChatMessage(msg) { response ->
                it.safeResume(response)
            }
        }
    }

    private suspend fun fetchTranslateIfNeed(responseText: String?): String? {
        translate ?: return responseText
        responseText ?: return null
        if (!needTranslate || vitsHelper.getModelLanguage() == "zh") {
            return responseText
        }
        chatStatusLiveData.postValue(ChatStatus.TRANSLATE)
        return suspendCancellableCoroutine {
            translate?.getTranslateResult(responseText) { result ->
                it.safeResume(result?.ifBlank { responseText } ?: responseText)
            }
        }
    }

    private fun generateAndPlaySound(needPlayText: String?) {
        vitsHelper.generateAndPlay(text = needPlayText,
            targetSpeakerId = localModelManager.getVITSSpeakerId(currentLive2DModelName),
            callback = { isSuccess ->
            Log.d(TAG, "generate sound $isSuccess")
            if (chatStatusLiveData.value == ChatStatus.GENERATE_SOUND) {
                chatStatusLiveData.postValue(ChatStatus.DEFAULT)
            }},
            forwardResult = {
                lipsValueHandler.sendLipsValues(it)
            }
        )
    }

    private fun constructUIStateFromResponse(response: ChatGPTResponseData?): ChatDialogContentUIState {

        if (!response?.errorMsg.isNullOrEmpty()) {
            return ChatDialogContentUIState(isFromMe = false, errorMsg = response?.errorMsg)
        }

        return ChatDialogContentUIState(
            isFromMe = false,
            chatContent = response?.choices?.firstOrNull()?.message?.content?.trim() ?: ""
        )
    }

    fun sendMineMsgUIState(content: String) {
        CoroutineScope(Dispatchers.Main).launch {
            _chatContentUIFlow.emit(
                ChatDialogContentUIState(
                    isFromMe = true,
                    chatContent = content
                )
            )
        }
    }

    override fun onCleared() {
        vitsHelper.clear()
        lipsValueHandler.shutDown()
        super.onCleared()
    }

    fun openDrawer() {
        drawerShouldBeOpened.value = true
    }

    fun resetOpenDrawerAction() {
        drawerShouldBeOpened.value = false
    }
}

fun <T> CancellableContinuation<T>.safeResume(value: T) {
    if (this.isActive) {
        (this as? Continuation<T>)?.resume(value)
    }
}
