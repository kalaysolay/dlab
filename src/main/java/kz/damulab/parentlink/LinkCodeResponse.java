package kz.damulab.parentlink;

import java.time.OffsetDateTime;

public record LinkCodeResponse(String code, OffsetDateTime expiresAt, String qrSvg) {
}
