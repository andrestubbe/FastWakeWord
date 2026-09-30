# Building FastWakeWord from Source

## Prerequisites

- **JDK 17+** (Java 17 or Java 21 LTS recommended)
- **Maven 3.9+**
- **Git**

## Quick Build

FastWakeWord is a high-performance audio engine backed by `FastAudioProcess` SIMD vector acceleration.

```bash
# Clean and package FastWakeWord
mvn clean package -DskipTests
```

## Running the Demos

### 1. Interactive GUI Detection Demo
Launch the real-time visual indicator GUI (flashes green when trigger word is recognized):
```powershell
.\run-detect.bat
```

### 2. Audio Template Recorder
Record your reference wake-word template (`template_bot.wav`):
```powershell
.\run-record.bat
```

### 3. OpenJDK JMH Microbenchmarks
Measure template extraction and frame-matching throughput:
```powershell
.\run-benchmark.bat
```
