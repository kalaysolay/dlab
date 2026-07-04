package kz.damulab.passkeys;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "damulab.passkeys")
public class PasskeyProperties {

    private String rpId = "localhost";
    private String rpName = "Damulab.kz";
    private Set<String> allowedOrigins = new LinkedHashSet<>(Set.of("http://localhost:8080"));

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName;
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
