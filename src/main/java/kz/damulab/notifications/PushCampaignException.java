package kz.damulab.notifications;

/**
 * Исключение для бизнес-ошибок при работе с кампаниями.
 * code — машинный код ошибки, преобразуется в HTTP-статус в PushCampaignExceptionHandler.
 */
public class PushCampaignException extends RuntimeException {

    private final String code;

    public PushCampaignException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
