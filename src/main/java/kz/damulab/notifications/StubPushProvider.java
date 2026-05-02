package kz.damulab.notifications;

import org.springframework.stereotype.Component;

@Component
public class StubPushProvider implements PushProvider {

    static final String FAILURE_TOKEN = "__FAIL_PUSH__";

    @Override
    public PushDeliveryResult send(PushNotification notification) {
        if (notification.getText().contains(FAILURE_TOKEN)) {
            return PushDeliveryResult.failed("stub", "stub_push_failure", "Stub push failure requested");
        }
        return PushDeliveryResult.sent("stub", "stub-push-" + notification.getId());
    }
}
