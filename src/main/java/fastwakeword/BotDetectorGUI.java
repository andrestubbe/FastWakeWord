package fastwakeword;

import fastaudioprocess.FastAudioProcess;
import fasttheme.FastTheme;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.*;
import javax.swing.*;

/**
 * A minimalist 400x400 JFrame GUI for FastWakeWord.
 * Styled natively via FastTheme (black titlebar and body, white text, circle icon).
 * Displays a single large circle in the center that flashes green when "bot" is detected.
 */
public final class BotDetectorGUI extends JFrame {

    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SIZE = 160;
    private static final int MEL_BANDS = 40;
    private static final int WINDOW_FRAMES = 30; // 300 ms window
    private static final float DEFAULT_THRESHOLD = 0.75f;

    private FastWakeWordEngine engine;
    private TargetDataLine recordLine;
    private Thread listenThread;
    private boolean isListening = false;

    // Cooldown state
    private long lastTriggerTime = 0;

    // Visual State
    private float flashIntensity = 0.0f; // 0.0 (gray) to 1.0 (neon green)
    private Timer fadeTimer;
    private JPanel circlePanel;

    public BotDetectorGUI() {
        super("FastWakeWord");
        initUI();
        initEngine();
        checkAndLoadTemplate();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 400);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(Color.BLACK);

        // Circular Title Bar Icon
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icon.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillOval(4, 4, 24, 24);
        g2.setColor(Color.BLACK);
        g2.fillOval(13, 13, 8, 8);
        g2.dispose();
        setIconImage(icon);

        circlePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (flashIntensity <= 0.0f) {
                    return;
                }
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = 200;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                // Pure white circle fading with opacity
                int alpha = (int) (255 * flashIntensity);
                if (alpha < 0) alpha = 0;
                if (alpha > 255) alpha = 255;
                g2d.setColor(new Color(255, 255, 255, 255));
                g2d.fillOval(x, y, size, size);
            }
        };
        circlePanel.setBackground(Color.BLACK);
        add(circlePanel);

        // Fade animation timer (runs at 60 FPS)
        fadeTimer = new Timer(16, e -> {
            if (flashIntensity > 0.0f) {
                flashIntensity -= 0.04f; // fade over ~400ms
                if (flashIntensity < 0.0f) {
                    flashIntensity = 0.0f;
                }
                circlePanel.repaint();
            } else {
                fadeTimer.stop();
            }
        });

        // Apply native Windows dark title bar and body background coloring
        addNotify(); // Creates native HWND peer without showing the window
        long hwnd = FastTheme.getWindowHandle(this);
        if (hwnd != 0) {
            FastTheme.setTitleBarDarkMode(hwnd, true);
            FastTheme.setTitleBarColor(hwnd, 0, 0, 0); // Black
            FastTheme.setWindowBackgroundColor(hwnd, 0, 0, 0); // Black
            FastTheme.setWindowTransparency(hwnd, 232); // Opacity 232
        }
        setVisible(true);
    }

    private void initEngine() {
        engine = new FastWakeWordEngine(SAMPLE_RATE, FRAME_SIZE, MEL_BANDS, WINDOW_FRAMES, DEFAULT_THRESHOLD);
        
        engine.setOnScore(score -> {
            if (score > 0.5f) {
                System.out.printf("[VAD DEBUG] Similarity score: %.3f%n", score);
            }
        });

        engine.setOnTrigger(() -> SwingUtilities.invokeLater(() -> {
            long now = System.currentTimeMillis();
            if (now - lastTriggerTime > 1500) { // 1.5s trigger cooldown (prevents consecutive trigger noise)
                lastTriggerTime = now;
                System.out.println(">>> WAKE WORD DETECTED: 'bot' <<<");
                flashIntensity = 1.0f;
                circlePanel.repaint();
                fadeTimer.restart();
            }
        }));
    }

    private void checkAndLoadTemplate() {
        File dir = new File(".");
        File[] matches = dir.listFiles((d, name) -> name.toLowerCase().startsWith("template_bot") && name.toLowerCase().endsWith(".wav"));
        
        if (matches == null || matches.length == 0) {
            File templateFile = new File("template_bot.wav");
            System.out.println("[INFO] No templates found. Recording a new template from microphone...");
            recordTemplate(templateFile);
        } else {
            loadTemplates(matches);
        }
    }

    private void loadTemplates(File[] files) {
        try {
            float[][][] templates = new float[files.length][WINDOW_FRAMES][MEL_BANDS];
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                System.out.println("[INFO] Loading template file: " + file.getAbsolutePath());
                float[] samples = readWavSamples(file);
                float[][] templateLogMel = FastAudioProcess.logMelSpectrogram(
                    samples, SAMPLE_RATE, FRAME_SIZE, FRAME_SIZE, MEL_BANDS
                );
                if (templateLogMel.length < WINDOW_FRAMES) {
                    System.err.println("[ERROR] Template WAV file " + file.getName() + " is too short.");
                    return;
                }
                int startFrame = Math.max(0, (templateLogMel.length - WINDOW_FRAMES) / 2);
                templates[i] = engine.extractTemplate(templateLogMel, startFrame, WINDOW_FRAMES);
            }
            
            // Average all templates to create a robust shape pattern
            float[][] botTemplate = FastWakeWordEngine.averageTemplates(templates);
            engine.setTemplate(botTemplate);
            System.out.println("[INFO] Loaded and averaged " + files.length + " template file(s) successfully.");
            
            startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recordTemplate(File templateFile) {
        new Thread(() -> {
            try {
                System.out.println("\n--- Prepare to record template ---");
                for (int i = 3; i > 0; i--) {
                    System.out.println("Recording starts in " + i + "...");
                    Thread.sleep(1000);
                }
                System.out.println("\n>>> SPEAK NOW: 'bot' <<<\n");

                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("[ERROR] Microphone not supported on this device.");
                    return;
                }

                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                byte[] data = new byte[SAMPLE_RATE * 2 * 3 / 2]; // 1.5 seconds
                int bytesRead = 0;
                while (bytesRead < data.length) {
                    int read = line.read(data, bytesRead, data.length - bytesRead);
                    if (read > 0) bytesRead += read;
                }

                line.stop();
                line.close();

                System.out.println("Recording complete. Saving template to " + templateFile.getName() + "...");
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                AudioInputStream ais = new AudioInputStream(bais, format, data.length / 2);
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, templateFile);

                SwingUtilities.invokeLater(() -> loadTemplates(new File[]{templateFile}));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startListening() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("[ERROR] Microphone not supported for live capture.");
            return;
        }

        try {
            recordLine = (TargetDataLine) AudioSystem.getLine(info);
            recordLine.open(format);
            recordLine.start();

            isListening = true;
            System.out.println("[INFO] Live microphone is listening for 'bot'...");

            listenThread = new Thread(() -> {
                byte[] byteBuf = new byte[FRAME_SIZE * 2];
                short[] shortBuf = new short[FRAME_SIZE];
                while (isListening) {
                    int read = recordLine.read(byteBuf, 0, byteBuf.length);
                    if (read == byteBuf.length) {
                        ByteBuffer.wrap(byteBuf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuf);
                        engine.processFrame(shortBuf);
                    }
                }
            });
            listenThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static float[] readWavSamples(File file) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
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

            if (Math.abs(format.getSampleRate() - SAMPLE_RATE) > 1.0f) {
                return resample(samples, format.getSampleRate(), SAMPLE_RATE);
            }
            return samples;
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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            BotDetectorGUI gui = new BotDetectorGUI();
            gui.setVisible(true);
        });
    }
}
