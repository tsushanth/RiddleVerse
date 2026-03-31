package com.kreativekoala.riddleverse

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// Use custom sealed class instead of Kotlin Result
sealed class UploadResult {
    data class Success(val url: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}

class ScreenshotUploadService {
    private val client = OkHttpClient()
    private val baseUrl = "https://puzzleverseai.com/api/games"

    suspend fun uploadScreenshot(imageUri: Uri, context: Context): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                // Convert URI to File
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.jpg")

                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Create multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "screenshot",
                        tempFile.name,
                        tempFile.asRequestBody("image/jpeg".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/upload-screenshot")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val uploadResponse = json.decodeFromString<UploadResponse>(responseBody)

                    // Clean up temp file
                    tempFile.delete()

                    UploadResult.Success(uploadResponse.url)
                } else {
                    UploadResult.Error("Upload failed: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("ScreenshotUpload", "Error uploading screenshot", e)
                UploadResult.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class UploadResponse(
    val url: String,
    val message: String
)