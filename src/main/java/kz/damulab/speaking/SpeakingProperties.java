package kz.damulab.speaking;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Флаг damulab.speaking.enabled закрыт по умолчанию; pilot-only требует серверного списка учеников. */
@Component
@ConfigurationProperties(prefix = "damulab.speaking")
public class SpeakingProperties {
    private boolean enabled;
    private boolean pilotOnly = true;
    private List<Long> allowedStudentIds = List.of();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isPilotOnly() { return pilotOnly; }
    public void setPilotOnly(boolean pilotOnly) { this.pilotOnly = pilotOnly; }
    public List<Long> getAllowedStudentIds() { return allowedStudentIds; }
    public void setAllowedStudentIds(List<Long> ids) { allowedStudentIds = List.copyOf(ids); }
}
