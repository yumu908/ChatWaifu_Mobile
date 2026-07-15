package com.chatwaifu.mobile.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.chatwaifu.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingContentScaffold(
    settingUIState: SettingUIState,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    onSave: (SettingUIData?) -> Unit = {},
    onDownloadModel: (Int) -> Unit = {},
    availableModels: List<Pair<String, String>> = emptyList()
) {
    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = "Setting",
                onNavIconPressed = onNavIconPressed,
                externalActions = {
                    OutlinedButton(
                        onClick = { onSave(settingUIState.convertState2Data()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 15.dp).height(35.dp),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        content = { Text(text = stringResource(id = R.string.setting_btn_save)) }
                    )
                }
            )
        },
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            SettingContent(settingUIState, onDownloadModel = onDownloadModel, availableModels = availableModels)
        }
    }
}

@Composable
fun SettingContent(
    settingUIState: SettingUIState,
    onDownloadModel: (Int) -> Unit = {},
    availableModels: List<Pair<String, String>> = emptyList()
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- 重点调节项：语速调节 ---
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "语音播放语速 (Scale): ${"%.2f".format(settingUIState.voiceScale)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "数值越小语速越快。解决中文语速过慢推荐: 0.85",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
                Slider(
                    value = settingUIState.voiceScale,
                    onValueChange = { settingUIState.voiceScale = it },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // --------------------------

        DividerItem(modifier = Modifier.padding(vertical = 15.dp))

        // ChatGPT Key
        ItemTitle(stringResource(id = R.string.setting_title_chatgpt))
        SettingEditText(
            initValue = settingUIState.chatGPTAppId,
            hint = stringResource(id = R.string.setting_chatgpt_appid_hint)
        ) {
            settingUIState.chatGPTAppId = it
        }

        DividerItem(modifier = Modifier.padding(vertical = 15.dp))
        
        // 模型下载
        ItemTitle("下载更多声音模型 (VITS):")
        SettingModelSelector(
            currentType = settingUIState.vitsModelType,
            onTypeSelected = { settingUIState.vitsModelType = it },
            isDownloading = settingUIState.isDownloading,
            downloadProgress = settingUIState.downloadProgress,
            onDownloadClick = { onDownloadModel(settingUIState.vitsModelType) }
        )
        
        DividerItem(modifier = Modifier.padding(vertical = 15.dp))

        // 角色声音映射
        ItemTitle("角色声音映射配置:")
        
        Text("优香 (Yuuka):", style = MaterialTheme.typography.labelMedium)
        VitsModelPathSelector(availableModels, settingUIState.yuukaVitsPath) { settingUIState.yuukaVitsPath = it }
        
        Spacer(modifier = Modifier.height(10.dp))
        Text("亚托莉 (ATRI):", style = MaterialTheme.typography.labelMedium)
        VitsModelPathSelector(availableModels, settingUIState.atriVitsPath) { settingUIState.atriVitsPath = it }
        
        Spacer(modifier = Modifier.height(10.dp))
        Text("助手 (Amadeus):", style = MaterialTheme.typography.labelMedium)
        VitsModelPathSelector(availableModels, settingUIState.amadeusVitsPath) { settingUIState.amadeusVitsPath = it }

        DividerItem(modifier = Modifier.padding(vertical = 15.dp))

        SettingSwitch(stringResource(id = R.string.setting_title_translate_switch), settingUIState.translateSwitch) {
            settingUIState.translateSwitch = it
        }
        
        SettingSwitch("使用 ChatGPT 代理", settingUIState.gptProxySwitch) {
            settingUIState.gptProxySwitch = it
        }
        
        AnimatedVisibility(visible = settingUIState.gptProxySwitch) {
            SettingEditText(initValue = settingUIState.gptProxyUrl) { settingUIState.gptProxyUrl = it.trim() }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SettingModelSelector(
    currentType: Int,
    onTypeSelected: (Int) -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadClick: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val modelOptions = listOf(
        "Nanoka (中/日)" to 1, "Anan (中/日)" to 2, "Arisa (中/日)" to 3, "Ema (中/日)" to 4,
        "Gokucho (中/日)" to 5, "Hanna (中/日)" to 6, "Hiro (中/日)" to 7, "Koko (中/日)" to 8,
        "Mago (中/日)" to 9, "Meruru (中/日)" to 10, "Miria (中/日)" to 11, "Noa (中/日)" to 12,
        "Reia (中/日)" to 13, "Sheri (中/日)" to 14
    )
    val currentLabel = modelOptions.find { it.second == currentType }?.first ?: "选择模型"

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                .clickable { expanded = true }.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = currentLabel, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                modelOptions.forEach { (label, type) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onTypeSelected(type); expanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDownloadClick, enabled = !isDownloading, modifier = Modifier.fillMaxWidth()) {
            if (isDownloading) {
                CircularProgressIndicator(progress = downloadProgress, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("下载中... ${(downloadProgress * 100).toInt()}%")
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("下载所选 VITS 模型")
            }
        }
    }
}

@Composable
fun SettingSwitch(switchName: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = switchName, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
    }
}

@Composable
fun SettingEditText(
    modifier: Modifier = Modifier.fillMaxWidth().height(50.dp),
    initValue: String? = null,
    hint: String = "",
    onValueChanged: (String) -> Unit = {}
) {
    var value by rememberSaveable { mutableStateOf(initValue ?: "") }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
        if (value.isEmpty()) {
            Text(text = hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = { value = it; onValueChanged(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DividerItem(modifier: Modifier = Modifier) {
    Divider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ItemTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
}

@Composable
fun VitsModelPathSelector(
    availableModels: List<Pair<String, String>>,
    currentPath: String,
    onPathSelected: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentLabel = availableModels.find { it.second == currentPath }?.first ?: "使用默认声音"

    Box(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable { expanded = true }.padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentLabel, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableModels.forEach { (label, path) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onPathSelected(path); expanded = false })
            }
        }
    }
}
