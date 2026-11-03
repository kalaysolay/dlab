package kz.damulab.speaking;

import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Провайдер задаётся сервером, не браузером. Сбой облака не меняет место обработки голоса автоматически. */
@Component @Primary
class SpeakingTranscriberRouter implements SpeakingTranscriber {
    private final SpeakingVoiceProperties properties;
    private final LocalWhisperTranscriber whisper;
    private final DeepgramSpeakingTranscriber deepgram;
    private final Semaphore slots=new Semaphore(2);
    SpeakingTranscriberRouter(SpeakingVoiceProperties properties,LocalWhisperTranscriber whisper,DeepgramSpeakingTranscriber deepgram){
        this.properties=properties;this.whisper=whisper;this.deepgram=deepgram;
    }
    private SpeakingTranscriber selected(){return switch(properties.getProvider()){case "deepgram"->deepgram;case "whisper"->whisper;default->null;};}
    public boolean available(){var provider=selected();return provider!=null&&provider.available();}
    public Transcript transcribe(byte[] bytes){
        var provider=selected();if(provider==null||!provider.available())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_unavailable");
        if(!slots.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_voice_busy");
        try{return provider.transcribe(bytes);}finally{slots.release();}
    }
}
