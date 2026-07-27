package org.microsoft.qintelipass.password;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.microsoft.qintelipass.util.QZhiPasswordPattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasswordTests {
    @Test
    public void generatePasswords(){
        QZhiPasswordPattern.Generator passwordPattern = new QZhiPasswordPattern.Generator();
        for (int i = 0; i < 100; i++) {
            String s = passwordPattern.generate();
            assertTrue(QZhiPasswordPattern.validate(s));
        }
    }

    @Test
    void acceptsStrongPasswordWithoutAddingAMaximumLength() {
        assertTrue(QZhiPasswordPattern.validateStrongPassword(
                "Uppercase1!" + "a".repeat(128)
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Short1!",
            "lowercase1!",
            "UPPERCASE1!",
            "NoDigits!",
            "NoSpecial1",
            "SpaceOnly1 ",
            "TabOnly1\t",
            "NewLine1\n",
            "NoBreak1\u00A0"
    })
    void rejectsPasswordsMissingAnyRequiredCharacterClass(String password) {
        assertFalse(QZhiPasswordPattern.validateStrongPassword(password));
    }

    @Test
    void rejectsNullStrongPassword() {
        assertFalse(QZhiPasswordPattern.validateStrongPassword(null));
    }
}
