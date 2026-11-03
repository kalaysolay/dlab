package kz.damulab.speaking;

/** Распознаёт настоящую запись. Реализация не должна исправлять грамматику или подставлять пример. */
interface SpeakingTranscriber {
    Transcript transcribe(byte[] audio);
    boolean available();
    record Transcript(String text, int seconds) {}
}
