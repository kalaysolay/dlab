package kz.damulab.speaking;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Локальные жёсткие ограничения дополняются отдельным семантическим guard, а не заменяют его. */
@Component
class SpeakingSafety {
    enum Decision{ALLOW,REDIRECT,UNSAFE,SUPPORT,PRIVATE}
    private static final Pattern PRIVATE=Pattern.compile("(?iu)([\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|https?://|www\\.|\\+?\\d[\\d ()-]{8,}\\d|\\b(?:my address|мой адрес|менің мекенжайым)\\b)");
    // Только однозначное короткое сообщение: сюжет/цитату с дополнительным контекстом разбирает semantic guard.
    private static final Pattern SUPPORT=Pattern.compile("(?iu)^\\s*(?:i (?:want to (?:die|kill myself)|am going to (?:kill|hurt) myself)|я (?:хочу умереть|убью себя)|мен өзімді өлтіремін|мені (?:үйде )?ұрады|меня (?:дома )?бьют)[.!?\\s]*$");
    private static final Pattern INJECTION=Pattern.compile("(?iu)^\\s*(?:(?:ignore|forget|disregard).{0,35}(?:instructions|rules|prompt)|(?:игнорируй|забудь).{0,30}(?:правила|инструкции)|(?:reveal|show).{0,20}(?:system prompt)|(?:ережелерді|нұсқауларды).{0,20}(?:ұмыт|елеме))");
    Decision local(String raw){
        String text=Normalizer.normalize(raw,Normalizer.Form.NFKC).replaceAll("[\\p{Cf}]","");
        if(SUPPORT.matcher(text).find())return Decision.SUPPORT;
        if(PRIVATE.matcher(text).find())return Decision.PRIVATE;
        if(INJECTION.matcher(text).find())return Decision.REDIRECT;
        return Decision.ALLOW;
    }
    SpeakingTutor.Reply fallback(Decision decision,String question,String language){
        boolean kk="kk".equals(language);
        String reaction=switch(decision){
            case SUPPORT->kk?"Саған көмек қажет сияқты. Сенетін ересек адамға хабарлас. Қазір қауіп төніп тұрса, жергілікті жедел қызметке жүгін. Жаттығуды осы жерде тоқтатамыз.":"Похоже, тебе нужна помощь. Обратись к взрослому, которому доверяешь. Если опасность прямо сейчас — к местной экстренной службе. Здесь остановим упражнение.";
            case PRIVATE->kk?"Нақты аты-жөн, мекенжай мен байланыс деректерін қоспайық. Оқиғаны ойдан шығаруға болады.":"Давай без настоящих имён, адресов и контактов. Детали истории можно придумать.";
            case UNSAFE->"Let's keep our conversation safe and return to our practice question.";
            default->"Let's return to our chosen situation. You can finish this conversation and choose another one.";
        };
        return new SpeakingTutor.Reply(reaction,decision==Decision.SUPPORT?"":question,List.of(),decision.name());
    }
}
