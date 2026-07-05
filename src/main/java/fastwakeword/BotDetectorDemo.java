package fastwakeword;

import fastaudioprocess.FastAudioProcess;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.*;

/**
 * Demo for FastWakeWord template matching:
 * 1. Loads a template WAV file containing the word "bot".
 * 2. Loads a test WAV file.
 * 3. Feeds frames to FastWakeWordEngine and reports detections.
 */
public final class BotDetectorDemo {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--record")) {
            String filename = "template_bot.wav";
            if (args.length > 1 && !args[1].isEmpty() && !args[1].equals("--help")) {
                filename = args[1];
            }
            recordTemplate(filename);
            System.exit(0);
        }

        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("  1. Record template:  mvn exec:exec -Dexec.args=\"--record\"");
            System.out.println("  2. Test detection:  mvn exec:exec -Dexec.args=\"template_bot.wav <test_audio.wav>\"");
            System.exit(1);
        }

        String[] templatePaths = args[0].split(",");
        File testFile = new File(args[1]);

        int sampleRate = 16000;
        int frameSize = 160;
        int melBands = 40;
        int windowFrames = 30; // 300 ms window
        float threshold = 0.75f;

        try {
            FastWakeWordEngine engine = new FastWakeWordEngine(
                sampleRate,
                frameSize,
                melBands,
                windowFrames,
                threshold
            );

            float[][][] templates = new float[templatePaths.length][windowFrames][melBands];
            for (int i = 0; i < templatePaths.length; i++) {
                File templateFile = new File(templatePaths[i]);
                System.out.println("Loading template WAV: " + templateFile.getAbsolutePath());
                float[] templateSamples = readWavSamples(templateFile, sampleRate);
                
                float[][] templateLogMel = FastAudioProcess.logMelSpectrogram(
                    templateSamples,
                    sampleRate,
                    frameSize,
                    frameSize,
                    melBands
                );

                if (templateLogMel.length < windowFrames) {
                    throw new IllegalArgumentException("Template file " + templateFile.getName() + " is too short (needs to be at least " + (windowFrames * 10) + " ms).");
                }

                int startFrame = Math.max(0, (templateLogMel.length - windowFrames) / 2);
                templates[i] = engine.extractTemplate(templateLogMel, startFrame, windowFrames);
            }

            // Average all templates to create a robust pattern
            float[][] botTemplate = FastWakeWordEngine.averageTemplates(templates);
            engine.setTemplate(botTemplate);
            System.out.printf("Initialized engine with averaged template of %d reference files.%n", templates.length);

            engine.setOnScore(score -> {
                if (score > 0.5f) {
                    System.out.printf("[DEBUG] Active Match Score: %.3f%n", score);
                }
            });

            engine.setOnTrigger(() -> {
                System.out.println("\n>>> WAKE WORD DETECTED: 'bot' <<<\n");
            });

            System.out.println("\nProcessing test WAV: " + testFile.getAbsolutePath());
            float[] testSamples = readWavSamples(testFile, sampleRate);
            System.out.printf("Loaded %d test samples at 16kHz.%n", testSamples.length);

            // Convert to short PCM to match the processFrame signature
            short[] testPcm = new short[testSamples.length];
            for (int i = 0; i < testSamples.length; i++) {
                testPcm[i] = (short) Math.max(-32768, Math.min(32767, (int) (testSamples[i] * 32767.0f)));
            }

            int totalFrames = testPcm.length / frameSize;
            System.out.printf("Streaming %d frames of 10ms through the engine...%n", totalFrames);

            for (int i = 0; i < totalFrames; i++) {
                short[] frame = new short[frameSize];
                System.arraycopy(testPcm, i * frameSize, frame, 0, frameSize);
                engine.processFrame(frame);
            }

            System.out.println("Processing complete.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static float[] readWavSamples(File wavFile, int expectedSampleRate) throws Exception {
        if (!wavFile.exists()) {
            throw new FileNotFoundException("WAV file not found: " + wavFile.getAbsolutePath());
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = ais.getFormat();
            byte[] bytes = ais.readAllBytes();
            
            int channels = format.getChannels();
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            int numSamples = bytes.length / (channels * bytesPerSample);
            float[] samples = new float[numSamples];
            
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            
            for (int i = 0; i < numSamples; i++) {
                float sum = 0;
                for (int c = 0; c < channels; c++) {
                    float val = 0;
                    if (bytesPerSample == 2) {
                        val = bb.getShort() / 32768.0f;
                    } else if (bytesPerSample == 1) {
                        val = ((bb.get() & 0xff) - 128) / 128.0f;
                    }
                    sum += val;
                }
                samples[i] = sum / channels;
            }

            if (Math.abs(format.getSampleRate() - expectedSampleRate) > 1.0f) {
                return resample(samples, format.getSampleRate(), expectedSampleRate);
            }
            return samples;
        }
    }

    private static void recordTemplate() {
        try {
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[ERROR] Microphone recording line is not supported on this device.");
                return;
            }
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);

            System.out.println("\nReady. Recording in 3...");
            Thread.sleep(1000);
            System.out.println("Recording in 2...");
            Thread.sleep(1000);
            System.out.println("Recording in 1...");
            Thread.sleep(1000);
            System.out.println("\n>>> SPEAK NOW: 'bot' <<<\n");

            line.start();
            // Record 1.5 seconds at 16000Hz 16-bit Mono (1.5 * 16000 * 2 = 48000 bytes)
            byte[] data = new byte[48000];
            int bytesRead = 0;
            while (bytesRead < data.length) {
                int read = line.read(data, bytesRead, data.length - bytesRead);
                if (read > 0) {
                    bytesRead += read;
                }
            }
            line.stop();
            line.close();

            System.out.println("Recording complete. Saving to 'template_bot.wav'...");
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            AudioInputStream ais = new AudioInputStream(bais, format, data.length / 2);
            File outputFile = new File("template_bot.wav");
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
            System.out.println("Saved template to: " + outputFile.getAbsolutePath());
            System.out.println("Now you can run the test detection.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static float[] resample(float[] input, float sourceRate, float targetRate) {
        double factor = (double) sourceRate / targetRate;
        int targetLength = (int) (input.length / factor);
        float[] output = new float[targetLength];
        for (int i = 0; i < targetLength; i++) {
            double srcIndex = i * factor;
            int base = (int) srcIndex;
            double frac = srcIndex - base;
            if (base < input.length - 1) {
                output[i] = (float) ((1.0 - frac) * input[base] + frac * input[base + 1]);
            } else {
                output[i] = input[input.length - 1];
            }
        }
        return output;
    }
}
