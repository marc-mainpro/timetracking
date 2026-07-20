package com.tfp.timetracking.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeSummaryCsvWriterTest {

    @Test
    void writesHeaderAndRowsWithCommaSeparator() {
        UUID employeeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TenantEmployeeSummary summary = new TenantEmployeeSummary(employeeId, Duration.ofHours(8), Duration.ofMinutes(30), 1, 0, 0);

        String csv = TimeSummaryCsvWriter.write(List.of(summary));

        assertThat(csv)
                .isEqualTo("employeeId,workedSeconds,pausedSeconds,workdayCount,adjustedWorkdayCount,openWorkdays\r\n"
                        + employeeId + ",28800,1800,1,0,0\r\n");
    }

    @Test
    void writesOnlyHeaderWhenEmpty() {
        String csv = TimeSummaryCsvWriter.write(List.of());

        assertThat(csv).isEqualTo("employeeId,workedSeconds,pausedSeconds,workdayCount,adjustedWorkdayCount,openWorkdays\r\n");
    }

    @Test
    void doesNotQuoteFieldsWithoutSpecialCharacters() {
        assertThat(TimeSummaryCsvWriter.escape("plain-value")).isEqualTo("plain-value");
    }

    @Test
    void quotesAndDoublesEmbeddedQuotesWhenFieldContainsAComma() {
        assertThat(TimeSummaryCsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
    }

    @Test
    void quotesAndDoublesEmbeddedQuotesWhenFieldContainsAQuote() {
        assertThat(TimeSummaryCsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    void quotesFieldsThatContainNewlines() {
        assertThat(TimeSummaryCsvWriter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(TimeSummaryCsvWriter.escape("line1\rline2")).isEqualTo("\"line1\rline2\"");
    }

    @Test
    void escapesNullAsEmptyString() {
        assertThat(TimeSummaryCsvWriter.escape(null)).isEqualTo("");
    }
}
