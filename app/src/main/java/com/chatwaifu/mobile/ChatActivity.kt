package com.chatwaifu.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.chatwaifu.mobile.ui.base.ChatWaifuRootView
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class ChatActivity : AppCompatActivity() {

    private val chatViewModel: ChatActivityViewModel by lazy {
        ViewModelProvider(this)[ChatActivityViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Orientation is strictly managed via AndroidManifest.xml (locked).
        
        checkPermissions()

        setContentView(
            ComposeView(this).apply {
                setContent {
                    ChatWaifu_MobileTheme {
                        ChatWaifuRootView(
                            chatViewModel = chatViewModel,
                            onChannelListClick = {
                                findNavController().navigate(R.id.nav_channel_list)
                            },
                            onChatLogClick = {
                                findNavController().navigate(R.id.nav_chat_log)
                            },
                            onSettingClick = {
                                findNavController().navigate(R.id.nav_setting)
                            }
                        )
                    }
                }
            }
        )
        chatViewModel.refreshAllKeys()
        chatViewModel.mainLoop()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needRequest.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            showToast("Some permissions were denied. Voice features may not work.")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController().navigateUp() || super.onSupportNavigateUp()
    }

    private fun findNavController(): NavController {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }
}
