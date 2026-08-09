package com.magicimageviewer.app.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Posts an image to the PC agent's /upload endpoint. */
object UploadClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }

    /** Blocking call — invoke from a background thread. */
    fun upload(hostPort: String, fileName: String, bytes: ByteArray, mimeType: String): Result {
        val url = "http://$hostPort/upload"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", fileName,
                bytes.toRequestBody(mimeType.toMediaType())
            )
            .build()
        val request = Request.Builder().url(url).post(body).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.Success
                else Result.Failure("HTTP ${response.code}")
            }
        } catch (e: IOException) {
            Result.Failure(e.message ?: "network error")
        }
    }
}
