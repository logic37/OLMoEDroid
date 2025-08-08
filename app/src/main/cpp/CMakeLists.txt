cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

# Set the ABI directory
set(ABI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Check if libraries exist
if(NOT EXISTS ${ABI_DIR}/libllama.so)
    message(FATAL_ERROR "libllama.so not found in ${ABI_DIR}. Please build llama.cpp for ${ANDROID_ABI} first.")
endif()

if(NOT EXISTS ${ABI_DIR}/libggml.so)
    message(FATAL_ERROR "libggml.so not found in ${ABI_DIR}. Please build llama.cpp for ${ANDROID_ABI} first.")
endif()

# Add llama.cpp library
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${ABI_DIR}/libllama.so)

# Add ggml library
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION ${ABI_DIR}/libggml.so)

# Create the native library
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Link against system libraries and llama.cpp
target_link_libraries(llama_jni
    android
    log
    atomic
    m
    llama
    ggml
)

# Set include directories
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ABI_DIR}
)

# Set compiler flags for proper alignment
target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fPIC
    -DGGML_USE_ACCELERATE=0
    -DGGML_USE_OPENBLAS=0
    -DGGML_USE_CUBLAS=0
    -DGGML_USE_CLBLAST=0
    -DGGML_USE_METAL=0
    -DGGML_USE_MPI=0
    -DGGML_USE_LLAMACPP=1

    -DGGML_USE_OPENMP=0
    -DLLAMA_USE_OPENMP=0
)

# Set linker flags for proper alignment
set_target_properties(llama_jni PROPERTIES
    LINK_FLAGS "-Wl,--hash-style=both"
)

# Set proper alignment for the shared library
target_link_options(llama_jni PRIVATE
    -Wl,--hash-style=both
    -Wl,--no-undefined
    -Wl,--gc-sections
    -Wl,--as-needed
    -Wl,--strip-all
    -Wl,--build-id=none
    -Wl,--exclude-libs,ALL
    -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/version.script
)

# Create version script for symbol visibility
file(WRITE ${CMAKE_CURRENT_SOURCE_DIR}/version.script
"{
    global:
        Java_com_allenai_olmoe_domain_model_LLMNative_*;
    local:
        *;
};"
)
