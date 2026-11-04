package kz.damulab.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import kz.damulab.ai.AiProviderProperties;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Озвучивает только проверенные серверные вопросы. Голос явно обозначен как ИИ; DeepSeek не имеет TTS API. */
@Service
class SpeakingSpeechService {
    private final SpeakingSessionService sessions;
    private final SpeakingLlmClient llm;
    private final AiProviderProperties providers;
    private final SpeakingCloudProperties cloud;
    private final SpeakingHttp http;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String,Cached> cache=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID,Long> deleted=new ConcurrentHashMap<>();
    private final Semaphore slots=new Semaphore(2);
    SpeakingSpeechService(SpeakingSessionService sessions,SpeakingLlmClient llm,AiProviderProperties providers,
            SpeakingCloudProperties cloud,SpeakingHttp http,ObjectMapper mapper){
        this.sessions=sessions;this.llm=llm;this.providers=providers;this.cloud=cloud;this.http=http;this.mapper=mapper;
    }
    byte[] speak(UUID id,UUID turnId){
        if(!llm.speechAvailable())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_speech_unavailable");
        String text=sessions.speechText(id,turnId),key=id+"/"+turnId+"/"+cloud.getSpeechModel();
        if(text.length()>500)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_speech_unavailable");
        purge();
        Cached old=cache.get(key);if(old!=null)return old.bytes();
        if(!slots.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_provider_busy");
        try{
            String base=providers.getOpenai().getBaseUrl().replaceAll("/+$","");
            byte[] audio=http.post(SpeakingMetrics.Stage.TTS,"OPENAI:"+cloud.getSpeechModel(),base+(base.endsWith("/v1")?"":"/v1")+"/audio/speech","Bearer "+providers.getOpenai().getApiKey(),
                    "application/json",mapper.writeValueAsBytes(Map.of("model",cloud.getSpeechModel(),"voice","alloy","input",text,"response_format","mp3")),1024*1024);
            if(audio.length<100 || !(audio[0]=='I'&&audio[1]=='D'&&audio[2]=='3'
                    || audio[0]==(byte)0xff&&(audio[1]&0xe0)==0xe0))throw new IllegalStateException();
            // Кэш только в памяти и не больше 32 вопросов. Повтор/замедление в браузере используют тот же Blob.
            synchronized(cache){
                // Удаление сессии побеждает и уже идущую озвучку: позднее аудио не возвращается и не оживляет кэш.
                if(deleted.containsKey(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"speaking_not_found");
                if(cache.size()>=32)cache.keySet().stream().findFirst().ifPresent(cache::remove);
                cache.put(key,new Cached(audio,System.currentTimeMillis()+15*60*1000));
            }
            return audio;
        }catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_speech_unavailable");}
        finally{slots.release();}
    }
    void evict(UUID id){synchronized(cache){deleted.put(id,System.currentTimeMillis()+15*60*1000);cache.keySet().removeIf(key->key.startsWith(id+"/"));}}
    /** Истечение TTL очищает байты даже при выключенном интерфейсе и без следующих запросов. */
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString="60000",fixedDelayString="60000")
    public void purge(){
        long now=System.currentTimeMillis();cache.entrySet().removeIf(entry->entry.getValue().expires()<now);
        deleted.entrySet().removeIf(entry->entry.getValue()<now);
    }
    record Cached(byte[] bytes,long expires){}
}
