package kz.damulab.speaking;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Общая локальная проверка звука до любого ASR, включая облачный. Исходный файл не покидает приватный temp. */
@Component
class SpeakingAudioProcessor {
    private final SpeakingVoiceProperties properties;
    private final Path root=Path.of(System.getProperty("java.io.tmpdir"),"damulab-speaking-voice");
    SpeakingAudioProcessor(SpeakingVoiceProperties properties){this.properties=properties;}
    boolean available(){return file(properties.getFfmpeg());}
    static boolean file(String value){try{return value!=null&&!value.isBlank()&&Files.isRegularFile(Path.of(value));}catch(InvalidPathException ex){return false;}}

    /** Декодирование ограничено 61 секундой; отправить облаку слишком длинную запись нельзя. */
    AudioFile prepare(byte[] input){
        if(!available())throw error(503,"speaking_voice_unavailable");
        Path folder=null;
        try{
            Files.createDirectories(root);folder=Files.createTempDirectory(root,"attempt-");
            Path source=folder.resolve("input"),wav=folder.resolve("audio.wav");Files.write(source,input);
            execute(List.of(properties.getFfmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y",
                    "-protocol_whitelist","file","-format_whitelist","wav,mp3,mov,matroska,webm,ogg",
                    "-i",source.toString(),"-t","61","-vn","-ac","1","-ar","16000","-c:a","pcm_s16le",wav.toString()),15,"speaking_audio_invalid");
            double duration;
            try(var stream=AudioSystem.getAudioInputStream(wav.toFile())){
                duration=stream.getFrameLength()/stream.getFormat().getFrameRate();
                if(duration<0.4||duration>60.05)throw error(400,"speaking_audio_duration");
                byte[] pcm=stream.readAllBytes();long energy=0;
                for(int i=0;i+1<pcm.length;i+=2){int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));energy+=(long)sample*sample;}
                if(pcm.length<2||Math.sqrt(energy/(pcm.length/2.0))<80)throw error(422,"speaking_audio_empty");
            }
            return new AudioFile(folder,wav,Math.min(60,(int)Math.ceil(duration)));
        }catch(ResponseStatusException ex){remove(folder);throw ex;}
        catch(Exception ex){remove(folder);throw error(503,"speaking_voice_failed");}
    }

    /** Без shell и stdout: в выводе локальных инструментов могут быть ученические слова. */
    static void execute(List<String> command,int seconds,String code)throws IOException{
        Process process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{
            if(!process.waitFor(seconds,TimeUnit.SECONDS))throw error(504,"speaking_voice_timeout");
            if(process.exitValue()!=0)throw error(422,code);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw error(503,"speaking_voice_failed");}
        finally{if(process.isAlive()){process.destroyForcibly();try{process.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}}
    }
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString="60000",fixedDelayString="60000")
    public void purge(){
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))return;
        try(var folders=Files.newDirectoryStream(root,"attempt-*")){
            for(Path folder:folders)if(Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS)
                    &&Files.getLastModifiedTime(folder).toMillis()<System.currentTimeMillis()-Duration.ofMinutes(30).toMillis())remove(folder);
        }catch(IOException ignored){}
    }
    private static void remove(Path folder){
        if(folder==null)return;
        try(var files=Files.list(folder)){for(Path path:files.toList())Files.deleteIfExists(path);}catch(IOException ignored){}
        try{Files.deleteIfExists(folder);}catch(IOException ignored){}
    }
    record AudioFile(Path folder,Path wav,int seconds)implements AutoCloseable{public void close(){remove(folder);}}
    private static ResponseStatusException error(int status,String code){return new ResponseStatusException(HttpStatus.valueOf(status),code);}
}
