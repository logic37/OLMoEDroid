package com.allenai.olmoe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
// import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.allenai.olmoe.data.model.Chat
import com.allenai.olmoe.data.model.Role
import com.allenai.olmoe.presentation.viewmodel.ChatViewModel
import com.allenai.olmoe.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel = remember { ChatViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsState()
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            android.util.Log.d("MainScreen", "File selected: $selectedUri")
            viewModel.copyModelFromUri(selectedUri)
        }
    }
    
    // Debug logging for UI state changes
    LaunchedEffect(uiState.history.size) {
        android.util.Log.d("MainScreen", "History size changed to: ${uiState.history.size}")
        android.util.Log.d("MainScreen", "History content: ${uiState.history.map { "${it.role}: ${it.content.take(50)}" }}")
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    var messageInput by remember { mutableStateOf("") }
    
    LaunchedEffect(uiState.history.size) {
        if (uiState.history.isNotEmpty()) {
            listState.animateScrollToItem(uiState.history.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("OLMoE")
                },
                actions = {
                    // Metrics toggle
                    IconButton(
                        onClick = { viewModel.toggleMetrics() }
                    ) {
                        Icon(
                            imageVector = if (uiState.showMetrics) Icons.Default.Analytics else Icons.Outlined.Analytics,
                            contentDescription = "Toggle metrics"
                        )
                    }
                    
                    // Clear chat
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        enabled = uiState.history.isNotEmpty() && !uiState.isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear chat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show model download view if model is not ready OR if download is in progress
            if (!uiState.isModelReady || uiState.downloadProgress != null) {
                android.util.Log.d("MainScreen", "Showing download view - isModelReady: ${uiState.isModelReady}, downloadProgress: ${uiState.downloadProgress}")
                // Show model download view
                ModelDownloadView(
                    downloadProgress = uiState.downloadProgress,
                    onDownloadClick = { viewModel.downloadModel() },
                    onSelectFileClick = {
                        android.util.Log.d("MainScreen", "Launching file picker")
                        filePickerLauncher.launch("*/*")
                    }
                )
            } else {
                android.util.Log.d("MainScreen", "Showing chat interface - isModelReady: ${uiState.isModelReady}")
                // Show chat interface
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Metrics view (if enabled)
                    android.util.Log.d("MainScreen", "showMetrics state: ${uiState.showMetrics}")
                    if (uiState.showMetrics) {
                        android.util.Log.d("MainScreen", "Showing metrics view")
                        val metrics = viewModel.getMetrics()
                        android.util.Log.d("MainScreen", "Metrics value: $metrics")
                        if (metrics != null) {
                            MetricsView(metrics = metrics)
                        } else {
                            Text(
                                text = "No metrics available",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        android.util.Log.d("MainScreen", "Metrics view hidden")
                    }
                    
                    // Chat messages
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        android.util.Log.d("MainScreen", "Rendering ${uiState.history.size} messages in LazyColumn")
                        items(uiState.history) { message ->
                            android.util.Log.d("MainScreen", "Rendering message: ${message.role} - ${message.content.take(50)}")
                            when (message.role) {
                                Role.USER -> {
                                    UserChatBubble(message = message)
                                }
                                Role.BOT -> {
                                    BotChatBubble(
                                        message = message,
                                        isGenerating = false
                                    )
                                }
                            }
                        }
                        
                        // Show typing indicator if generating
                        if (uiState.isGenerating) {
                            item {
                                if (uiState.currentOutput.isNotEmpty()) {
                                    // Show partial response
                                    BotChatBubble(
                                        message = Chat(
                                            role = Role.BOT,
                                            content = uiState.currentOutput
                                        ),
                                        isGenerating = true
                                    )
                                } else {
                                    // Show typing indicator
                                    TypingIndicator()
                                }
                            }
                        }
                    }
                    
                    // Message input
                    MessageInput(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        onSend = {
                            android.util.Log.d("MainScreen", "onSend called with messageInput: '$messageInput'")
                            if (messageInput.isNotBlank()) {
                                android.util.Log.d("MainScreen", "Calling viewModel.sendMessage()")
                                viewModel.sendMessage(messageInput)
                                messageInput = ""
                            } else {
                                android.util.Log.d("MainScreen", "Message is blank, not sending")
                            }
                        },
                        onStop = {
                            android.util.Log.d("MainScreen", "onStop called")
                            viewModel.stopGeneration()
                        },
                        isGenerating = uiState.isGenerating
                    )
                }
            }
            
            // Error dialog
            uiState.error?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Error") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
} 