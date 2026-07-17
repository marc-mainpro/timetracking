package com.tfp.timetracking.corrections.interfaces.rest;

import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Component;

@Component
public class CorrectionRestMapper {

    public CorrectionResponse toResponse(CorrectionRequest correctionRequest) {
        return new CorrectionResponse(
                correctionRequest.id(),
                correctionRequest.workdayId(),
                correctionRequest.requestedBy(),
                correctionRequest.reason(),
                toProposedChangesResponse(correctionRequest.proposedChanges()),
                correctionRequest.status().name(),
                correctionRequest.resolvedBy(),
                correctionRequest.resolvedAt(),
                correctionRequest.resolutionComment(),
                correctionRequest.createdAt());
    }

    public PagedCorrectionsResponse toPagedResponse(PagedResult<CorrectionRequest> result) {
        return new PagedCorrectionsResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public ProposedChanges toDomain(CorrectionRequestDto.ProposedChangesDto dto) {
        return new ProposedChanges(
                dto.startedAt(),
                dto.endedAt(),
                dto.breaks().stream().map(breakDto -> new ProposedChanges.ProposedBreak(breakDto.startedAt(), breakDto.endedAt())).toList());
    }

    private CorrectionResponse.ProposedChangesResponse toProposedChangesResponse(ProposedChanges proposedChanges) {
        return new CorrectionResponse.ProposedChangesResponse(
                proposedChanges.startedAt(),
                proposedChanges.endedAt(),
                proposedChanges.breaks().stream()
                        .map(breakEntry -> new CorrectionResponse.ProposedBreakResponse(breakEntry.startedAt(), breakEntry.endedAt()))
                        .toList());
    }
}
