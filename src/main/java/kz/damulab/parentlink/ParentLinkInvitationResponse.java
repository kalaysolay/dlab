package kz.damulab.parentlink;

/** Нейтральный ответ не раскрывает, существует ли ученик и было ли отправлено письмо. */
public record ParentLinkInvitationResponse(String status) {

    public static ParentLinkInvitationResponse accepted() {
        return new ParentLinkInvitationResponse("accepted");
    }
}
