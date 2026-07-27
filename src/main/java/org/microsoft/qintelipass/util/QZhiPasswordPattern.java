package org.microsoft.qintelipass.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class QZhiPasswordPattern {
    private static final Pattern USER_PATTERN = Pattern.compile("[A-Za-z0-9]{8,20}");
    public static class Generator {
        private static final String charSequence = "1234567890qwertyuiopasdfghjklzxcvbnm";
        private StringBuilder stringBuilder;
        public String generate(){
            this.stringBuilder = new StringBuilder();
            ThreadLocalRandom
                    .current()
                    .ints(0,charSequence.length())
                    .limit(8)
                    .forEach((i)-> stringBuilder.append(charSequence.charAt(i))
            );
            return stringBuilder.toString();
        }
    }

    public static boolean validate(String password){
        return USER_PATTERN.matcher(password).matches();
    }

    public static boolean validateStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecialCharacter = false;

        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                hasUppercase = true;
            } else if (character >= 'a' && character <= 'z') {
                hasLowercase = true;
            } else if (character >= '0' && character <= '9') {
                hasDigit = true;
            } else if (!Character.isWhitespace(character)
                    && !Character.isSpaceChar(character)
                    && !Character.isLetterOrDigit(character)) {
                hasSpecialCharacter = true;
            }
        }

        return hasUppercase && hasLowercase && hasDigit && hasSpecialCharacter;
    }
}
