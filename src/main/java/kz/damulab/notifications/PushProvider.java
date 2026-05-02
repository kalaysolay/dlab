package kz.damulab.notifications;

public interface PushProvider {

    PushDeliveryResult send(PushNotification notification);
}
