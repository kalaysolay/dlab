package kz.damulab.speaking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Локальные исполняемые файлы задаёт оператор; клиент не может выбирать команды, модель или пути. */
@Component
@ConfigurationProperties(prefix="damulab.speaking.voice")
public class SpeakingVoiceProperties {
    private boolean enabled;
    private String whisper="";
    private String ffmpeg="";
    private String model="";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled=value; }
    public String getWhisper() { return whisper; }
    public void setWhisper(String value) { whisper=value; }
    public String getFfmpeg() { return ffmpeg; }
    public void setFfmpeg(String value) { ffmpeg=value; }
    public String getModel() { return model; }
    public void setModel(String value) { model=value; }
}
