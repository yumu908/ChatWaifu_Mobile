package com.chatwaifu.mobile.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.chatwaifu.mobile.ui.theme.globalDarkTheme
import kotlinx.coroutines.launch

class SettingFragment : Fragment() {

    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: SettingFragmentViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(inflater.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContent {
            // Get state from ViewModel to survive configuration changes and avoid rememberSaveable issues
            val settingUIState = fragmentViewModel.getOrInitState(requireContext())
            
            // local state for theme switching in the fragment
            var currentDarkMode by remember {
                mutableStateOf(settingUIState.darkModeSwitch)
            }
            globalDarkTheme = currentDarkMode
            
            ChatWaifu_MobileTheme(darkTheme = currentDarkMode) {
                val availableModels = fragmentViewModel.getAvailableVitsModels()
                SettingContentScaffold(
                    settingUIState = settingUIState,
                    onNavIconPressed = {
                        activityViewModel.openDrawer()
                    },
                    onSave = { saved ->
                        fragmentViewModel.saveData(saved)
                        activityViewModel.refreshAllKeys()
                        currentDarkMode = saved?.darkModeSwitch ?: false
                        showToast("Settings saved")
                    },
                    onDownloadModel = { type ->
                        lifecycleScope.launch {
                            settingUIState.isDownloading = true
                            val success = fragmentViewModel.downloadModel(type) { progress ->
                                settingUIState.downloadProgress = progress
                            }
                            settingUIState.isDownloading = false
                            if (success) {
                                showToast("Model $type downloaded and ready")
                            } else {
                                showToast("Failed to download model")
                            }
                        }
                    },
                    availableModels = availableModels
                )
            }
        }
    }
}
