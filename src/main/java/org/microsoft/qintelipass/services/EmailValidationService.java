package org.microsoft.qintelipass.services;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EmailValidationService {
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;
    private static final int MAX_DOMAIN_LENGTH = 253;
    private static final Pattern LOCAL_PART_PATTERN = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
    );
    private static final Pattern DOMAIN_LABEL_PATTERN = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
    );
    private static final Pattern TOP_LEVEL_DOMAIN_PATTERN = Pattern.compile(
            "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})"
    );

    private final Set<String> disposableDomains;

    public EmailValidationService(
            @Value("classpath:disposable-email-domains.txt") Resource disposableDomainsResource
    ) {
        this.disposableDomains = loadDisposableDomains(disposableDomainsResource);
    }

    public String normalize(String rawEmail) {
        if (!StringUtils.hasText(rawEmail)) {
            throw unavailable();
        }

        String candidate = rawEmail.trim();
        if (candidate.length() > MAX_EMAIL_LENGTH
                || candidate.contains("\r")
                || candidate.contains("\n")
                || candidate.contains("..")) {
            throw unavailable();
        }

        validateWithJakartaMail(candidate);
        int atIndex = candidate.indexOf('@');
        if (atIndex <= 0 || atIndex != candidate.lastIndexOf('@') || atIndex == candidate.length() - 1) {
            throw unavailable();
        }

        String localPart = candidate.substring(0, atIndex);
        String rawDomain = candidate.substring(atIndex + 1);
        if (localPart.length() > MAX_LOCAL_PART_LENGTH || !LOCAL_PART_PATTERN.matcher(localPart).matches()) {
            throw unavailable();
        }

        String domain = normalizeDomain(rawDomain);
        validateDomain(domain);
        if (isDisposableDomain(domain)) {
            throw unavailable();
        }
        return localPart.toLowerCase(Locale.ROOT) + "@" + domain;
    }

    private void validateWithJakartaMail(String email) {
        try {
            InternetAddress parsed = new InternetAddress(email, true);
            parsed.validate();
            if (!email.equals(parsed.getAddress())) {
                throw unavailable();
            }
        } catch (AddressException exception) {
            throw unavailable();
        }
    }

    private String normalizeDomain(String domain) {
        try {
            return IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private void validateDomain(String domain) {
        if (!StringUtils.hasText(domain)
                || domain.length() > MAX_DOMAIN_LENGTH
                || domain.startsWith("[")
                || domain.endsWith("]")
                || !domain.contains(".")) {
            throw unavailable();
        }

        String[] labels = domain.split("\\.", -1);
        for (String label : labels) {
            if (label.length() > 63 || !DOMAIN_LABEL_PATTERN.matcher(label).matches()) {
                throw unavailable();
            }
        }
        if (!TOP_LEVEL_DOMAIN_PATTERN.matcher(labels[labels.length - 1]).matches()) {
            throw unavailable();
        }
    }

    private boolean isDisposableDomain(String domain) {
        String candidate = domain;
        while (StringUtils.hasText(candidate)) {
            if (disposableDomains.contains(candidate)) {
                return true;
            }
            int dotIndex = candidate.indexOf('.');
            candidate = dotIndex < 0 ? null : candidate.substring(dotIndex + 1);
        }
        return false;
    }

    private Set<String> loadDisposableDomains(Resource resource) {
        Set<String> domains = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            reader.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .filter(line -> !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .forEach(domains::add);
        } catch (IOException exception) {
            throw new IllegalStateException("临时邮箱域名名单加载失败", exception);
        }
        if (domains.isEmpty()) {
            throw new IllegalStateException("临时邮箱域名名单为空");
        }
        log.info("已加载 {} 个临时邮箱域名", domains.size());
        return Collections.unmodifiableSet(domains);
    }

    private BadRequestException unavailable() {
        return new BadRequestException(EmailBindingMessages.EMAIL_UNAVAILABLE);
    }
}
