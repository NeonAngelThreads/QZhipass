package org.microsoft.qintelipass.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "canceled_dates")
public class CanceledUser {
    @Id
    @Column(name = "user_id", nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime canceledAt;
}
