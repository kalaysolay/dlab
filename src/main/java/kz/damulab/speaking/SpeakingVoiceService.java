package kz.damulab.speaking;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** HTTP-загрузка ограничена 10 MiB. Декодер и ASR вызываются вне JPA-транзакций и блокировок владельца. */
@Service
class SpeakingVoiceService {
    static final int MAX_BYTES=10*1024*1024;
    private final SpeakingAccess access;
    private final SpeakingVoiceStore store;
    private final SpeakingTranscriber transcriber;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(5,true);
    SpeakingVoiceService(SpeakingAccess access,SpeakingVoiceStore store,SpeakingTranscriber transcriber) {
        this.access=access; this.store=store; this.transcriber=transcriber;
    }
    /** Загружает реальную запись; распознанный текст возвращается только как неподтверждённый черновик. */
    SpeakingViews.Transcription upload(UUID sessionId,UUID key,long version,HttpServletRequest request) throws IOException {
        access.student(false);
        // Ограничиваем память ДО чтения тела: пять загрузок по 10 MiB, а не по одному буферу на каждый HTTP-поток.
        if(!uploads.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_voice_busy");
        try{return processUpload(sessionId,key,version,request);}finally{uploads.release();}
    }
    private SpeakingViews.Transcription processUpload(UUID sessionId,UUID key,long version,HttpServletRequest request)throws IOException{
        if(version<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"speaking_invalid_request");
        if(!transcriber.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_voice_unavailable");
        if(request.getContentLengthLong()>MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"speaking_audio_size");
        // Raw body вместо multipart: не меняем общие лимиты загрузки учебных материалов.
        byte[] audio=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(audio.length>MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"speaking_audio_size");
        if(!audioContainer(audio)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"speaking_audio_invalid");
        String hash;
        try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(audio)); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        var reservation=store.begin(sessionId,key,version,hash);
        if(!reservation.fresh()) return reservation.replay();
        try { return store.complete(reservation.id(),transcriber.transcribe(audio)); }
        catch(RuntimeException ex) { store.fail(reservation.id()); throw ex; }
    }
    private boolean audioContainer(byte[] b) {
        if(b.length<12) return false;
        // Сигнатуры разрешённых контейнеров, не расширение файла и не присланный MIME.
        return starts(b,0,"RIFF") && starts(b,8,"WAVE") || starts(b,0,"OggS") || starts(b,0,"ID3")
                || b[0]==(byte)0xff && (b[1]&0xe0)==0xe0 || starts(b,4,"ftyp")
                || b[0]==0x1a && b[1]==0x45 && b[2]==(byte)0xdf && b[3]==(byte)0xa3;
    }
    private boolean starts(byte[] b,int start,String value) {
        for(int i=0;i<value.length();i++) if(b[start+i]!=(byte)value.charAt(i)) return false;
        return true;
    }
}
