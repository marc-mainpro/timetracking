package com.tfp.timetracking.absence.application;

import com.tfp.timetracking.absence.domain.AbsenceType;
import com.tfp.timetracking.absence.domain.AbsenceTypeRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAbsenceTypesUseCase {

    private final AbsenceTypeRepository absenceTypeRepository;
    private final TenantContext tenantContext;

    public ListAbsenceTypesUseCase(AbsenceTypeRepository absenceTypeRepository, TenantContext tenantContext) {
        this.absenceTypeRepository = absenceTypeRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<AbsenceType> listActive() {
        return absenceTypeRepository.findByTenantId(tenantContext.currentTenantId()).stream()
                .filter(AbsenceType::active)
                .toList();
    }
}
