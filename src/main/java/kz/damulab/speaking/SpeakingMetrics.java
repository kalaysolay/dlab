package kz.damulab.speaking;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** Фиксированные метки этапов: без ID, URL, запросов, ответов или свободных labels. Сбрасываются при рестарте JVM. */
@Component
class SpeakingMetrics {
    enum Stage{INPUT_GUARD,TUTOR,OUTPUT_GUARD,STT,TTS}
    private static final class Count{final LongAdder calls=new LongAdder(),failures=new LongAdder(),millis=new LongAdder();}
    private final Map<Stage,Count> counts=new EnumMap<>(Stage.class);
    SpeakingMetrics(){for(var stage:Stage.values())counts.put(stage,new Count());}
    void record(Stage stage,boolean ok,long nanos){var c=counts.get(stage);c.calls.increment();if(!ok)c.failures.increment();c.millis.add(nanos/1_000_000);}
    Map<String,Object> snapshot(){
        var result=new LinkedHashMap<String,Object>();counts.forEach((stage,c)->result.put(stage.name().toLowerCase(Locale.ROOT),
                Map.of("attempts",c.calls.sum(),"failures",c.failures.sum(),"totalMillis",c.millis.sum())));return result;
    }
}
