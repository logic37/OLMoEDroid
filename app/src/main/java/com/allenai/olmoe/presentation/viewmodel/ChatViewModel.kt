package com.allenai.olmoe.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allenai.olmoe.data.model.Chat
import com.allenai.olmoe.data.model.InferenceMetrics
import com.allenai.olmoe.data.model.Role
import com.allenai.olmoe.domain.model.LLM
import com.allenai.olmoe.domain.usecase.ModelDownloadUseCase
import com.allenai.olmoe.domain.usecase.DownloadProgress
import com.allenai.olmoe.domain.model.AppConstants
import com.allenai.olmoe.data.repository.ChatRepository
import com.allenai.olmoe.data.database.AppDatabase
import java.io.File
// import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
// import javax.inject.Inject

// @HiltViewModel
class ChatViewModel(private val context: android.content.Context) : ViewModel() {
    
    init {
        android.util.Log.d("ChatViewModel", "ChatViewModel created/initialized")
    }
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var llm: LLM? = null
    
    // Create repository for persistence (temporarily disabled due to Room issues)
    private val chatRepository: ChatRepository? = null
    
    init {
        // Check if model exists and initialize accordingly
        checkModelStatus()
    }
    
    private fun checkModelStatus() {
        // Check if model file exists and is complete
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        if (modelFile.exists() && modelFile.length() >= 4_000_000_000L) { // Check if file is at least 4GB (complete model)
            android.util.Log.d("ChatViewModel", "Model file exists and is complete (${modelFile.length()} bytes), initializing LLM")
            _uiState.value = _uiState.value.copy(
                isModelReady = false, // Will be set to true after LLM initialization
                downloadProgress = null // Clear download progress to show chat interface
            )
            initializeLLM()
        } else {
            android.util.Log.d("ChatViewModel", "Model file not found or incomplete (${modelFile.length()} bytes), starting download")
            _uiState.value = _uiState.value.copy(
                isModelReady = false,
                downloadProgress = null // Show download button initially
            )
            // Don't auto-start download, let user click the button
        }
    }
    
    fun downloadModel() {
        android.util.Log.d("ChatViewModel", "Starting model download")
        val modelDownloadUseCase = ModelDownloadUseCase(context)
        viewModelScope.launch {
            modelDownloadUseCase.downloadModel().collect { progress ->
                android.util.Log.d("ChatViewModel", "Download progress: $progress")
                when (progress) {
                    is DownloadProgress.Started -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                    is DownloadProgress.Resume -> {
                        android.util.Log.d("ChatViewModel", "Resuming download from ${progress.existingBytes} bytes")
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                    is DownloadProgress.Progress -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                    is DownloadProgress.Success -> {
                        android.util.Log.d("ChatViewModel", "Model downloaded successfully: ${progress.filePath}")
                        _uiState.value = _uiState.value.copy(
                            isModelReady = false, // Will be set to true after LLM initialization
                            downloadProgress = null // Clear download progress to show chat interface
                        )
                        initializeLLM()
                    }
                    is DownloadProgress.Error -> {
                        android.util.Log.e("ChatViewModel", "Model download failed: ${progress.message}")
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                }
            }
        }
    }

    private fun initializeLLM(existingMessages: List<Chat> = emptyList()) {
        // Get the model file path from the download use case
        val modelFile = File(context.filesDir, "${AppConstants.Model.filename}.gguf")
        android.util.Log.d("ChatViewModel", "Initializing LLM with model path: ${modelFile.absolutePath}")
        android.util.Log.d("ChatViewModel", "Model file exists: ${modelFile.exists()}")
        android.util.Log.d("ChatViewModel", "Initializing LLM with ${existingMessages.size} existing messages")
        
        // Only initialize if LLM is null
        if (llm == null) {
            llm = LLM(
                modelPath = modelFile.absolutePath,
                onMessageAdded = { chat ->
                    viewModelScope.launch {
                        try {
                            // Temporarily disable database saving due to Room issues
                            android.util.Log.d("ChatViewModel", "Database temporarily disabled, message not saved")
                            
                            // Update UI state with the new message
                            _uiState.value = _uiState.value.copy(
                                history = _uiState.value.history + chat
                            )
                            android.util.Log.d("ChatViewModel", "Message added to UI state: ${chat.content.take(50)}")
                        } catch (e: Exception) {
                            android.util.Log.e("ChatViewModel", "Error adding message to UI state", e)
                        }
                    }
                },
                initialHistory = existingMessages
            )
            
            // Check if model is ready after initialization
            val modelReady = llm?.isModelReady() ?: false
            android.util.Log.d("ChatViewModel", "LLM initialized successfully with ${existingMessages.size} existing messages")
            android.util.Log.d("ChatViewModel", "Model ready status: $modelReady")
            
            // Update UI state based on model readiness - IMPORTANT: Set isModelReady to true if model file exists and is complete
            val modelFileExists = modelFile.exists() && modelFile.length() >= 4_000_000_000L
            val finalModelReady = modelReady || modelFileExists
            android.util.Log.d("ChatViewModel", "Model file exists and complete: $modelFileExists, Final model ready: $finalModelReady")
            
            _uiState.value = _uiState.value.copy(
                isModelReady = finalModelReady,
                downloadProgress = if (finalModelReady) null else _uiState.value.downloadProgress
            )
            
            // Only collect output state flow, not history
            viewModelScope.launch {
                llm?.output?.collect { output ->
                    android.util.Log.d("ChatViewModel", "Output updated: '$output'")
                    _uiState.value = _uiState.value.copy(
                        currentOutput = output,
                        isGenerating = output.isNotEmpty()
                    )
                    android.util.Log.d("ChatViewModel", "UI state updated with output: '$output'")
                }
            }
        } else {
            android.util.Log.d("ChatViewModel", "LLM already initialized, skipping")
        }
    }
    
    private fun loadExistingMessagesAndInitializeLLM() {
        viewModelScope.launch {
            try {
                // Temporarily disable database loading due to Room issues
                android.util.Log.d("ChatViewModel", "Database temporarily disabled, starting with empty history")
                
                // Initialize LLM with empty history for now
                initializeLLM(emptyList())
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in initialization", e)
                // Still initialize LLM even if loading fails
                initializeLLM(emptyList())
            }
        }
    }
    
    fun sendMessage(message: String) {
        if (message.isBlank() || _uiState.value.isGenerating) return
        
        // Add debugging
        android.util.Log.d("ChatViewModel", "Sending message: $message")
        android.util.Log.d("ChatViewModel", "Current history size: ${_uiState.value.history.size}")
        
        // Check if model is ready
        if (!_uiState.value.isModelReady) {
            android.util.Log.d("ChatViewModel", "Model not ready, starting download")
            checkModelStatus()
            return
        }
        
        // Ensure LLM is initialized
        if (llm == null) {
            android.util.Log.e("ChatViewModel", "LLM is null, initializing...")
            initializeLLM(emptyList())
        }
        
        viewModelScope.launch {
            try {
                // Send to LLM (it will handle adding the user message to history)
                llm?.respond(message)
                android.util.Log.d("ChatViewModel", "Message sent successfully")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error sending message", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${e.message}"
                )
            }
        }
    }
    
    fun stopGeneration() {
        llm?.stop()
    }
    
    fun clearHistory() {
        android.util.Log.d("ChatViewModel", "Clearing history")
        viewModelScope.launch {
            try {
                // Temporarily disable database clearing due to Room issues
                android.util.Log.d("ChatViewModel", "Database temporarily disabled, clearing UI state only")
                
                // Clear from UI state
                _uiState.value = _uiState.value.copy(history = emptyList())
                
                // Clear from LLM
                llm?.clearHistory()
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error clearing history", e)
            }
        }
    }
    
    fun toggleMetrics() {
        android.util.Log.d("ChatViewModel", "toggleMetrics called, current showMetrics: ${_uiState.value.showMetrics}")
        _uiState.value = _uiState.value.copy(
            showMetrics = !_uiState.value.showMetrics
        )
        android.util.Log.d("ChatViewModel", "toggleMetrics completed, new showMetrics: ${_uiState.value.showMetrics}")
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun getMetrics(): InferenceMetrics? {
        return try {
            llm?.metrics
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error getting metrics", e)
            null
        }
    }

    fun copyModelFromUri(uri: android.net.Uri) {
        android.util.Log.d("ChatViewModel", "Starting model copy from URI: $uri")
        val modelDownloadUseCase = ModelDownloadUseCase(context)
        viewModelScope.launch {
            modelDownloadUseCase.copyModelFromUri(uri).collect { progress ->
                android.util.Log.d("ChatViewModel", "Copy progress: $progress")
                when (progress) {
                    is DownloadProgress.Started -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                    is DownloadProgress.Resume -> {
                        android.util.Log.d("ChatViewModel", "Resuming copy from ${progress.existingBytes} bytes")
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                    is DownloadProgress.Progress -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                    is DownloadProgress.Success -> {
                        android.util.Log.d("ChatViewModel", "Model copied successfully: ${progress.filePath}")
                        _uiState.value = _uiState.value.copy(
                            isModelReady = false, // Will be set to true after LLM initialization
                            downloadProgress = null // Clear download progress to show chat interface
                        )
                        initializeLLM()
                    }
                    is DownloadProgress.Error -> {
                        android.util.Log.e("ChatViewModel", "Model copy failed: ${progress.message}")
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress
                        )
                    }
                }
            }
        }
    }
}

data class ChatUiState(
    val history: List<Chat> = emptyList(),
    val currentOutput: String = "",
    val isGenerating: Boolean = false,
    val isModelReady: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val showMetrics: Boolean = false,
    val error: String? = null
) 