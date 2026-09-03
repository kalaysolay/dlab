package kz.damulab.parentlink;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Настройки срока жизни и частоты повторной отправки приглашений parent→student. */
@Component
@ConfigurationProperties(prefix = "damulab.parent-link-invitations")
public class ParentLinkInvitationProperties {

    private Duration tokenTtl = Duration.ofHours(24);
    private Duration resendCooldown = Duration.ofMinutes(1);

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }
}
