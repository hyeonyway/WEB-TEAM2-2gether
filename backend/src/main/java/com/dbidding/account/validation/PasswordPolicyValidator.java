package com.dbidding.account.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        boolean letter = value.codePoints().anyMatch(Character::isLetter);
        boolean digit = value.codePoints().anyMatch(Character::isDigit);
        boolean special = value.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c));
        int kinds = (letter ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
        return kinds >= 2 && value.length() >= (kinds >= 3 ? 8 : 10);
    }
}
