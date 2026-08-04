package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Listado de solicitudes: filtro opcional por estado y validación del nombre del estado. */
class ListTenantRegistrationsUseCaseTest {

    private final TenantRegistrationRepository repository = mock(TenantRegistrationRepository.class);
    private final ListTenantRegistrationsUseCase useCase = new ListTenantRegistrationsUseCase(repository);

    @Test
    void listsWithoutFilterWhenNoStatusIsGiven() {
        PagedResult<TenantRegistration> empty = new PagedResult<>(List.of(), 0, 20, 0, 0);
        when(repository.findAll(null, 0, 20)).thenReturn(empty);

        assertThat(useCase.list(null, 0, 20)).isEqualTo(empty);
        assertThat(useCase.list("  ", 0, 20)).isEqualTo(empty);
    }

    @Test
    void translatesTheStatusNameToTheDomainEnum() {
        PagedResult<TenantRegistration> empty = new PagedResult<>(List.of(), 0, 20, 0, 0);
        when(repository.findAll(TenantRegistrationStatus.PENDING_REVIEW, 1, 5)).thenReturn(empty);

        assertThat(useCase.list("PENDING_REVIEW", 1, 5)).isEqualTo(empty);
        verify(repository).findAll(TenantRegistrationStatus.PENDING_REVIEW, 1, 5);
    }

    @Test
    void rejectsAnUnknownStatusName() {
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.list("INVENTADO", 0, 20));
    }
}
