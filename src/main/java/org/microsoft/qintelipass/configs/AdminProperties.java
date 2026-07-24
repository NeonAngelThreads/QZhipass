package org.microsoft.qintelipass.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    private String phones = "";

    public String getPhones() {
        return phones;
    }

    public void setPhones(String phones) {
        this.phones = phones;
    }

    /**
     * Returns the set of admin phone numbers.
     */
    public Set<String> getAdminPhones() {
        if (phones == null || phones.isBlank()) {
            return Collections.emptySet();
        }
        return Stream.of(phones.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Check if the given phone number belongs to an admin.
     */
    public boolean isAdmin(String phone) {
        return phone != null && getAdminPhones().contains(phone);
    }
}
