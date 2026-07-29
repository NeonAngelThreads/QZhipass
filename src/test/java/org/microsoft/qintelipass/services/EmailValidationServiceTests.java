package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailValidationServiceTests {
    private EmailValidationService service;

    @BeforeEach
    void setUp() {
        service = new EmailValidationService(
                new ClassPathResource("disposable-email-domains.txt")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "employee@company.com",
            "user.name@small-company.cn",
            "dev_team@startup.io",
            "notice@internal-company.com.cn"
    })
    void acceptsValidCompanyEmails(String email) {
        assertThat(service.normalize(email)).isEqualTo(email);
    }

    @Test
    void trimsAndNormalizesForUniqueStorage() {
        assertThat(service.normalize("  Employee@Small-Company.CN  "))
                .isEqualTo("employee@small-company.cn");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "abc@",
            "@company.com",
            "abc@company",
            "abc..def@company.com",
            ".abc@company.com",
            "abc.@company.com",
            "abc@-company.com",
            "abc@company-.com",
            "abc@company.c",
            "abc@[127.0.0.1]",
            "abc\r\n@example.com"
    })
    void rejectsInvalidAddressesWithOneAuthoritativeMessage(String email) {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.normalize(email)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.EMAIL_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "person@mailinator.com",
            "person@inbox.mailinator.com",
            "person@10minutemail.com",
            "person@yopmail.com"
    })
    void rejectsDisposableDomainsAndTheirSubdomains(String email) {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.normalize(email)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.EMAIL_UNAVAILABLE);
    }

    @Test
    void failsFastWhenDisposableDomainListIsEmpty() {
        assertThrows(
                IllegalStateException.class,
                () -> new EmailValidationService(new ByteArrayResource(new byte[0]))
        );
    }
}
