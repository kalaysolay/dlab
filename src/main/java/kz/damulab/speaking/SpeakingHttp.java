package kz.damulab.speaking;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Отдельный транспорт speaking: без журналирования тел/ключей, редиректов и автоматических платных повторов. */
@Component
class SpeakingHttp {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** Ограничивает размер и полное время ответа, включая тело; наружу выходят только стабильные коды ошибок. */
    byte[] post(String url,String authorization,String type,byte[] body,int maxBytes) {
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            URI uri=URI.create(url);
            if(uri.getUserInfo()!=null || uri.getFragment()!=null || !("https".equals(uri.getScheme())
                    || "http".equals(uri.getScheme()) && List.of("127.0.0.1","localhost","[::1]").contains(uri.getHost())))
                throw new IllegalArgumentException();
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(35))
                    .header("Authorization",authorization).header("Content-Type",type)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending=client.sendAsync(request,response->new LimitedBody(maxBytes));
            var response=pending.get(35,TimeUnit.SECONDS);
            if(response.statusCode()==429)throw error(429,"speaking_provider_busy");
            if(response.statusCode()<200 || response.statusCode()>=300)throw error(503,"speaking_provider_failed");
            return response.body();
        } catch(ResponseStatusException ex){throw ex;}
        catch(TimeoutException ex){throw error(504,"speaking_provider_timeout");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw error(503,"speaking_provider_failed");}
        catch(Exception ex){throw error(503,"speaking_provider_failed");}
        finally {if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
    private static ResponseStatusException error(int status,String code){return new ResponseStatusException(HttpStatus.valueOf(status),code);}

    /** Прерываем поток до накопления большого ответа, а не после неограниченного ofByteArray(). */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;subscription.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(buffer.remaining()>limit-bytes.size()){
                    subscription.cancel();result.completeExceptionally(new IllegalStateException("Response too large"));return;
                }
                byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
