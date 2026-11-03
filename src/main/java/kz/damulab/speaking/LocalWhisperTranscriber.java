package kz.damulab.speaking;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** CPU Whisper.cpp: никакой сети, звук существует только во временном каталоге на время обработки. */
@Component
class LocalWhisperTranscriber implements SpeakingTranscriber {
    private final SpeakingVoiceProperties properties;
    private final Path tempRoot=Path.of(System.getProperty("java.io.tmpdir"),"damulab-speaking-voice");
    // Ограничиваем суммарную нагрузку процесса приложения; очередь из приватных записей не создаём.
    private final Semaphore slots=new Semaphore(2);
    LocalWhisperTranscriber(SpeakingVoiceProperties properties) { this.properties=properties; }

    @Override public boolean available() {
        return properties.isEnabled() && file(properties.getWhisper()) && file(properties.getFfmpeg()) && file(properties.getModel());
    }
    private boolean file(String value) {
        try { return !value.isBlank() && Files.isRegularFile(Path.of(value)); }
        catch(InvalidPathException ex) { return false; }
    }

    /** Декодируем не больше 61 секунды: превышение 60 секунд отвергаем, а не молча обрезаем. */
    @Override public Transcript transcribe(byte[] audio) {
        if(!available()) throw failure(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_unavailable");
        if(!slots.tryAcquire()) throw failure(HttpStatus.TOO_MANY_REQUESTS,"speaking_voice_busy");
        Path folder=null;
        try {
            Files.createDirectories(tempRoot);
            folder=Files.createTempDirectory(tempRoot,"attempt-");
            Path input=folder.resolve("input"), wav=folder.resolve("audio.wav"), output=folder.resolve("result");
            Files.write(input,audio);
            // Не shell: ни имя файла клиента, ни его MIME не становятся аргументами команды.
            // Whitelist исключает сетевые протоколы и плейлисты со ссылками на файлы сервера.
            execute(List.of(properties.getFfmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y",
                    "-protocol_whitelist","file","-format_whitelist","wav,mp3,mov,matroska,webm,ogg",
                    "-i",input.toString(),"-t","61","-vn","-ac","1","-ar","16000","-c:a","pcm_s16le",wav.toString()),
                    Duration.ofSeconds(15),"speaking_audio_invalid");
            double duration;
            try(var stream=AudioSystem.getAudioInputStream(wav.toFile())) {
                duration=stream.getFrameLength()/stream.getFormat().getFrameRate();
                // Полную тишину отсеиваем до модели: Whisper иногда придумывает слова на пустой записи.
                byte[] pcm=stream.readAllBytes();long energy=0;
                for(int i=0;i+1<pcm.length;i+=2) {
                    int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8)); energy+=(long)sample*sample;
                }
                if(pcm.length<2 || Math.sqrt(energy/(pcm.length/2.0))<80)
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY,"speaking_audio_empty");
            }
            if(duration<0.4 || duration>60.05) throw failure(HttpStatus.BAD_REQUEST,"speaking_audio_duration");
            execute(List.of(properties.getWhisper(),"-m",properties.getModel(),"-f",wav.toString(),"-l","en",
                    "-t","4","-nt","-otxt","-of",output.toString()),Duration.ofSeconds(90),"speaking_voice_failed");
            Path result=folder.resolve("result.txt");
            if(!Files.isRegularFile(result) || Files.size(result)>12000) throw failure(HttpStatus.UNPROCESSABLE_ENTITY,"speaking_audio_empty");
            String text=Files.readString(result).strip().replaceAll("\\s+"," ");
            // Тишина/шум не превращаются в учебную ошибку. Ничего не отправляем собеседнику без подтверждения.
            if(text.isBlank() || text.length()>2000 || !text.matches(".*[A-Za-z].*")
                    || text.matches("(?i)\\s*[\\[(].*[\\])]\\s*"))
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY,"speaking_audio_empty");
            return new Transcript(text,Math.min(60,(int)Math.ceil(duration)));
        } catch(ResponseStatusException ex) { throw ex; }
        catch(Exception ex) { throw failure(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_failed"); }
        finally {
            if(folder!=null) {
                // Здесь только сгенерированный нами каталог; чужие пути и рекурсивное удаление не используются.
                removeFolder(folder);
            }
            slots.release();
        }
    }

    /** Подбирает файлы после аварии JVM; возраст больше максимальной обработки, чужие каталоги не трогаем. */
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString="60000",fixedDelayString="60000")
    public void purgeAbandonedAudio() {
        if(!Files.isDirectory(tempRoot,LinkOption.NOFOLLOW_LINKS))return;
        try(var folders=Files.newDirectoryStream(tempRoot,"attempt-*")) {
            for(Path folder:folders) {
                if(Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(folder).toMillis()<System.currentTimeMillis()-Duration.ofMinutes(30).toMillis())
                    removeFolder(folder);
            }
        } catch(IOException ignored) { /* Следующая очистка повторит попытку без персональных данных в логах. */ }
    }
    private void removeFolder(Path folder) {
        try(var files=Files.list(folder)) { for(Path path:files.toList()) Files.deleteIfExists(path); }
        catch(IOException ignored) { /* При занятости файла повторит периодическая очистка. */ }
        try { Files.deleteIfExists(folder); } catch(IOException ignored) {}
    }

    private void execute(List<String> command, Duration timeout, String code) throws IOException {
        // Вывод Whisper содержит расшифровку, поэтому не перенаправляем его в журнал приложения.
        Process process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS))
                throw failure(HttpStatus.GATEWAY_TIMEOUT,"speaking_voice_timeout");
            if(process.exitValue()!=0) throw failure(HttpStatus.UNPROCESSABLE_ENTITY,code);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt(); throw failure(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_failed");
        } finally {
            if(process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(5,TimeUnit.SECONDS); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
    }
    private static ResponseStatusException failure(HttpStatus status,String code) { return new ResponseStatusException(status,code); }
}
