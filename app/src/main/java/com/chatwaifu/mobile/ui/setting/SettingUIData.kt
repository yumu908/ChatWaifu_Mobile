package com.chatwaifu.mobile.ui.setting

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatwaifu.chatgpt.ChatGPTNetService
import kotlinx.parcelize.Parcelize

/**
 * 设置页面的原始数据模型
 */
@Immutable
@Parcelize
data class SettingUIData(
    var chatGPTAppId: String = "",
    var translateSwitch: Boolean = true,
    var translateAppId: String = "",
    var translateAppKey: String = "",
    var yuukaSetting: String = "",
    var amadeusSetting: String = "",
    var atriSetting: String = "",
    var externalSetting: String = "",
    var darkModeSwitch: Boolean = false,
    var externalModelSpeakerId: Int = 0,
    var gptProxySwitch: Boolean = false,
    var gptProxyUrl: String? = ChatGPTNetService.CHATGPT_DEAFULT_PROXY_URL,
    var vitsModelType: Int = 1,
    var yuukaVitsPath: String = "",
    var atriVitsPath: String = "",
    var amadeusVitsPath: String = "",
    var voiceScale: Float = 1.0f
) : Parcelable

/**
 * 设置页面的 UI 渲染状态类
 */
class SettingUIState(data: SettingUIData) {
    var chatGPTAppId by mutableStateOf(data.chatGPTAppId)
    var translateSwitch by mutableStateOf(data.translateSwitch)
    var yuukaSetting by mutableStateOf(data.yuukaSetting)
    var amadeusSetting by mutableStateOf(data.amadeusSetting)
    var atriSetting by mutableStateOf(data.atriSetting)
    var externalSetting by mutableStateOf(data.externalSetting)
    var externalModelSpeakerId by mutableStateOf(data.externalModelSpeakerId)
    var darkModeSwitch by mutableStateOf(data.darkModeSwitch)
    var gptProxyUrl by mutableStateOf(data.gptProxyUrl ?: "")
    var gptProxySwitch by mutableStateOf(data.gptProxySwitch)
    var vitsModelType by mutableStateOf(data.vitsModelType)
    var yuukaVitsPath by mutableStateOf(data.yuukaVitsPath)
    var atriVitsPath by mutableStateOf(data.atriVitsPath)
    var amadeusVitsPath by mutableStateOf(data.amadeusVitsPath)
    var voiceScale by mutableStateOf(data.voiceScale)
    
    var isDownloading by mutableStateOf(false)
    var downloadProgress by mutableStateOf(0f)

    fun convertState2Data(): SettingUIData {
        return SettingUIData(
            chatGPTAppId = chatGPTAppId,
            translateSwitch = translateSwitch,
            yuukaSetting = yuukaSetting,
            amadeusSetting = amadeusSetting,
            atriSetting = atriSetting,
            externalSetting = externalSetting,
            externalModelSpeakerId = externalModelSpeakerId,
            darkModeSwitch = darkModeSwitch,
            gptProxyUrl = gptProxyUrl,
            gptProxySwitch = gptProxySwitch,
            vitsModelType = vitsModelType,
            yuukaVitsPath = yuukaVitsPath,
            atriVitsPath = atriVitsPath,
            amadeusVitsPath = amadeusVitsPath,
            voiceScale = voiceScale
        )
    }
}
