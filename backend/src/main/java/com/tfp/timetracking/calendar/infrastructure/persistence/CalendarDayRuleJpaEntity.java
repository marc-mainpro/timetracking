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
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA de {@code calendar_day_rule} (migracion V16). Clave primaria
 * compuesta {@code (calendar_id, day_of_week)}: dentro de un calendario una
 * regla se identifica por su dia de la semana, sin id artificial.
 *
 * <p>El dia de la semana se persiste como {@code String} (nombre de
 * {@link java.time.DayOfWeek}) y no como el ordinal, para que la fila siga
 * siendo legible y estable ante cambios de la enumeracion.
 */
@Entity
@Table(name = "calendar_day_rule")
@IdClass(CalendarDayRuleJpaEntity.PrimaryKey.class)
public class CalendarDayRuleJpaEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private WorkCalendarJpaEntity calendar;

    @Id
    @Column(name = "day_of_week", nullable = false, length = 10)
    private String dayOfWeek;

    @Column(name = "working", nullable = false)
    private boolean working;

    @Column(name = "expected_minutes", nullable = false)
    private int expectedMinutes;

    protected CalendarDayRuleJpaEntity() {
        // Requerido por JPA.
    }

    public CalendarDayRuleJpaEntity(
            WorkCalendarJpaEntity calendar, String dayOfWeek, boolean working, int expectedMinutes) {
        this.calendar = calendar;
        this.dayOfWeek = dayOfWeek;
        this.working = working;
        this.expectedMinutes = expectedMinutes;
    }

    public WorkCalendarJpaEntity getCalendar() {
        return calendar;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public boolean isWorking() {
        return working;
    }

    public int getExpectedMinutes() {
        return expectedMinutes;
    }

    /** Clave compuesta de {@link CalendarDayRuleJpaEntity}; uso interno de JPA. */
    public static class PrimaryKey implements Serializable {

        private UUID calendar;
        private String dayOfWeek;

        public PrimaryKey() {}

        public PrimaryKey(UUID calendar, String dayOfWeek) {
            this.calendar = calendar;
            this.dayOfWeek = dayOfWeek;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PrimaryKey key)) {
                return false;
            }
            return Objects.equals(calendar, key.calendar) && Objects.equals(dayOfWeek, key.dayOfWeek);
        }

        @Override
        public int hashCode() {
            return Objects.hash(calendar, dayOfWeek);
        }
    }
}
