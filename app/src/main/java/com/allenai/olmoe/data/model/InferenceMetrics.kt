package com.allenai.olmoe.data.model

import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Parcelize
data class InferenceMetrics(
    var inputTokenCount: Int = 0,
    var outputTokenCount: Int = 0,
    var totalTokens: Int = 0,
    var inferenceTimeMs: Long = 0,
    var tokensPerSecond: Float = 0f,
    var startTime: Long = 0,
    var endTime: Long = 0
) : Parcelable {
    
    fun start() {
        startTime = System.currentTimeMillis()
    }
    
    fun stop() {
        endTime = System.currentTimeMillis()
        inferenceTimeMs = endTime - startTime
        if (inferenceTimeMs > 0) {
            tokensPerSecond = (outputTokenCount * 1000f) / inferenceTimeMs
        }
    }
    
    fun recordToken() {
        outputTokenCount++
        totalTokens = inputTokenCount + outputTokenCount
    }
    
    fun reset() {
        inputTokenCount = 0
        outputTokenCount = 0
        totalTokens = 0
        inferenceTimeMs = 0
        tokensPerSecond = 0f
        startTime = 0
        endTime = 0
    }
} 