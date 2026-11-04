package kz.damulab.speaking;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Консервативные USD-резервы, не инвойс провайдера. Новую модель оператор допускает только после проверки её тарифа. */
@Component
@ConfigurationProperties(prefix="damulab.speaking.budget")
public class SpeakingBudgetProperties {
    private BigDecimal studentDailyUsd=new BigDecimal("3.00"),moduleDailyUsd=new BigDecimal("20.00");
    private BigDecimal llmReserveUsd=new BigDecimal("0.05"),sttReserveUsd=new BigDecimal("0.02"),speechReserveUsd=new BigDecimal("0.10");
    private Set<String> approvedLlmModels=Set.of("OPENAI:gpt-4.1-mini","DEEPSEEK:deepseek-flash");
    public BigDecimal getStudentDailyUsd(){return studentDailyUsd;}
    public void setStudentDailyUsd(BigDecimal v){studentDailyUsd=v;}
    public BigDecimal getModuleDailyUsd(){return moduleDailyUsd;}
    public void setModuleDailyUsd(BigDecimal v){moduleDailyUsd=v;}
    public BigDecimal getLlmReserveUsd(){return llmReserveUsd;}
    public void setLlmReserveUsd(BigDecimal v){llmReserveUsd=v;}
    public BigDecimal getSttReserveUsd(){return sttReserveUsd;}
    public void setSttReserveUsd(BigDecimal v){sttReserveUsd=v;}
    public BigDecimal getSpeechReserveUsd(){return speechReserveUsd;}
    public void setSpeechReserveUsd(BigDecimal v){speechReserveUsd=v;}
    public Set<String> getApprovedLlmModels(){return approvedLlmModels;}
    public void setApprovedLlmModels(Set<String> v){approvedLlmModels=Set.copyOf(v);}
}
