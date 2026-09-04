package kz.damulab.users;

import org.springframework.stereotype.Component;

/**
 * Приводит пользовательский ввод телефона к E.164, чтобы уникальность не зависела от
 * пробелов, скобок и локального написания казахстанского номера через 8.
 */
@Component
public class PhoneNormalizer {

    /**
     * @return {@code null} для пустого необязательного телефона или канонический номер с {@code +}
     * @throws InvalidPhoneException если значение нельзя однозначно привести к E.164
     */
    public String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String compact = rawPhone.trim().replaceAll("[\\s().-]", "");
        if (compact.matches("8\\d{10}")) {
            compact = "+7" + compact.substring(1);
        } else if (compact.matches("7\\d{10}")) {
            compact = "+" + compact;
        } else if (compact.matches("\\d{10}")) {
            compact = "+7" + compact;
        }
        if (!compact.matches("\\+[1-9]\\d{7,14}")) {
            throw new InvalidPhoneException();
        }
        return compact;
    }
}
