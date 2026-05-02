package kz.damulab.auth;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Email is already registered: " + email);
    }
}
