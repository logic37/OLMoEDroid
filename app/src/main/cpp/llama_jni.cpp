#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include <atomic>
#include <memory>
#include <vector>

// Include llama.cpp headers
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "llama_jni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "llama_jni", __VA_ARGS__)

// Global state
static std::mutex g_mutex;
static std::atomic<bool> g_initialized{false};
static llama_context* g_ctx = nullptr;
static llama_model* g_model = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_allenai_olmoe_domain_model_LLMNative_initModel(
        JNIEnv* env, jobject thiz, jstring model_path) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (g_initialized.load()) {
        LOGI("Model already initialized");
        return JNI_TRUE;
    }
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing model from: %s", path);
    
    // Initialize llama.cpp
    LOGI("Initializing llama backend");
    llama_backend_init();
    LOGI("Backend initialized successfully");
    
    // Load model
    LOGI("Loading model with default parameters");
    llama_model_params model_params = llama_model_default_params();
    LOGI("Model params - n_gpu_layers: %d, main_gpu: %d, tensor_split: %p, vocab_only: %s, use_mmap: %s, use_mlock: %s",
         model_params.n_gpu_layers, model_params.main_gpu, model_params.tensor_split,
         model_params.vocab_only ? "true" : "false",
         model_params.use_mmap ? "true" : "false",
         model_params.use_mlock ? "true" : "false");
    
    g_model = llama_model_load_from_file(path, model_params);
    
    if (g_model == nullptr) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }
    
    LOGI("Model loaded successfully");
    
    // Create context
    LOGI("Creating context with default parameters");
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;  // Context window
    ctx_params.n_threads = 4;  // Number of threads
    ctx_params.n_batch = 512;  // Batch size for prompt processing
    
    LOGI("Context params - n_ctx: %d, n_threads: %d, n_batch: %d",
         ctx_params.n_ctx, ctx_params.n_threads, ctx_params.n_batch);
    
    g_ctx = llama_init_from_model(g_model, ctx_params);
    
    if (g_ctx == nullptr) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }
    
    LOGI("Context created successfully");
    
    // Test the context by getting vocabulary info
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    if (vocab != nullptr) {
        LOGI("Vocabulary loaded - n_tokens: %d", llama_vocab_n_tokens(vocab));
        LOGI("EOS token: %d", llama_vocab_eos(vocab));
    } else {
        LOGE("Failed to get vocabulary");
    }
    
    g_initialized.store(true);
    LOGI("Model initialized successfully");
    
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_allenai_olmoe_domain_model_LLMNative_tokenize(
        JNIEnv* env, jobject thiz, jstring text) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_initialized.load() || g_ctx == nullptr || g_model == nullptr) {
        LOGE("Model not initialized");
        return nullptr;
    }
    
    const char* input_text = env->GetStringUTFChars(text, nullptr);
    LOGI("Tokenizing text: %s", input_text);
    
    // Get vocabulary from model
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    
    // Tokenize the input
    std::vector<llama_token> tokens;
    tokens.resize(1024); // Reserve space for tokens
    
    int n_tokens = llama_tokenize(vocab, input_text, strlen(input_text), tokens.data(), tokens.size(), true, true);
    
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        env->ReleaseStringUTFChars(text, input_text);
        return nullptr;
    }
    
    // Create Java array
    jintArray result = env->NewIntArray(n_tokens);
    env->SetIntArrayRegion(result, 0, n_tokens, reinterpret_cast<jint*>(tokens.data()));
    
    LOGI("Tokenized %d tokens", n_tokens);
    env->ReleaseStringUTFChars(text, input_text);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_allenai_olmoe_domain_model_LLMNative_detokenize(
        JNIEnv* env, jobject thiz, jint token) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_initialized.load() || g_ctx == nullptr || g_model == nullptr) {
        LOGE("Model not initialized");
        return env->NewStringUTF("");
    }
    
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    char text[256];
    int text_len = llama_token_to_piece(vocab, token, text, sizeof(text), 0, false);
    
    if (text_len < 0) {
        LOGE("Detokenization failed");
        return env->NewStringUTF("");
    }
    
    text[text_len] = '\0';
    return env->NewStringUTF(text);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_allenai_olmoe_domain_model_LLMNative_generateResponse(
        JNIEnv* env, jobject thiz, jintArray tokens, jobject callback) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_initialized.load() || g_ctx == nullptr) {
        LOGE("Model not initialized");
        return env->NewStringUTF("Error: Model not initialized");
    }
    
    // Validate context
    if (!g_ctx || !g_model) {
        LOGE("Context or model not initialized");
        return env->NewStringUTF("Error: Model not initialized");
    }
    
    // Reset context to ensure clean state
    llama_kv_self_clear(g_ctx);
    LOGI("Cleared KV cache for clean context state");
    
    LOGI("Starting generation with clean context state");
    
    // Get token array
    jsize token_count = env->GetArrayLength(tokens);
    jint* token_array = env->GetIntArrayElements(tokens, nullptr);
    
    // Convert to vector
    std::vector<llama_token> input_tokens(token_array, token_array + token_count);
    env->ReleaseIntArrayElements(tokens, token_array, JNI_ABORT);
    
    LOGI("Generating response for %d tokens", token_count);
    LOGI("Model context: %p, Model: %p", g_ctx, g_model);
    
    // Validate tokens
    if (input_tokens.empty()) {
        LOGE("No input tokens provided");
        return env->NewStringUTF("Error: No input tokens");
    }
    
    // Log first few tokens for debugging
    LOGI("First 3 tokens: %d, %d, %d", 
         input_tokens.size() > 0 ? input_tokens[0] : -1,
         input_tokens.size() > 1 ? input_tokens[1] : -1,
         input_tokens.size() > 2 ? input_tokens[2] : -1);
    
    // Create batch for input tokens using llama_batch_init
    llama_batch batch = llama_batch_init(input_tokens.size(), 0, 1);
    LOGI("Created batch with %d tokens", batch.n_tokens);
    
    // Ensure batch is properly initialized
    if (!batch.token) {
        LOGE("llama_batch_init failed!");
        return env->NewStringUTF("Error: Batch initialization failed");
    }
    
    // Set the actual number of tokens in the batch
    batch.n_tokens = input_tokens.size();
    
    // Add tokens to batch
    for (size_t i = 0; i < input_tokens.size(); i++) {
        if (input_tokens[i] >= 0) {
            batch.token[i] = input_tokens[i];
            batch.pos[i] = i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = false;
            LOGI("Added token %d at position %d", input_tokens[i], (int)i);
        } else {
            LOGE("Invalid token: %d", input_tokens[i]);
            llama_batch_free(batch);
            return env->NewStringUTF("Error: Invalid token");
        }
    }
    
    // Enable logits for the last token
    if (batch.n_tokens > 0) {
        batch.logits[batch.n_tokens - 1] = true;
    }
    
    LOGI("About to call llama_decode with batch size %d", batch.n_tokens);
    
    // Evaluate input tokens
    int decode_result = llama_decode(g_ctx, batch);
    if (decode_result != 0) {
        LOGE("Failed to decode input tokens, error code: %d", decode_result);
        LOGE("Context state - initialized: %s, ctx: %p, model: %p", 
             g_initialized.load() ? "true" : "false", g_ctx, g_model);
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Failed to process input");
    }
    
    LOGI("Successfully decoded input tokens");
    
    // Generate response
    std::string response;
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    llama_token eos_token = llama_vocab_eos(vocab);
    
    LOGI("Starting generation loop, EOS token: %d", eos_token);
    
    for (int i = 0; i < 100; i++) { // Limit to 100 tokens
        // Sample next token
        llama_token_data_array candidates = {0};
        candidates.data = new llama_token_data[llama_vocab_n_tokens(vocab)];
        candidates.size = llama_vocab_n_tokens(vocab);
        candidates.sorted = false;
        
        // Get logits
        float* logits = llama_get_logits(g_ctx);
        for (int j = 0; j < candidates.size; j++) {
            candidates.data[j].id = j;
            candidates.data[j].logit = logits[j];
            candidates.data[j].p = 0.0f;
        }
        
        // Simple sampling (greedy)
        llama_token new_token_id = candidates.data[0].id;
        for (int j = 1; j < candidates.size; j++) {
            if (candidates.data[j].logit > candidates.data[new_token_id].logit) {
                new_token_id = candidates.data[j].id;
            }
        }
        
        delete[] candidates.data;
        
        // Check for EOS
        if (new_token_id == eos_token) {
            LOGI("Reached EOS token, stopping generation");
            break;
        }
        
        // Convert token to text
        char text[256];
        int text_len = llama_token_to_piece(vocab, new_token_id, text, sizeof(text), 0, false);
        if (text_len > 0) {
            text[text_len] = '\0';
            response += text;
            
            // Call callback if provided
            if (callback != nullptr) {
                jclass callback_class = env->GetObjectClass(callback);
                jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
                if (on_token_method != nullptr) {
                    jstring token_text = env->NewStringUTF(text);
                    env->CallVoidMethod(callback, on_token_method, token_text);
                    env->DeleteLocalRef(token_text);
                }
                env->DeleteLocalRef(callback_class);
            }
        }
        
        // Create batch for next token
        llama_batch next_batch = llama_batch_init(1, 0, 1);
        next_batch.n_tokens = 1;  // Set the actual number of tokens
        next_batch.token[0] = new_token_id;
        next_batch.pos[0] = input_tokens.size() + i;
        next_batch.n_seq_id[0] = 1;
        next_batch.seq_id[0][0] = 0;
        next_batch.logits[0] = 1; // true as int8_t
        
        LOGI("Created next batch with %d tokens, token: %d", next_batch.n_tokens, new_token_id);
        
        // Evaluate next token
        decode_result = llama_decode(g_ctx, next_batch);
        if (decode_result != 0) {
            LOGE("Failed to decode next token, error code: %d", decode_result);
            LOGE("Context state - initialized: %s, ctx: %p, model: %p", 
                 g_initialized.load() ? "true" : "false", g_ctx, g_model);
            llama_batch_free(next_batch);
            break;
        }
        
        llama_batch_free(next_batch);
        
        LOGI("Successfully generated token: %d, response so far: %s", new_token_id, response.c_str());
    }
    
    llama_batch_free(batch);
    
    LOGI("Generated response: %s", response.c_str());
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_allenai_olmoe_domain_model_LLMNative_cleanup(
        JNIEnv* env, jobject thiz) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    llama_backend_free();
    g_initialized.store(false);
    LOGI("Cleanup completed");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_allenai_olmoe_domain_model_LLMNative_isInitialized(
        JNIEnv* env, jobject thiz) {
    
    return g_initialized.load() ? JNI_TRUE : JNI_FALSE;
}
