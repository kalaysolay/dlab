package kz.damulab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Same-origin STOMP transport с авторизацией входа и доставки каждой комнатной подписки. */
@Configuration
@EnableWebSocketMessageBroker
public class QuizWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final kz.damulab.quiz.QuizRoomSubscriptions subscriptions;
    /** Подключает авторизацию подписки и фактической исходящей доставки. Origin остаётся same-origin Spring. */
    public QuizWebSocketConfig(kz.damulab.quiz.QuizRoomSubscriptions subscriptions) { this.subscriptions = subscriptions; }

    /** Клиентские SEND запрещены; SUBSCRIBE проверяет STUDENT и точное членство. */
    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message, org.springframework.messaging.MessageChannel channel) {
                return subscriptions.inbound(message);
            }
        });
    }
    /** Проверка выполняется перед handler, а не только до помещения сообщения в очередь executor. */
    @Override
    public void configureClientOutboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(subscriptions);
    }

    /** Сохраняет существующий simple broker; клиентские события не принимаются. */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /** Не расширяет allowed origins: Spring принимает только тот же origin приложения. */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/quiz");
    }
}
