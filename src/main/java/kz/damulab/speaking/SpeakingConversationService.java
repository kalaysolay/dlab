package kz.damulab.speaking;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** Сетевые guard/tutor вызываются вне DB-транзакции. В каждый этап сохранения доступ проверяется снова. */
@Service
class SpeakingConversationService {
    private final SpeakingSessionService sessions;
    private final LiveSpeakingTutor live;
    SpeakingConversationService(SpeakingSessionService sessions,LiveSpeakingTutor live){this.sessions=sessions;this.live=live;}
    SpeakingViews.Session answer(UUID id,UUID requestId,SpeakingViews.AnswerRequest request){
        if(!sessions.live(id))return sessions.answer(id,requestId,request);
        var prepared=sessions.beginLive(id,requestId,request);
        if(prepared.replay()!=null)return prepared.replay();
        try{
            var reply=live.answer(prepared.context(),prepared.route());
            return sessions.completeLive(id,requestId,prepared.leaseId(),request,reply);
        }catch(RuntimeException ex){sessions.failLive(prepared.leaseId());throw ex;}
    }
}
