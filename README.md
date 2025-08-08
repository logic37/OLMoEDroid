
<div align="center">
  <br>
  <h1>Ai2 OLMoE</h1>
</div>
<p align="center">
  <a href="https://github.com/allenai/OLMo/blob/main/LICENSE">
    <img alt="GitHub License" src="https://img.shields.io/github/license/allenai/OLMo">
  </a>
  <a href="https://playground.allenai.org">
    <img alt="Playground" src="https://img.shields.io/badge/Ai2-Playground-F0529C">
  </a>
  <a href="https://discord.gg/sZq3jTNVNG">
    <img alt="Discord" src="https://img.shields.io/badge/Discord%20-%20blue?style=flat&logo=discord&label=Ai2&color=%235B65E9">
  </a>
</p>

# OLMoEDroid
Started as a port of OLMoE Swift app, with some extra functionallity. Build from scratch to support easy Inference of OLMoE in android phones by download or selecting the model.

OLMoEDroid, supports Downloading the model with resuming functionality from (https://dolma-artifacts.org/app/OLMoE-latest.gguf). Also supports selecting the model from local storage. 
Alfter loading the model, you can ask a question, see the tokens generated with metrics, and even clear the history. 

## Getting started with OLMoE app

1. **Open the app** – Launch OLMoE app on your device.
2. **Download the model** – The app may prompt you to download the required model files or select the model to load. Just follow the instructions and wait for the download to complete. The app supports resume option.
3. **Ask questions** – Once the model is ready, just type your question and OLMoE will answer you.



## OLMoEDroid

Clone the repository in your respective directory by Android Studio

``` sh
git clone https://github.com/logic37/OLMoEDroid.git
```

### Building the app to run on Android Studio 

1) Open the project in Android Studio.

2) Ensure that you have Llama.cpp and GGML library binaries for arm64 or build them by downloading llama.cpp and build for arm.

3) Run the project

*Running the app on the simulator may be tricky. 

<strong>For quick access of the app there is a pre-build release in app/release/app-release.apk ready to run </strong>

## License

This project is open source. See [LICENSE](LICENSE) for more information.

## Open Source Dependencies

This project relies on the following open-source libraries, each licensed under the **MIT License**:

### [LlamaCPP](https://github.com/ggerganov/llama.cpp)

- **Author(s):** The ggml authors (2023-2024)
- **License:** MIT
- **Repository:** [LlamaCPP](https://github.com/ggerganov/llama.cpp)

### [ggml](https://github.com/ggerganov/ggml)

- **Author(s):** The ggml authors (2023-2024)
- **License:** MIT
- **Repository:** [ggml](https://github.com/ggerganov/ggml)


For more details on each license, visit the respective repositories linked above.
