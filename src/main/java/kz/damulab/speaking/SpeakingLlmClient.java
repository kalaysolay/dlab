package kz.damulab.speaking;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import kz.damulab.ai.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Общие runtime-настройки и ключи DamuLab, но отдельный транспорт без AiCallLogger и содержимого в логах. */
@Component
class SpeakingLlmClient {
    private final AiRuntimeSettingsService settings;
    private final AiProviderProperties providers;
    private final SpeakingCloudProperties cloud;
    private final SpeakingHttp http;
    private final SpeakingBudgetService budget;
    private final ObjectMapper mapper;
    SpeakingLlmClient(AiRuntimeSettingsService settings,AiProviderProperties providers,SpeakingCloudProperties cloud,SpeakingHttp http,ObjectMapper mapper,SpeakingBudgetService budget){
        this.settings=settings;this.providers=providers;this.cloud=cloud;this.http=http;this.mapper=mapper;this.budget=budget;
    }
    /** Снимок маршрута фиксируется до начала цепочки guard → tutor → guard. */
    AiRuntimeSelection selection(){
        ensureEnabled();
        var route=settings.resolve(AiUsageType.SPEAKING);
        if(route.provider()==AiProviderCode.STUB||key(route).isBlank())throw unavailable();
        // Проверяем допуск модели до старта новой беседы, чтобы не создавать заведомо неработающий разговор.
        if(!budget.priced(SpeakingMetrics.Stage.TUTOR,route.provider().name()+":"+route.model()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_cost_not_configured");
        return route;
    }
    void ensureEnabled(){if(!cloud.isEnabled()||!providers.isRealProvidersEnabled())throw unavailable();}
    boolean speechAvailable(){return cloud.isSpeechEnabled()&&providers.isRealProvidersEnabled()
            &&providers.getOpenai().getApiKey()!=null&&!providers.getOpenai().getApiKey().isBlank();}
    JsonNode json(AiRuntimeSelection route,String system,Object user,SpeakingMetrics.Stage stage){
        try{
            var body=new LinkedHashMap<String,Object>();body.put("model",route.model());
            body.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",mapper.writeValueAsString(user))));
            body.put("response_format",Map.of("type","json_object"));
            body.put("temperature",stage==SpeakingMetrics.Stage.TUTOR?0.2:0);
            if(route.provider()==AiProviderCode.OPENAI){body.put("store",false);body.put("max_completion_tokens",1400);}
            else {body.put("max_tokens",1400);body.put("thinking",Map.of("type","disabled"));}
            byte[] raw=http.post(stage,route.provider().name()+":"+route.model(),endpoint(route),"Bearer "+key(route),"application/json",mapper.writeValueAsBytes(body),128*1024);
            var choice=mapper.readTree(raw).path("choices").path(0);
            if(!choice.path("finish_reason").asText().equals("stop")||!choice.path("message").path("refusal").asText("").isBlank())throw unavailable();
            String content=choice.path("message").path("content").asText();
            if(content.isBlank()||content.length()>16000)throw unavailable();
            var result=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
            if(!result.isObject())throw unavailable();return result;
        }catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw unavailable();}
    }
    private AiProviderProperties.Provider provider(AiRuntimeSelection route){return switch(route.provider()){
        case OPENAI->providers.getOpenai();case DEEPSEEK->providers.getDeepseek();default->throw unavailable();};}
    private String key(AiRuntimeSelection route){return Objects.toString(provider(route).getApiKey(),"");}
    private String endpoint(AiRuntimeSelection route){String base=provider(route).getBaseUrl().replaceAll("/+$","");return base+(base.endsWith("/v1")?"":"/v1")+"/chat/completions";}
    static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_dialogue_unavailable");}
}
