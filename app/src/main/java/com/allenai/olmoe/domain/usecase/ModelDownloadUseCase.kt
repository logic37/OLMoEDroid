package com.allenai.olmoe.domain.usecase

import android.content.Context
import com.allenai.olmoe.domain.model.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ModelDownloadUseCase(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Increased timeout
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Increased timeout
        .retryOnConnectionFailure(true) // Enable retry on connection failure
        .build()
    
    fun downloadModel(): Flow<DownloadProgress> = flow {
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        
        // Check if model already exists and is complete (at least 4GB)
        if (modelFile.exists() && modelFile.length() >= 4_000_000_000L) {
            android.util.Log.d("ModelDownload", "Model already exists and is complete (${modelFile.length()} bytes)")
            emit(DownloadProgress.Success(modelFile.absolutePath))
            return@flow
        }
        
        // Check if partial file exists for resume
        val existingSize = if (modelFile.exists()) modelFile.length() else 0L
        val shouldResume = existingSize > 0 && existingSize < 4_000_000_000L
        
        if (shouldResume) {
            android.util.Log.d("ModelDownload", "Resuming download from ${existingSize} bytes")
            emit(DownloadProgress.Resume(existingSize))
        } else if (modelFile.exists()) {
            android.util.Log.d("ModelDownload", "Partial model file found (${modelFile.length()} bytes), deleting to restart download")
            modelFile.delete()
        }
        
        try {
            emit(DownloadProgress.Started)
            
            // Add some debugging
            android.util.Log.d("ModelDownload", "Starting download from: ${AppConstants.Model.downloadURL}")
            android.util.Log.d("ModelDownload", "Downloading to: ${modelFile.absolutePath}")
            
            val requestBuilder = Request.Builder()
                .url(AppConstants.Model.downloadURL)
            
            // Add Range header for resume if needed
            if (shouldResume) {
                requestBuilder.addHeader("Range", "bytes=$existingSize-")
                android.util.Log.d("ModelDownload", "Added Range header: bytes=$existingSize-")
            }
            
            val request = requestBuilder.build()
            
            val response = client.newCall(request).execute()
            
            android.util.Log.d("ModelDownload", "Response code: ${response.code}")
            android.util.Log.d("ModelDownload", "Response headers: ${response.headers}")
            
            // Handle different response codes for resume
            if (response.code == 206) {
                android.util.Log.d("ModelDownload", "Server supports resume (206 Partial Content)")
            } else if (response.code == 200 && shouldResume) {
                android.util.Log.d("ModelDownload", "Server doesn't support resume, restarting download")
                modelFile.delete()
                emit(DownloadProgress.Started)
            } else if (!response.isSuccessful) {
                val errorMsg = "Failed to download model: HTTP ${response.code}"
                android.util.Log.e("ModelDownload", errorMsg)
                emit(DownloadProgress.Error(errorMsg))
                return@flow
            }
            
            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            val totalSize = if (shouldResume && contentLength > 0) existingSize + contentLength else contentLength
            
            android.util.Log.d("ModelDownload", "Content length: $contentLength bytes")
            android.util.Log.d("ModelDownload", "Total expected size: $totalSize bytes")
            
            // Create parent directories if they don't exist
            modelFile.parentFile?.mkdirs()
            
            val inputStream = body.byteStream()
            
            // Use RandomAccessFile for resume capability
            val outputFile = RandomAccessFile(modelFile, "rw")
            if (shouldResume) {
                outputFile.seek(existingSize)
                android.util.Log.d("ModelDownload", "Seeked to position: $existingSize")
            } else {
                outputFile.setLength(0) // Truncate file for fresh download
            }
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = existingSize
            
            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputFile.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val progress = if (totalSize > 0) {
                        (totalBytesRead.toFloat() / totalSize) * 100
                    } else {
                        0f
                    }
                    
                    emit(DownloadProgress.Progress(progress, totalBytesRead, totalSize))
                }
                
                outputFile.close()
                inputStream.close()
                
                // Verify the download is complete
                if (modelFile.length() < 4_000_000_000L) {
                    throw IOException("Download incomplete: file size ${modelFile.length()} bytes is too small")
                }
                
                android.util.Log.d("ModelDownload", "Download completed successfully")
                android.util.Log.d("ModelDownload", "File size: ${modelFile.length()} bytes")
                
                emit(DownloadProgress.Success(modelFile.absolutePath))
                
            } catch (e: Exception) {
                // Don't delete partial file on error, allow resume
                outputFile.close()
                inputStream.close()
                android.util.Log.e("ModelDownload", "Download interrupted, keeping partial file for resume", e)
                throw e
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ModelDownload", "Download exception", e)
            val errorMessage = when (e) {
                is UnknownHostException -> "Network error: Unable to reach server. Check your internet connection."
                is SocketTimeoutException -> "Network error: Connection timeout. The download may resume when you retry."
                is IOException -> "Network error: ${e.message}. You can resume the download."
                is okhttp3.internal.http2.StreamResetException -> "Network error: Connection interrupted. The download can be resumed."
                else -> "Download failed: ${e.message ?: "Unknown error"}. You can resume the download."
            }
            android.util.Log.e("ModelDownload", "Error message: $errorMessage")
            emit(DownloadProgress.Error(errorMessage))
        }
    }.flowOn(Dispatchers.IO)
    
    fun copyModelFromUri(uri: android.net.Uri): Flow<DownloadProgress> = flow {
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        
        try {
            emit(DownloadProgress.Started)
            
            android.util.Log.d("ModelDownload", "Copying model from URI: $uri")
            android.util.Log.d("ModelDownload", "Copying to: ${modelFile.absolutePath}")
            
            // Create parent directories if they don't exist
            modelFile.parentFile?.mkdirs()
            
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(modelFile)
            
            if (inputStream == null) {
                throw IOException("Could not open input stream from URI")
            }
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            
            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    // Emit progress every 1MB
                    if (totalBytesRead % (1024 * 1024) == 0L) {
                        emit(DownloadProgress.Progress(0f, totalBytesRead, -1L))
                    }
                }
                
                outputStream.close()
                inputStream.close()
                
                // Verify the file is complete (at least 4GB)
                if (modelFile.length() < 4_000_000_000L) {
                    throw IOException("File too small: ${modelFile.length()} bytes (expected at least 4GB)")
                }
                
                android.util.Log.d("ModelDownload", "File copied successfully")
                android.util.Log.d("ModelDownload", "File size: ${modelFile.length()} bytes")
                
                emit(DownloadProgress.Success(modelFile.absolutePath))
                
            } catch (e: Exception) {
                // Clean up partial file on error
                outputStream.close()
                inputStream.close()
                if (modelFile.exists()) {
                    modelFile.delete()
                    android.util.Log.d("ModelDownload", "Deleted partial file due to error")
                }
                throw e
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ModelDownload", "Copy exception", e)
            val errorMessage = when (e) {
                is java.io.IOException -> "File error: ${e.message}"
                is java.lang.SecurityException -> "Permission denied: ${e.message}"
                else -> "Copy failed: ${e.message ?: "Unknown error"}"
            }
            android.util.Log.e("ModelDownload", "Error message: $errorMessage")
            emit(DownloadProgress.Error(errorMessage))
        }
    }.flowOn(Dispatchers.IO)
    
    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        return modelFile.exists() && modelFile.length() >= 4_000_000_000L
    }
    
    fun getModelPath(): String {
        return File(context.filesDir, "${AppConstants.Model.filename}.gguf").absolutePath
    }
    
    fun getPartialDownloadSize(): Long {
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        return if (modelFile.exists() && modelFile.length() < 4_000_000_000L) modelFile.length() else 0L
    }
    
    fun clearPartialDownload() {
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        if (modelFile.exists() && modelFile.length() < 4_000_000_000L) {
            modelFile.delete()
            android.util.Log.d("ModelDownload", "Cleared partial download")
        }
    }
}

sealed class DownloadProgress {
    object Started : DownloadProgress()
    data class Resume(val existingBytes: Long) : DownloadProgress()
    data class Progress(val percentage: Float, val bytesRead: Long, val totalBytes: Long) : DownloadProgress()
    data class Success(val filePath: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
} 