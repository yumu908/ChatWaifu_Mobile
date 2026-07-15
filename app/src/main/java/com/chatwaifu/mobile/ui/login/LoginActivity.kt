package com.chatwaifu.mobile.ui.login

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.chatwaifu.mobile.ChatActivity
import com.chatwaifu.mobile.databinding.ActivityLoginBinding
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.showToast

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val sp: SharedPreferences by lazy {
        getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)

            binding.chatGptText.setText(sp.getString(Constant.SAVED_CHAT_KEY, ""))

            binding.done.setOnClickListener {
                val chatKey = binding.chatGptText.text.toString().trim()

                if (chatKey.isEmpty()) {
                    showToast("Please fill the ChatGPT key.")
                    return@setOnClickListener
                }

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                    return@setOnClickListener
                }

                // Save config
                sp.edit().apply {
                    putString(Constant.SAVED_CHAT_KEY, chatKey)
                    putBoolean(Constant.SAVED_USE_TRANSLATE, true) 
                    apply()
                }

                jumpToChat()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Init error", e)
        }
    }

    private fun jumpToChat() {
        try {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Navigation failed", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showToast("Permission granted! Click Done again.")
        } else {
            showToast("Microphone permission is required for voice features.")
        }
    }
}
