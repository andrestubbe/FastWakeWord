# FastWakeWord

## 1. Vision & Kernidee
**FastWakeWord** ist das ultra-effiziente, immer zuhörende "Wach-Auge" (oder eher Ohr) deines KI-Agenten. 

Ein gängiger Denkfehler beim Bau von Voice-Assistants ist es, Whisper (oder FastSTT) 24/7 laufen zu lassen, um auf ein Aktivierungswort wie *"Hey Butler"* zu warten. Das würde die CPU/GPU dauerhaft blockieren und extrem viel Strom fressen. 

Ein Wake-Word-Engine macht nur genau eine Sache: Sie horcht auf ein spezifisches Klangmuster und verbraucht dabei weniger als 1% CPU-Leistung.

## 2. Die Architektur (Lokales ONNX / C++)
FastWakeWord setzt zu 100% auf lokale Verarbeitung. Nichts geht in die Cloud, solange das Wake-Word nicht gefallen ist (Datenschutz!).

**Die Engine (z.B. openWakeWord oder Porcupine):**
- Wir nutzen eine leichtgewichtige C++ Engine (wie die ONNX-Runtime für `openWakeWord` Modelle).
- Das Modul bekommt eine `.onnx` Modell-Datei, die genau auf dein gewünschtes Wort trainiert ist (z.B. "Computer", "Jarvis", "Hey FastJava").
- Es nimmt einen konstanten 16kHz Audio-Stream von `FastAudioCapture` und berechnet alle paar Millisekunden eine "Wahrscheinlichkeit", ob das Wort gesagt wurde.

## 3. Java High-Level API

```java
public interface FastWakeWord {
    static FastWakeWord open(String modelPath) { return new FastWakeWordImpl(modelPath); }

    // Startet das durchgehende, extrem ressourcenschonende Lauschen
    void startListening(FastWakeWordListener listener);
    
    // Stoppt das Lauschen (z.B. während der Agent gerade selbst redet oder aktiv zuhört)
    void stopListening();
}

public interface FastWakeWordListener {
    // Wird gefeuert, wenn das Wort erkannt wurde
    // 'confidence' gibt an, wie sicher die Engine ist (z.B. 0.95)
    void onWakeWordDetected(float confidence);
}
```

## 4. Der perfekte Agenten-Workflow
So arbeiten `FastAudioCapture`, `FastWakeWord` und `FastSTT` zusammen, ohne die CPU zu sprengen:

1. **Standby:** `FastWakeWord` läuft 24/7. Es bekommt Audiodaten via Shared Memory von `FastAudioCapture`. CPU-Last: ~0.5%.
2. **Trigger:** Du sagst *"Hey Butler"*. `FastWakeWord` feuert `onWakeWordDetected`.
3. **Switch:** Der Agent pausiert `FastWakeWord` (damit es sich nicht selbst triggert) und startet `FastSTT.startStreaming()`.
4. **Action:** Du sagst *"Schließe Chrome"*. FastSTT liefert den Text. Der Agent tut es.
5. **Reset:** Der Agent stoppt FastSTT und wirft `FastWakeWord` wieder an.

## 5. Agent-Kit (KI-Integration)
Das Wake-Word ist die Tür zum Agenten. Du kannst im Agent-Kit definieren, auf welches Wort dein Bot hört und ab welchem Confidence-Level er aufwachen soll, um Fehlalarme (False Positives) durch Hintergrundgeräusche zu vermeiden.
