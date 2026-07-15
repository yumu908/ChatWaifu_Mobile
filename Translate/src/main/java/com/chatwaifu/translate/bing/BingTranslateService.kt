package com.chatwaifu.translate.bing

import android.content.Context
import android.util.Log
import com.chatwaifu.translate.ITranslate
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

class BingTranslateService(
    private val context: Context,
    private var fromLanguage: String = "auto",
    override var toLanguage: String = "ja" // Bing uses "ja" instead of "jp"
) : ITranslate {

    companion object {
        private const val TAG = "BingTranslateService"
        private const val BASE_URL = "https://api-edge.cognitive.microsofttranslator.com/"
        private const val TOKEN_EXPIRY_MS = 15 * 60 * 1000 // 15 minutes
    }

    private var cachedToken: String? = null
    private var lastTokenFetchTime: Long = 0L

    private val api: BingTranslateAPI by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(BingTranslateAPI::class.java)
    }

    override fun getTranslateResult(input: String, callback: (result: String?) -> Unit) {
        val normalizedTo = if (toLanguage == "jp") "ja" else toLanguage
        val normalizedFrom = if (fromLanguage == "auto") null else fromLanguage

        val startIndex = input.indexOfFirst { it.isLetterOrDigit() }
        val endIndex = input.indexOfLast { it.isLetterOrDigit() }
        if (startIndex == -1 || endIndex == -1) {
            callback.invoke(input)
            return
        }
        val realInput = input.substring(startIndex, endIndex + 1).replace("\n", "").trimIndent()

        getValidToken { token ->
            if (token == null) {
                Log.e(TAG, "Failed to obtain auth token")
                callback.invoke(null)
                return@getValidToken
            }

            val body = listOf(TranslateTextItem(realInput))
            api.translate(
                authorization = "Bearer $token",
                from = normalizedFrom,
                to = normalizedTo,
                body = body
            ).enqueue(object : Callback<List<TranslateResponseItem>> {
                override fun onResponse(
                    call: Call<List<TranslateResponseItem>>,
                    response: Response<List<TranslateResponseItem>>
                ) {
                    if (response.isSuccessful) {
                        val resultText = response.body()?.firstOrNull()?.translations?.firstOrNull()?.text
                        callback.invoke(resultText)
                    } else {
                        Log.e(TAG, "Translation error: ${response.code()} ${response.message()}")
                        callback.invoke(null)
                    }
                }

                override fun onFailure(call: Call<List<TranslateResponseItem>>, t: Throwable) {
                    Log.e(TAG, "Translation failed", t)
                    callback.invoke(null)
                }
            })
        }
    }

    @Synchronized
    private fun getValidToken(onTokenReady: (String?) -> Unit) {
        val currentTime = System.currentTimeMillis()
        val currentToken = cachedToken
        if (currentToken != null && (currentTime - lastTokenFetchTime) < TOKEN_EXPIRY_MS) {
            onTokenReady.invoke(currentToken)
            return
        }

        api.getAuthToken().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val token = response.body()?.string()?.trim()
                    if (!token.isNullOrBlank()) {
                        cachedToken = token
                        lastTokenFetchTime = System.currentTimeMillis()
                        onTokenReady.invoke(token)
                        return
                    }
                }
                Log.e(TAG, "Token request failed with code: ${response.code()}")
                onTokenReady.invoke(null)
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e(TAG, "Failed to fetch token", t)
                onTokenReady.invoke(null)
            }
        })
    }
}

interface BingTranslateAPI {
    @GET("https://edge.microsoft.com/translate/auth")
    fun getAuthToken(): Call<ResponseBody>

    @POST("translate")
    fun translate(
        @Header("Authorization") authorization: String,
        @Query("api-version") apiVersion: String = "3.0",
        @Query("from") from: String?,
        @Query("to") to: String,
        @Body body: List<TranslateTextItem>
    ): Call<List<TranslateResponseItem>>
}

data class TranslateTextItem(
    @SerializedName("Text") val text: String
)

data class TranslateResponseItem(
    @SerializedName("translations") val translations: List<Translation>
)

data class Translation(
    @SerializedName("text") val text: String,
    @SerializedName("to") val to: String
)
