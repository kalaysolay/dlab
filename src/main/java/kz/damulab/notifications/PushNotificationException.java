package kz.damulab.notifications;

public class PushNotificationException extends RuntimeException {

    private final String code;

    public PushNotificationException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
