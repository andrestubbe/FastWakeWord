# FastWakeWord 0.1.2 [ALPHA] — Ultra-Fast Native Wake-Word Detection for Java

[![Status](https://img.shields.io/badge/status-0.1.2-brightgreen.svg)](https://github.com/andrestubbe/FastWakeWord/releases/tag/0.1.2)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-ready-green.svg)](https://jitpack.io/#andrestubbe/FastWakeWord)

---

**⚡ A high-performance native voice trigger module for the FastJava ecosystem. Accelerated wake-word detection via native AI pipelines.**

**FastWakeWord** provides real-time voice trigger detection with minimal CPU overhead. Built for AI agents and hands-free automation tools that require instant response to wake-word activation.

Watch Demo (YouTube) | Watch JMH Benchmark (YouTube)

[![FastKeyboard Showcase](docs/screenshot.png)](https://www.youtube.com/watch?v=BZsqQl7WqWk)

---

## Table of Contents

- [Why FastWakeWord?](#why-fastwakeword)
- [Quick Start](#quick-start)
- [Features](#features)
- [Installation](#installation)
- [License](#license)

---

## Why FastWakeWord?

Continuous, hands-free voice trigger detection ("Hey Assistant", "Computer", "Bot") is notoriously difficult in Java applications:

* **Resource-Heavy Inference Engines:** Running full neural frameworks (like ONNX Runtime or TensorFlow Lite) 24/7 just to detect a single trigger word consumes 100–300 MB of RAM and constantly burdens laptop batteries.
* **CPU Churn in Silent Environments:** Naive audio polling processes every microphone frame equally, executing expensive FFT and acoustic scoring algorithms even when the room is completely silent.
* **Proprietary Vendor Lock-in:** Solutions like Picovoice Porcupine require online license activations, commercial contracts, and closed-source binaries.
* **Garbage Collection Jitter:** Streaming live 16 kHz audio streams through high-level Java buffers triggers GC pauses that cause missed voice trigger events.

FastWakeWord provides an ultra-lightweight, offline template-matching engine powered by native SIMD Log-Mel spectrograms (`FastAudioProcess`). It includes energy-based Voice Activity Detection (VAD) gating, dropping CPU consumption to near zero during quiet periods.

| Feature | Porcupine (Picovoice) | OpenWakeWord (ONNX) | CMU Sphinx / Vosk | FastWakeWord |
| :--- | :--- | :--- | :--- | :--- |
| **Detection Method** | Proprietary Neural Net | Heavy DNN / ONNX Model | Acoustic HMM Model | **SIMD Log-Mel Template Matching** |
| **Idle CPU (Silence)** | 1–3% continuous | 3–8% continuous | 5–12% continuous | **< 0.1% (VAD Energy Pre-gate)** |
| **RAM Footprint** | ~20–40 MB | 80–250 MB (ONNX Runtime) | 150–300 MB | **< 10 MB (Pure In-Memory)** |
| **Frame Processing Latency** | ~5–12 ms | ~15–30 ms | ~40–80 ms | **< 0.5 ms per 10 ms frame** |
| **Licensing / Privacy** | Commercial Key / Tracking | Open Source (Apache 2.0) | Open Source (BSD/Apache) | **100% MIT / Offline Local** |
| **Dependencies** | Proprietary C library | Heavy ONNX Runtime JARs | Bulky native libraries | **Lightweight FastJava Core** |

---

## Features

- **🚀 Low Latency**: Native voice processing for instant trigger detection.
- **🧠 AI Powered**: Optimized native models for high accuracy and low overhead.
- **🔒 Zero GC Stalls**: Efficient audio buffer handling via JNI.
- **⚡ Raw Performance**: Designed for continuous background operation.

---

## Installation

### Option 1: Maven (Recommended)

Add the JitPack repository and the dependencies to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependencies>
   <dependency>
       <groupId>com.github.andrestubbe</groupId>
       <artifactId>fastwakeword</artifactId>
       <version>0.1.0</version>
   </dependency>
   <dependency>
       <groupId>com.github.andrestubbe</groupId>
       <artifactId>fastcore</artifactId>
       <version>0.1.0</version>
   </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.andrestubbe:fastwakeword:0.1.0'
    implementation 'com.github.andrestubbe:fastcore:0.1.0'
}
```

### Option 3: Direct Download (No Build Tool)

Download the latest JARs directly to add them to your classpath:

1. 📦 **[fastwakeword-0.1.0.jar](https://github.com/andrestubbe/FastWakeWord/releases/download/0.1.0/fastwakeword-0.1.0.jar)** (The Core Library)
2. 📦 **[fastcore-0.1.0.jar](https://github.com/andrestubbe/FastCore/releases/download/0.1.0/fastcore-0.1.0.jar)** (The Mandatory Native Loader)

---

## Documentation

* **[COMPILE.md](COMPILE.md)**: Full compilation guide (MSVC C++17 build chain + JNI Setup).
* **[REFERENCE.md](docs/REFERENCE.md)**: Full API descriptions, border configurations, and codepoint index.
* **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: The engineering rationale for zero-allocation performance.
* **[ROADMAP.md](docs/ROADMAP.md)**: Future milestones and planned features.

---

## Platform Support

| Platform      | Status            |
|---------------|-------------------|
| Windows 10/11 | ✅ Fully Supported |
| Linux         | 🚧 Planned        |
| macOS         | 🚧 Planned        |

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects

- [FastCore](https://github.com/andrestubbe/FastCore) — Native Library Loader for Java
- [FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) — High-Performance Native Audio Capture for Java
- [FastAudioPlayer](https://github.com/andrestubbe/FastAudioPlayer) — Native Windows WASAPI Audio Playback for Java
- [FastTTS](https://github.com/andrestubbe/FastTTS) — High-Performance Native Windows TTS API for Java
- [FastSTT](https://github.com/andrestubbe/FastSTT) — Ultra-Fast Native Speech-to-Text for Java

---
**Part of the FastJava Ecosystem** — *Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀⚡*
