package com.tfp.timetracking.timetracking.interfaces.rest;

import com.tfp.timetracking.timetracking.application.EndBreakUseCase;
import com.tfp.timetracking.timetracking.application.EndWorkdayUseCase;
import com.tfp.timetracking.timetracking.application.GetCurrentWorkdayUseCase;
import com.tfp.timetracking.timetracking.application.GetWorkdayUseCase;
import com.tfp.timetracking.timetracking.application.ListOwnWorkdaysUseCase;
import com.tfp.timetracking.timetracking.application.StartBreakUseCase;
import com.tfp.timetracking.timetracking.application.StartWorkdayUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workdays")
@Tag(name = "Workdays")
public class WorkdayController {

    private final StartWorkdayUseCase startWorkdayUseCase;
    private final StartBreakUseCase startBreakUseCase;
    private final EndBreakUseCase endBreakUseCase;
    private final EndWorkdayUseCase endWorkdayUseCase;
    private final GetCurrentWorkdayUseCase getCurrentWorkdayUseCase;
    private final ListOwnWorkdaysUseCase listOwnWorkdaysUseCase;
    private final GetWorkdayUseCase getWorkdayUseCase;
    private final WorkdayRestMapper workdayRestMapper;

    public WorkdayController(
            StartWorkdayUseCase startWorkdayUseCase,
            StartBreakUseCase startBreakUseCase,
            EndBreakUseCase endBreakUseCase,
            EndWorkdayUseCase endWorkdayUseCase,
            GetCurrentWorkdayUseCase getCurrentWorkdayUseCase,
            ListOwnWorkdaysUseCase listOwnWorkdaysUseCase,
            GetWorkdayUseCase getWorkdayUseCase,
            WorkdayRestMapper workdayRestMapper) {
        this.startWorkdayUseCase = startWorkdayUseCase;
        this.startBreakUseCase = startBreakUseCase;
        this.endBreakUseCase = endBreakUseCase;
        this.endWorkdayUseCase = endWorkdayUseCase;
        this.getCurrentWorkdayUseCase = getCurrentWorkdayUseCase;
        this.listOwnWorkdaysUseCase = listOwnWorkdaysUseCase;
        this.getWorkdayUseCase = getWorkdayUseCase;
        this.workdayRestMapper = workdayRestMapper;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<WorkdayResponse> start() {
        WorkdayResponse response = workdayRestMapper.toResponse(startWorkdayUseCase.start());
        return ResponseEntity.created(URI.create("/api/v1/workdays/" + response.id())).body(response);
    }

    @PostMapping("/current/breaks/start")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public WorkdayResponse startBreak() {
        return workdayRestMapper.toResponse(startBreakUseCase.startBreak());
    }

    @PostMapping("/current/breaks/end")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public WorkdayResponse endBreak() {
        return workdayRestMapper.toResponse(endBreakUseCase.endBreak());
    }

    @PostMapping("/current/end")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public WorkdayResponse endWorkday() {
        return workdayRestMapper.toResponse(endWorkdayUseCase.endWorkday());
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public WorkdayResponse current() {
        return workdayRestMapper.toResponse(getCurrentWorkdayUseCase.get());
    }

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public PagedResponse<WorkdayResponse> listOwn(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return workdayRestMapper.toPagedResponse(listOwnWorkdaysUseCase.list(page, size, from, to));
    }

    @GetMapping("/{workdayId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public WorkdayResponse get(@PathVariable UUID workdayId) {
        return workdayRestMapper.toResponse(getWorkdayUseCase.get(workdayId));
    }
}
