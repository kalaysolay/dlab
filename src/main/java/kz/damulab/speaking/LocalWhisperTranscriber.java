package kz.damulab.speaking;

import java.nio.file.*;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Явно выбираемый офлайн-провайдер. Автоматической передачи записи другому ASR при отказе нет. */
@Component
class LocalWhisperTranscriber implements SpeakingTranscriber {
    private final SpeakingVoiceProperties properties;
    private final SpeakingAudioProcessor audio;
    LocalWhisperTranscriber(SpeakingVoiceProperties properties,SpeakingAudioProcessor audio){this.properties=properties;this.audio=audio;}
    public boolean available(){return properties.isEnabled()&&audio.available()
            &&SpeakingAudioProcessor.file(properties.getWhisper())&&SpeakingAudioProcessor.file(properties.getModel());}
    public Transcript transcribe(byte[] bytes){
        if(!available())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_unavailable");
        try(var file=audio.prepare(bytes)){
            Path result=file.folder().resolve("result");
            SpeakingAudioProcessor.execute(List.of(properties.getWhisper(),"-m",properties.getModel(),"-f",file.wav().toString(),
                    "-l","en","-t","4","-nt","-otxt","-of",result.toString()),90,"speaking_voice_failed");
            Path text=file.folder().resolve("result.txt");
            if(!Files.isRegularFile(text)||Files.size(text)>12000)throw new IllegalStateException();
            return new Transcript(SpeakingTranscriber.checkedText(Files.readString(text)),file.seconds());
        }catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_failed");}
    }
}
