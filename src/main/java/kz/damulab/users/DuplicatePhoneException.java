package kz.damulab.users;

/** Возникает при попытке закрепить один телефон за несколькими учётными записями. */
public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException() {
        super("duplicate_phone");
    }
}
