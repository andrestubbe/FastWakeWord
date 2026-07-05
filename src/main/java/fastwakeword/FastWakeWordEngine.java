package fastwakeword;

import fastaudioprocess.FastAudioProcess;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Template-Matching WakeWord Engine for the word "bot".
 * Uses Log-Mel Spectrograms and Sliding Window Matching.
 */
public final class FastWakeWordEngine {

    private final int sampleRate;          // 16000
    private final int frameSize;          // 160 Samples (10 ms)
    private final int melBands;           // e.g. 40
    private final int windowFrames;       // e.g. 30 Frames (~300 ms)
    private final float triggerThreshold; // e.g. 0.8

    private final float[][] windowBuffer; // [windowFrames][melBands]
    private final float[] floatFrame;     // Pre-allocated buffer to reduce GC pressure
    private int windowIndex = 0;
    private boolean windowFilled = false;

    private float[][] template;           // Reference Log-Mel for "bot"
    private float templateNorm;           // Pre-calculated norm of the template to save CPU
    private Consumer<Float> onScore;      // optional: Score Callback
    private Runnable onTrigger;           // Trigger Callback

    public FastWakeWordEngine(int sampleRate,
                              int frameSize,
                              int melBands,
                              int windowFrames,
                              float triggerThreshold) {
        this.sampleRate = sampleRate;
        this.frameSize = frameSize;
        this.melBands = melBands;
        this.windowFrames = windowFrames;
        this.triggerThreshold = triggerThreshold;
        this.windowBuffer = new float[windowFrames][melBands];
        this.floatFrame = new float[frameSize];
    }

    public void setTemplate(float[][] template) {
        // Expected Shape: [T][melBands]
        this.template = template;
        if (template != null) {
            double sum = 0.0;
            for (int t = 0; t < template.length; t++) {
                for (int f = 0; f < melBands; f++) {
                    float val = template[t][f];
                    sum += val * val;
                }
            }
            this.templateNorm = (float) sum;
        } else {
            this.templateNorm = 0.0f;
        }
    }

    public synchronized void resetState() {
        this.windowIndex = 0;
        this.windowFilled = false;
        for (int i = 0; i < windowFrames; i++) {
            Arrays.fill(windowBuffer[i], 0.0f);
        }
    }

    public void setOnTrigger(Runnable onTrigger) {
        this.onTrigger = onTrigger;
    }

    public void setOnScore(Consumer<Float> onScore) {
        this.onScore = onScore;
    }

    /**
     * Processes a single PCM frame (10 ms, 160 samples).
     */
    public void processFrame(short[] pcmFrame) {
        if (pcmFrame.length != frameSize) {
            throw new IllegalArgumentException("pcmFrame size must be " + frameSize);
        }

        // VAD Pre-gate: Quick energy threshold check to skip silence and reduce CPU
        long energy = 0;
        for (int i = 0; i < frameSize; i++) {
            energy += (long) pcmFrame[i] * pcmFrame[i];
        }
        if (energy < 4000) { // Cuts CPU significantly in silent/quiet environments
            return;
        }

        // Convert short PCM to normalized float [-1.0, 1.0] using pre-allocated buffer
        for (int i = 0; i < frameSize; i++) {
            floatFrame[i] = pcmFrame[i] / 32768.0f;
        }

        // Extract Log-Mel Spectrogram features for this single frame
        float[][] melResult = FastAudioProcess.logMelSpectrogram(
            floatFrame,
            sampleRate,
            frameSize, // fftSize
            frameSize, // hopSize
            melBands
        );

        if (melResult == null || melResult.length == 0) {
            return;
        }
        float[] mel = melResult[0];

        // Update Sliding Window Buffer
        windowBuffer[windowIndex] = mel;
        windowIndex = (windowIndex + 1) % windowFrames;
        if (windowIndex == 0) {
            windowFilled = true;
        }

        // Evaluate Similarity Score
        if (template != null && windowFilled) {
            float score = computeSimilarity(windowBuffer, template);
            if (onScore != null) {
                onScore.accept(score);
            }
            if (score >= triggerThreshold && onTrigger != null) {
                onTrigger.run();
            }
        }
    }

    /**
     * Extracts a template slice from a longer reference Log-Mel Spectrogram.
     */
    public float[][] extractTemplate(float[][] fullLogMel, int startFrame, int lengthFrames) {
        if (startFrame + lengthFrames > fullLogMel.length) {
            throw new IllegalArgumentException("Template range out of bounds");
        }
        float[][] tpl = new float[lengthFrames][melBands];
        for (int t = 0; t < lengthFrames; t++) {
            tpl[t] = Arrays.copyOf(fullLogMel[startFrame + t], melBands);
        }
        return tpl;
    }

    /**
     * Computes similarity between window and template using Normalized Cross-Correlation.
     */
    private float computeSimilarity(float[][] window, float[][] template) {
        int T = Math.min(window.length, template.length);
        int F = melBands;

        // Compute mean of window and template to center the signals around zero
        double sumW = 0.0;
        double sumT = 0.0;
        int count = T * F;
        for (int t = 0; t < T; t++) {
            float[] w = window[(windowIndex + t) % windowFrames];
            float[] tpl = template[t];
            for (int f = 0; f < F; f++) {
                sumW += w[f];
                sumT += tpl[f];
            }
        }
        double meanW = sumW / count;
        double meanT = sumT / count;

        double dot = 0.0;
        double normW = 0.0;
        double normT = 0.0;

        for (int t = 0; t < T; t++) {
            // Retrieve time-aligned window frames
            float[] w = window[(windowIndex + t) % windowFrames];
            float[] tpl = template[t];
            for (int f = 0; f < F; f++) {
                double a = w[f] - meanW;
                double b = tpl[f] - meanT;
                dot += a * b;
                normW += a * a;
                normT += b * b;
            }
        }

        if (normW == 0.0 || normT == 0.0) {
            return 0.0f;
        }
        return (float) (dot / (Math.sqrt(normW) * Math.sqrt(normT)));
    }

    /**
     * Averages multiple templates to create a more robust reference pattern.
     */
    public static float[][] averageTemplates(float[][][] templates) {
        if (templates == null || templates.length == 0) {
            throw new IllegalArgumentException("Templates array cannot be empty");
        }
        int T = templates[0].length;
        int F = templates[0][0].length;
        float[][] out = new float[T][F];
        int N = templates.length;

        for (int n = 0; n < N; n++) {
            for (int t = 0; t < T; t++) {
                for (int f = 0; f < F; f++) {
                    out[t][f] += templates[n][t][f];
                }
            }
        }
        for (int t = 0; t < T; t++) {
            for (int f = 0; f < F; f++) {
                out[t][f] /= N;
            }
        }
        return out;
    }
}
