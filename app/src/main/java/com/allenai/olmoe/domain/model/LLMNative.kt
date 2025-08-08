package com.allenai.olmoe.domain.model

import android.util.Log

/**
 * Native interface for LLaMA.cpp model inference
 * Provides JNI bindings to the actual LLaMA model
 */
class LLMNative {
    
    companion object {
        private const val TAG = "LLMNative"
        
        init {
            try {
                Log.d(TAG, "Attempting to load llama_jni library...")
                System.loadLibrary("llama_jni")
                Log.d(TAG, "LLaMA native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load LLaMA native library", e)
                // Try alternative library names
                try {
                    Log.d(TAG, "Attempting to load llama library as fallback...")
                    System.loadLibrary("llama")
                    Log.d(TAG, "LLaMA library loaded as fallback")
                } catch (e2: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load LLaMA library as fallback", e2)
                    // Try to list available libraries
                    try {
                        val libDir = System.getProperty("java.library.path")
                        Log.d(TAG, "Library path: $libDir")
                    } catch (e3: Exception) {
                        Log.e(TAG, "Failed to get library path", e3)
                    }
                }
            }
        }
    }
    
    /**
     * Callback interface for receiving generated tokens
     */
    interface TokenCallback {
        fun onToken(token: String)
    }
    
    /**
     * Initialize the LLaMA model
     * @param modelPath Path to the .gguf model file
     * @return true if initialization successful, false otherwise
     */
    external fun initModel(modelPath: String): Boolean
    
    /**
     * Tokenize input text
     * @param text Input text to tokenize
     * @return Array of token IDs, or null if failed
     */
    external fun tokenize(text: String): IntArray?
    
    /**
     * Convert a single token ID to text
     * @param token Token ID to convert
     * @return Text representation of the token
     */
    external fun detokenize(token: Int): String?
    
    /**
     * Generate response from input tokens
     * @param tokens Input token array
     * @param callback Optional callback for receiving tokens as they're generated
     * @return Complete generated response, or null if failed
     */
    external fun generateResponse(tokens: IntArray, callback: TokenCallback? = null): String?
    
    /**
     * Clean up native resources
     */
    external fun cleanup()
    
    /**
     * Check if the model is initialized
     */
    fun isInitialized(): Boolean {
        return try {
            // Try a simple operation to check if model is loaded
            tokenize("test") != null
        } catch (e: Exception) {
            Log.e(TAG, "Model not initialized", e)
            false
        }
    }
}
