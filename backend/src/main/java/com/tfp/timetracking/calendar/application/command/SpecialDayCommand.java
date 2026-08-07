package com.tfp.timetracking.calendar.application.command;

import java.time.LocalDate;

/** Jornada especial enviada por el cliente (RF-CAL-004). Fecha local, nunca instante. */
public record SpecialDayCommand(LocalDate date, String name, int expectedMinutes) {}
