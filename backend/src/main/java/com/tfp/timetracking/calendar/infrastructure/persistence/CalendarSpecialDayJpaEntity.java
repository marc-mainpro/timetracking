package com.tfp.timetracking.calendar.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA de {@code calendar_special_day} (migracion V16). Clave primaria
 * compuesta {@code (calendar_id, special_date)}.
 */
@Entity
@Table(name = "calendar_special_day")
@IdClass(CalendarSpecialDayJpaEntity.PrimaryKey.class)
public class CalendarSpecialDayJpaEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private WorkCalendarJpaEntity calendar;

    @Id
    @Column(name = "special_date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "expected_minutes", nullable = false)
    private int expectedMinutes;

    protected CalendarSpecialDayJpaEntity() {
        // Requerido por JPA.
    }

    public CalendarSpecialDayJpaEntity(
            WorkCalendarJpaEntity calendar, LocalDate date, String name, int expectedMinutes) {
        this.calendar = calendar;
        this.date = date;
        this.name = name;
        this.expectedMinutes = expectedMinutes;
    }

    public WorkCalendarJpaEntity getCalendar() {
        return calendar;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public int getExpectedMinutes() {
        return expectedMinutes;
    }

    /** Clave compuesta de {@link CalendarSpecialDayJpaEntity}; uso interno de JPA. */
    public static class PrimaryKey implements Serializable {

        private UUID calendar;
        private LocalDate date;

        public PrimaryKey() {}

        public PrimaryKey(UUID calendar, LocalDate date) {
            this.calendar = calendar;
            this.date = date;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PrimaryKey key)) {
                return false;
            }
            return Objects.equals(calendar, key.calendar) && Objects.equals(date, key.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(calendar, date);
        }
    }
}
