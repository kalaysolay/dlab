package kz.damulab.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Deepgram получает только проверенный WAV, без student/session ID, имён файлов и пользовательских тегов. */
@Component
class DeepgramSpeakingTranscriber implements SpeakingTranscriber {
    private final SpeakingCloudProperties cloud;
    private final SpeakingVoiceProperties voice;
    private final SpeakingAudioProcessor audio;
    private final SpeakingHttp http;
    private final ObjectMapper mapper;
    DeepgramSpeakingTranscriber(SpeakingCloudProperties cloud,SpeakingVoiceProperties voice,SpeakingAudioProcessor audio,SpeakingHttp http,ObjectMapper mapper){
        this.cloud=cloud;this.voice=voice;this.audio=audio;this.http=http;this.mapper=mapper;
    }
    public boolean available(){return voice.isEnabled()&&!cloud.getDeepgramKey().isBlank()&&audio.available();}
    public Transcript transcribe(byte[] bytes){
        if(!available())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_unavailable");
        try(var file=audio.prepare(bytes)){
            // Opt-out обязателен для каждого запроса. Пунктуация не должна переписывать грамматику.
            String base=cloud.getDeepgramUrl().replaceAll("/+$","");
            byte[] response=http.post(SpeakingMetrics.Stage.STT,"DEEPGRAM:nova-3",base+"/v1/listen?model=nova-3&language=en&punctuate=true&mip_opt_out=true",
                    "Token "+cloud.getDeepgramKey(),"audio/wav",Files.readAllBytes(file.wav()),256*1024);
            var alternatives=mapper.readTree(response).path("results").path("channels").path(0).path("alternatives").path(0);
            if(alternatives.path("confidence").asDouble(0)<0.45)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"speaking_audio_empty");
            return new Transcript(SpeakingTranscriber.checkedText(alternatives.path("transcript").asText()),file.seconds());
        }catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_failed");}
    }
}
