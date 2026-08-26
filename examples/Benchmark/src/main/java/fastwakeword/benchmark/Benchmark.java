package fastwakeword.benchmark;

import fastwakeword.FastWakeWordEngine;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class Benchmark {

    private FastWakeWordEngine engine;
    private short[] pcmFrame;

    @Setup
    public void setup() {
        int sampleRate = 16000;
        int frameSize = 160;
        int melBands = 40;
        int windowFrames = 30;
        float triggerThreshold = 0.8f;

        engine = new FastWakeWordEngine(sampleRate, frameSize, melBands, windowFrames, triggerThreshold);

        // Dummy template
        float[][] template = new float[windowFrames][melBands];
        for (int i = 0; i < windowFrames; i++) {
            for (int j = 0; j < melBands; j++) {
                template[i][j] = (float) Math.sin(i + j);
            }
        }
        engine.setTemplate(template);

        pcmFrame = new short[frameSize];
        for (int i = 0; i < frameSize; i++) {
            pcmFrame[i] = (short) (Math.sin(i * 0.1) * 10000);
        }
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void benchmarkProcessFrame() {
        engine.processFrame(pcmFrame);
    }
}
