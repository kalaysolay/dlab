package kz.damulab.speaking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Внешний диалог и озвучка закрыты по умолчанию. Выбор LLM берётся из общей админки, ключи здесь не дублируются. */
@Component
@ConfigurationProperties(prefix="damulab.speaking.cloud")
public class SpeakingCloudProperties {
    private boolean enabled;
    private boolean speechEnabled;
    private String speechModel="gpt-4o-mini-tts";
    private String deepgramKey="";
    private String deepgramUrl="https://api.deepgram.com";
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public boolean isSpeechEnabled(){return speechEnabled;}
    public void setSpeechEnabled(boolean value){speechEnabled=value;}
    public String getSpeechModel(){return speechModel;}
    public void setSpeechModel(String value){speechModel=value;}
    public String getDeepgramKey(){return deepgramKey;}
    public void setDeepgramKey(String value){deepgramKey=value;}
    public String getDeepgramUrl(){return deepgramUrl;}
    public void setDeepgramUrl(String value){deepgramUrl=value;}
}
