package kz.damulab.notifications;

public record PushDeliveryResult(
        boolean success,
        String providerName,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {

    public static PushDeliveryResult sent(String providerName, String providerMessageId) {
        return new PushDeliveryResult(true, providerName, providerMessageId, null, null);
    }

    public static PushDeliveryResult failed(String providerName, String errorCode, String errorMessage) {
        return new PushDeliveryResult(false, providerName, null, errorCode, errorMessage);
    }
}
