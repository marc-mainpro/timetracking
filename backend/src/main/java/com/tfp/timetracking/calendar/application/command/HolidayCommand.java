package com.tfp.timetracking.calendar.application.command;

import java.time.LocalDate;

/** Festivo enviado por el cliente (RF-CAL-003). Fecha local, nunca instante. */
public record HolidayCommand(LocalDate date, String name) {}
