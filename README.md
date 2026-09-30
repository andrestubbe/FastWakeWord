# FastWakeWord 0.1.2 [ALPHA-2026-09-30] — Ultra-Fast Native Wake-Word Detection for Java

[![Status](https://img.shields.io/badge/status-0.1.2-brightgreen.svg)](https://github.com/andrestubbe/FastWakeWord/releases/tag/0.1.2)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+%20%7C%20Linux%20%7C%20macOS-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-0.1.2-green.svg)](https://jitpack.io/#andrestubbe/FastWakeWord)

---

**⚡ A high-performance voice trigger module for the FastJava ecosystem. Accelerated wake-word detection via native SIMD pipelines.**

**FastWakeWord** provides real-time voice trigger detection with minimal CPU overhead. Built for AI agents, voice assistants, and hands-free desktop automation tools that require instant response to wake-word activation without heavy DNN runtimes.

Watch Demo (YouTube) | Watch JMH Benchmark (YouTube)

![FastWakeWord Showcase](docs/screenshot.png)

---

## Quick Start — Example

```java
import fastwakeword.FastWakeWordEngine;
import fastaudioprocess.FastAudioProcess;
import java.io.File;
import javax.sound.sampled.*;

public class Demo {
    public static void main(String[] args) throws Exception {
        int sampleRate = 16000;
        int frameSize = 160;   // 10 ms at 16 kHz
        int melBands = 40;
        int windowFrames = 30; // 300 ms sliding window
        float threshold = 0.75f;

        // 1. Initialize Ultra-Fast Wake-Word Engine
        FastWakeWordEngine engine = new FastWakeWordEngine(
            sampleRate, frameSize, melBands, windowFrames, threshold
        );

        // 2. Load Reference Wake-Word Template ("bot")
        File templateFile = new File("template_bot.wav");
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(templateFile)) {
            byte[] bytes = ais.readAllBytes();
            float[] samples = new float[bytes.length / 2];
            for (int i = 0; i < samples.length; i++) {
                short s = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
                samples[i] = s / 32768.0f;
            }
            float[][] templateLogMel = FastAudioProcess.logMelSpectrogram(
                samples, sampleRate, frameSize, frameSize, melBands
            );
            int startFrame = Math.max(0, (templateLogMel.length - windowFrames) / 2);
            engine.setTemplate(engine.extractTemplate(templateLogMel, startFrame, windowFrames));
        }

        // 3. Register Trigger & Confidence Callbacks
        engine.setOnTrigger(() -> System.out.println("⚡ Wake-word 'bot' detected!"));
        engine.setOnScore(score -> {
            if (score > 0.5f) System.out.printf("Match confidence: %.2f%n", score);
        });

        // 4. Feed live 10 ms PCM frames (e.g. from FastAudioCapture or microphone)
        short[] pcmFrame = new short[160];
        engine.processFrame(pcmFrame);
    }
}
```

---

## Table of Contents

- [Why FastWakeWord?](#why-fastwakeword)
- [Key Features](#key-features)
- [Real-World Use Cases](#real-world-use-cases)
- [Performance Benchmarks](#performance-benchmarks)
- [API Quick Reference](#api-quick-reference)
- [Technical Demos & Benchmarks](#technical-demos--benchmarks)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)
- [Related Projects](#related-projects)

---

## Why FastWakeWord?

Continuous, hands-free voice trigger detection ("Hey Assistant", "Computer", "Bot") is notoriously difficult in Java applications:

1. **Resource-Heavy Inference Engines**: Running full neural frameworks (like ONNX Runtime or TensorFlow Lite) 24/7 just to detect a single trigger word consumes 100–300 MB of RAM and constantly burdens laptop batteries.
2. **CPU Churn in Silent Environments**: Naive audio polling processes every microphone frame equally, executing expensive FFT and acoustic scoring algorithms even when the room is completely silent.
3. **Proprietary Vendor Lock-in**: Solutions like Picovoice Porcupine require online license activations, commercial contracts, and closed-source binaries.
4. **Garbage Collection Jitter**: Streaming live 16 kHz audio streams through high-level Java buffers triggers GC pauses that cause missed voice trigger events.

FastWakeWord provides an ultra-lightweight, offline template-matching engine powered by native SIMD Log-Mel spectrograms (`FastAudioProcess`). It includes energy-based Voice Activity Detection (VAD) gating, dropping CPU consumption to near zero during quiet periods:

| Feature | Porcupine (Picovoice) | OpenWakeWord (ONNX) | FastWakeWord |
|:---|:---|:---|:---|
| **Detection Engine** | Proprietary Neural Net | Heavy DNN / ONNX Model | **SIMD Log-Mel Template Matching** |
| **Idle CPU (Silence)** | 1–3% continuous | 3–8% continuous | **< 0.1% (VAD Energy Pre-gate)** |
| **RAM Footprint** | ~20–40 MB | 80–250 MB (ONNX Runtime) | **< 10 MB (Pure In-Memory)** |
| **Frame Latency** | ~5–12 ms | ~15–30 ms | **< 0.5 ms per 10 ms frame** |
| **Licensing / Privacy** | Commercial Key / Tracking | Open Source (Apache 2.0) | **100% MIT / Offline Local** |
| **Dependencies** | Proprietary C library | Heavy ONNX Runtime JARs | **Lightweight FastJava Core** |

---

## Key Features

- **🚀 Sub-Millisecond Frame Latency**: Processes 10 ms audio frames in under 0.5 ms via SIMD Log-Mel filters.
- **⚡ VAD Energy Pre-Gate**: Cuts CPU consumption to near zero during quiet or silent periods.
- **🔒 Zero-GC Hot Paths**: Reuses fixed audio and spectrogram arrays without heap churn.
- **🔋 Battery-Friendly Background Execution**: Ideal for 24/7 background desktop listeners and voice assistants.
- **📦 Part of FastJava**: Directly pairs with `FastAudioCapture`, `FastTTS`, and `FastSTT`.

---

## Real-World Use Cases

- 🗣️ **Autonomous AI Assistants**: Hands-free voice trigger activation for local desktop LLMs and agents.
- 🎙️ **Voice Command Gating**: Start recording speech to text (`FastSTT`) only when a trigger word is detected.
- 🎮 **Game & Accessibility Voice Triggers**: Low-latency voice push-to-talk or command triggers without game framerate stutter.
- 🏢 **Kiosk & Hands-Free Terminals**: Offline trigger-word detection without external internet connectivity or API charges.

---

## Performance Benchmarks

Measured using OpenJDK JMH microbenchmarks (16 kHz audio, 160-sample frames, 40 Mel bands):

| Benchmark Operation | Score (ops/ms) | Ops per Second | Memory Allocation |
|:---|:---:|:---:|:---:|
| **Frame Process (Energy VAD Filter)** | **> 2,500 ops/ms** | **> 2,500,000** | **0 bytes / op (Zero GC)** |
| **Log-Mel Template Matching** | **~480 ops/ms** | **~480,000** | **0 bytes / op (Zero GC)** |

*Run benchmarks locally:*
```powershell
.\run-benchmark.bat
```

---

## API Quick Reference

| Method / Signature | Return Type | Description | Docs |
|:---|:---|:---|:---|
| `FastWakeWordEngine(sampleRate, frameSize, melBands, windowFrames, threshold)` | `FastWakeWordEngine` | Constructs a new template-matching trigger engine. | [Wiki](docs/REFERENCE.md) |
| `setTemplate(float[][] template)` | `void` | Sets the reference Log-Mel acoustic template for matching. | [Wiki](docs/REFERENCE.md) |
| `processFrame(short[] pcmFrame)` | `void` | Processes a 10 ms PCM audio frame with VAD energy pre-check. | [Wiki](docs/REFERENCE.md) |
| `setOnTrigger(Runnable onTrigger)` | `void` | Registers callback executed when the trigger word is recognized. | [Wiki](docs/REFERENCE.md) |
| `setOnScore(Consumer<Float> onScore)` | `void` | Registers live confidence score callback for UI visualizers. | [Wiki](docs/REFERENCE.md) |
| `resetState()` | `void` | Resets sliding window state and historical match buffers. | [Wiki](docs/REFERENCE.md) |

---

## Technical Demos & Benchmarks

| Case | Java Example | Launcher | Description |
|:---|:---|:---|:---|
| **Interactive GUI Indicator** | [BotDetectorGUI.java](src/main/java/fastwakeword/BotDetectorGUI.java) | `run-detect.bat` | Minimalist dark-mode circle UI that flashes green on voice trigger. |
| **Voice Template Recorder** | [BotDetectorDemo.java](src/main/java/fastwakeword/BotDetectorDemo.java) | `run-record.bat` | Utility tool for recording reference wake-word WAV templates. |
| **JMH Microbenchmark Suite** | [Benchmark.java](examples/Benchmark/src/main/java/fastwakeword/benchmark/Benchmark.java) | `run-benchmark.bat` | Formal OpenJDK JMH throughput benchmark measuring frame processing. |

---

## Installation

### Option 1: Maven (Recommended via JitPack)

Add the JitPack repository and the dependencies to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- FastWakeWord Engine -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastWakeWord</artifactId>
        <version>0.1.2</version>
    </dependency>

    <!-- FastAudioProcess SIMD Feature Extraction -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastAudioProcess</artifactId>
        <version>0.1.1</version>
    </dependency>

    <!-- FastCore Unified Loader -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastCore</artifactId>
        <version>0.1.1</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastWakeWord:0.1.2'
    implementation 'com.github.andrestubbe:FastAudioProcess:0.1.1'
    implementation 'com.github.andrestubbe:FastCore:0.1.1'
}
```

---

## Documentation

- **[CHANGELOG.md](docs/CHANGELOG.md)**: Release notes and version history.
- **[REFERENCE.md](docs/REFERENCE.md)**: Full API contracts and mathematical model.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: Zero-allocation and energy VAD engineering rationale.
- **[COMPILE.md](docs/COMPILE.md)**: Build and execution guide.
- **[ROADMAP.md](docs/ROADMAP.md)**: Future milestones and planned features.

---

## Platform Support

| Platform | Architecture | Status | Notes |
|:---|:---|:---|:---|
| Windows 10/11 | x64 | ✅ Fully Supported | Native WASAPI audio capture & SIMD acceleration |
| Linux | x64, ARM64 | 🚧 Planned | ALSA audio capture integration |
| macOS | Apple Silicon, x64 | 🚧 Planned | CoreAudio integration |

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects

- [FastCore](https://github.com/andrestubbe/FastCore) — Native Library Loader for Java
- [FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) — High-Performance Native Audio Capture for Java
- [FastAudioProcess](https://github.com/andrestubbe/FastAudioProcess) — SIMD Audio Vector Processing & Log-Mel Spectrograms
- [FastAudioPlayer](https://github.com/andrestubbe/FastAudioPlayer) — Native Windows WASAPI Audio Playback for Java
- [FastTTS](https://github.com/andrestubbe/FastTTS) — High-Performance Native Windows TTS API for Java
- [FastSTT](https://github.com/andrestubbe/FastSTT) — Ultra-Fast Native Speech-to-Text for Java

---

Part of the FastJava Ecosystem — Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀⚡

