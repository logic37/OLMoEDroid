package com.allenai.olmoe.domain.model

import com.allenai.olmoe.data.model.Chat
import com.allenai.olmoe.data.model.InferenceMetrics
import com.allenai.olmoe.data.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import android.os.Build

class LLM(
    private val modelPath: String,
    private val template: Template = Template.OLMoE(),
    private val maxTokenCount: Int = 2048,
    private val topK: Int = 40,
    private val topP: Float = 0.95f,
    private val temperature: Float = 0.8f,
    private val onMessageAdded: ((Chat) -> Unit)? = null,
    initialHistory: List<Chat> = emptyList()
) {
    
    private val _history = MutableStateFlow<List<Chat>>(initialHistory)
    val history: StateFlow<List<Chat>> = _history.asStateFlow()
    
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()
    
    val metrics = InferenceMetrics()
    
    private var isGenerating = false
    private var shouldStop = false
    private var savedState: Boolean = false
    private var modelLoaded = false
    
    // Native LLM interface
    private val nativeLLM = LLMNative()
    
    init {
        android.util.Log.d("LLM", "LLM initialized with model path: $modelPath")
        
        // Check if model file exists, if not, download it
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            android.util.Log.d("LLM", "Model file not found, will download: $modelPath")
            // Model will be downloaded by ModelDownloadUseCase when needed
        } else {
            android.util.Log.d("LLM", "Model file exists, attempting to load: $modelPath")
            // Initialize native model with OLMoE model
            if (nativeLLM.initModel(modelPath)) {
                android.util.Log.d("LLM", "OLMoE model loaded successfully from: $modelPath")
                modelLoaded = true
            } else {
                android.util.Log.e("LLM", "Failed to load OLMoE model from: $modelPath")
                modelLoaded = false
            }
        }
    }
    
    // Check if model file exists and is loaded
    fun isModelReady(): Boolean {
        val modelFile = File(modelPath)
        return modelFile.exists() && modelFile.length() >= 4_000_000_000L && (modelLoaded || nativeLLM.isInitialized())
    }
    
    suspend fun respond(input: String) {
        if (isGenerating) return
        
        android.util.Log.d("LLM", "Starting response for: $input")
        
        isGenerating = true
        shouldStop = false
        
        try {
            // Add user message to history
            val userMessage = Chat(role = Role.USER, content = input)
            val newHistory = _history.value + userMessage
            _history.value = newHistory
            
            // Save user message to database
            onMessageAdded?.invoke(userMessage)
            
            // Clear output
            _output.value = ""
            
            // Start metrics
            metrics.start()
            
            // Use native model for actual inference
            generateResponse(input)
            
            // Stop metrics
            metrics.stop()
            
        } catch (e: Exception) {
            android.util.Log.e("LLM", "Error in respond function", e)
            // Add error message to history
            val errorMessage = Chat(role = Role.BOT, content = "Sorry, I encountered an error while processing your request.")
            _history.value = _history.value + errorMessage
        } finally {
            isGenerating = false
            android.util.Log.d("LLM", "Response completed, isGenerating set to false")
        }
    }
    
    private suspend fun generateResponse(input: String) {
        android.util.Log.d("LLM", "Generating response for: $input")
        android.util.Log.d("LLM", "Current isGenerating state: $isGenerating")
        
        try {
            // TEMPORARY: Use simple input format for testing
            val processedInput = input  // Just use the raw input for now
            android.util.Log.d("LLM", "Processed input: $processedInput")
            
            // Tokenize the input
            val tokens = nativeLLM.tokenize(processedInput)
            if (tokens == null) {
                throw Exception("Failed to tokenize input")
            }
            
            android.util.Log.d("LLM", "Tokenized input: ${tokens.size} tokens")
            
            // Generate response using native model with limited tokens for more reasonable responses
            val maxTokens = 20 // Limit tokens for stub testing
            var tokenCount = 0
            
            val response = nativeLLM.generateResponse(tokens, object : LLMNative.TokenCallback {
                override fun onToken(token: String) {
                    if (tokenCount < maxTokens && !shouldStop) {
                        // Update output for streaming effect
                        _output.value = _output.value + token
                        metrics.recordToken()
                        tokenCount++
                    }
                }
            })
            
            if (response == null) {
                throw Exception("Failed to generate response")
            }
            
            android.util.Log.d("LLM", "Generated response: $response")
            
            // Add bot response to history
            val botMessage = Chat(role = Role.BOT, content = response)
            val newHistory = _history.value + botMessage
            _history.value = newHistory
            
            // Save bot message to database
            onMessageAdded?.invoke(botMessage)
            
            android.util.Log.d("LLM", "Bot message added to history: ${botMessage.content}")
            
            // Clear output to indicate generation is complete
            _output.value = ""
            android.util.Log.d("LLM", "GenerateResponse completed, output cleared")
            
            // Mark that we have saved state for next interaction
            savedState = true
            
        } catch (e: Exception) {
            android.util.Log.e("LLM", "Error in generateResponse", e)
            val errorMessage = Chat(role = Role.BOT, content = "Sorry, I encountered an error while generating a response: ${e.message}")
            _history.value = _history.value + errorMessage
        }
    }
    
    fun stop() {
        shouldStop = true
    }
    
    fun cleanup() {
        nativeLLM.cleanup()
        android.util.Log.d("LLM", "Native resources cleaned up")
    }
    
    suspend fun clearHistory() {
        _history.value = emptyList()
        _output.value = ""
        metrics.reset()
        savedState = false
        android.util.Log.d("LLM", "History cleared, saved state reset")
    }
    

} 