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
 * Entidad JPA de {@code calendar_holiday} (migracion V16). Clave primaria
 * compuesta {@code (calendar_id, holiday_date)}: un festivo se identifica por su
 * fecha dentro del calendario.
 */
@Entity
@Table(name = "calendar_holiday")
@IdClass(CalendarHolidayJpaEntity.PrimaryKey.class)
public class CalendarHolidayJpaEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private WorkCalendarJpaEntity calendar;

    @Id
    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    protected CalendarHolidayJpaEntity() {
        // Requerido por JPA.
    }

    public CalendarHolidayJpaEntity(WorkCalendarJpaEntity calendar, LocalDate date, String name) {
        this.calendar = calendar;
        this.date = date;
        this.name = name;
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

    /** Clave compuesta de {@link CalendarHolidayJpaEntity}; uso interno de JPA. */
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
