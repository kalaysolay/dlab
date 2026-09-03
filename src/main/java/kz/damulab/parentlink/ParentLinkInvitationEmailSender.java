package kz.damulab.parentlink;

import kz.damulab.auth.EmailDeliveryException;
import kz.damulab.auth.EmailSendResult;

/** Порт отправки минимального письма: провайдер получает только адрес и секретную ссылку. */
public interface ParentLinkInvitationEmailSender {

    /** @throws EmailDeliveryException при ошибке провайдера, сети или конфигурации */
    EmailSendResult sendInvitation(String studentEmail, String rawToken);
}
