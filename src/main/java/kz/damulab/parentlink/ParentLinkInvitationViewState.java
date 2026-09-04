package kz.damulab.parentlink;

/** Состояние безопасной страницы: персональные данные она не показывает ни в одной ветке. */
public enum ParentLinkInvitationViewState {
    LOGIN_REQUIRED,
    READY,
    WRONG_ACCOUNT,
    INVALID
}
