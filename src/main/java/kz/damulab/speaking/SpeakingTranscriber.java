package kz.damulab.speaking;

/** Распознаёт настоящую запись. Реализация не должна исправлять грамматику или подставлять пример. */
interface SpeakingTranscriber {
    Transcript transcribe(byte[] audio);
    boolean available();
    static String checkedText(String value){
        String text=value==null?"":value.strip().replaceAll("\\s+"," ");
        if(text.isBlank()||text.length()>2000||!text.matches(".*[A-Za-z].*")||text.matches("\\s*[\\[(].*[\\])]\\s*"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"speaking_audio_empty");
        return text;
    }
    record Transcript(String text, int seconds) {}
}
