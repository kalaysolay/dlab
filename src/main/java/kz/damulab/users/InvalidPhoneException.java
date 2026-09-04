package kz.damulab.users;

/** Возникает, когда введённый номер нельзя сохранить в каноническом E.164-формате. */
public class InvalidPhoneException extends RuntimeException {

    public InvalidPhoneException() {
        super("invalid_phone");
    }
}
