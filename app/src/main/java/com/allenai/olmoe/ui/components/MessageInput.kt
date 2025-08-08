package com.allenai.olmoe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = if (isGenerating) "AI is thinking..." else "Type a message...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            enabled = !isGenerating,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    android.util.Log.d("MessageInput", "Keyboard send triggered with value: '$value'")
                    if (value.isNotBlank() && !isGenerating) {
                        android.util.Log.d("MessageInput", "Calling onSend()")
                        onSend()
                    } else {
                        android.util.Log.d("MessageInput", "Send blocked - blank: ${value.isBlank()}, generating: $isGenerating")
                    }
                }
            ),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        
        FloatingActionButton(
            onClick = {
                android.util.Log.d("MessageInput", "FAB clicked - isGenerating: $isGenerating, value: '$value'")
                if (isGenerating) {
                    onStop()
                } else {
                    if (value.isNotBlank()) {
                        android.util.Log.d("MessageInput", "FAB calling onSend()")
                        onSend()
                    } else {
                        android.util.Log.d("MessageInput", "FAB send blocked - value is blank")
                    }
                }
            },
            modifier = Modifier.size(48.dp),
            containerColor = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.Send,
                contentDescription = if (isGenerating) "Stop generation" else "Send message"
            )
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
} 