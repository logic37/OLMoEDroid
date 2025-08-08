package com.allenai.olmoe.domain.model

import com.allenai.olmoe.data.model.Chat

/**
 * Template for formatting conversations for different LLM architectures
 * Similar to the Swift Template implementation
 */
data class Template(
    val prefix: String = "",
    val system: Attachment = Attachment("", ""),
    val user: Attachment = Attachment("", ""),
    val bot: Attachment = Attachment("", ""),
    val stopSequence: String? = null,
    val systemPrompt: String? = null,
    val shouldDropLast: Boolean = false
) {
    data class Attachment(
        val prefix: String,
        val suffix: String
    )
    
    /**
     * Preprocess input and history into model-ready prompt
     * @param input Current user input
     * @param history Previous conversation messages
     * @param savedState Whether there's saved state (for continuation)
     * @return Formatted string ready for model inference
     */
    fun preprocess(input: String, history: List<Chat>, savedState: Boolean = false): String {
        return if (savedState) {
            // If state is restored, only preprocess the new input
            var processed = prefix
            processed += "${user.prefix}$input${user.suffix}"
            processed += bot.prefix
            processed
        } else {
            // Full preprocessing for the first input or reset state
            var processed = prefix
            
            if (systemPrompt != null) {
                processed += "${system.prefix}$systemPrompt${system.suffix}"
            }
            
            for (chat in history) {
                when (chat.role) {
                    com.allenai.olmoe.data.model.Role.USER -> {
                        processed += "${user.prefix}${chat.content}${user.suffix}"
                    }
                    com.allenai.olmoe.data.model.Role.BOT -> {
                        processed += "${bot.prefix}${chat.content}${bot.suffix}"
                    }
                }
            }
            
            // Add the current user input
            processed += "${user.prefix}$input${user.suffix}"
            
            // Handle bot prefix for the new response
            if (shouldDropLast) {
                processed += bot.prefix.dropLast(1)
            } else {
                processed += bot.prefix
            }
            
            processed
        }
    }
    
    companion object {
        /**
         * Creates a template for OLMoE-style models
         * @param systemPrompt Optional system message for context
         * @return Template configured for OLMoE format
         */
        fun OLMoE(systemPrompt: String? = null): Template {
            return Template(
                prefix = "<|endoftext|>",
                system = Attachment("<|system|>\n", "\n"),
                user = Attachment("<|user|>\n", "\n"),
                bot = Attachment("<|assistant|>\n", "\n"),
                stopSequence = "<|endoftext|>",
                systemPrompt = systemPrompt
            )
        }
        
        /**
         * Creates a template for ChatML format
         * @param systemPrompt Optional system message for context
         * @return Template configured for ChatML format
         */
        fun chatML(systemPrompt: String? = null): Template {
            return Template(
                system = Attachment("<|im_start|>system\n", "<|im_end|>\n"),
                user = Attachment("<|im_start|>user\n", "<|im_end|>\n"),
                bot = Attachment("<|im_start|>assistant\n", "<|im_end|>\n"),
                stopSequence = "<|im_end|>",
                systemPrompt = systemPrompt
            )
        }
        
        /**
         * Creates a template for Alpaca-style models
         * @param systemPrompt Optional system message for context
         * @return Template configured for Alpaca format
         */
        fun alpaca(systemPrompt: String? = null): Template {
            return Template(
                system = Attachment("", "\n\n"),
                user = Attachment("### Instruction:\n", "\n\n"),
                bot = Attachment("### Response:\n", "\n\n"),
                stopSequence = "###",
                systemPrompt = systemPrompt
            )
        }
        
        /**
         * Creates a template for LLaMA-style models
         * @param systemPrompt Optional system message for context
         * @return Template configured for LLaMA format
         */
        fun llama(systemPrompt: String? = null): Template {
            return Template(
                prefix = "[INST] ",
                system = Attachment("<<SYS>>\n", "\n<</SYS>>\n\n"),
                user = Attachment("", " [/INST]"),
                bot = Attachment(" ", "</s><s>[INST] "),
                stopSequence = "</s>",
                systemPrompt = systemPrompt,
                shouldDropLast = true
            )
        }
        
        /**
         * Template configured for Mistral-style models
         */
        val mistral = Template(
            user = Attachment("[INST] ", " [/INST]"),
            bot = Attachment("", "</s> "),
            stopSequence = "</s>",
            systemPrompt = null
        )
    }
}
