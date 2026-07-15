package com.chatwaifu.chatgpt

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Description: GhatGPTAPI
 * Author: Voine
 * Date: 2023/2/18
 */
interface GhatGPTAPI {
    @POST
    fun sendMsg(@Url url: String, @Body requestMsg: RequestBody): Call<ChatGPTResponseData>
}