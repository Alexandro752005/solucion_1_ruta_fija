package pe.rutafija.terms.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.AuthenticatedUser;
import pe.rutafija.shared.security.CurrentUserProvider;
import pe.rutafija.terms.api.dto.AcceptAdminTermsRequest;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsAcceptanceRepository;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTermsServiceTest {

    @Mock
    CurrentUserProvider currentUserProvider;

    @Mock
    AppUserRepository userRepository;

    @Mock
    AdminTermsDocumentRepository documentRepository;

    @Mock
    AdminTermsAcceptanceRepository acceptanceRepository;

    @Mock
    AuditService auditService;

    @Mock
    Environment environment;

    private AdminTermsService service;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        service = new AdminTermsService(
                currentUserProvider,
                userRepository,
                documentRepository,
                acceptanceRepository,
                auditService,
                environment,
                Clock.systemUTC()
        );
        admin = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "terms-admin@rutafija.test",
                pe.rutafija.identity.domain.UserRole.ADMIN
        );
        when(currentUserProvider.requireCurrentUser()).thenReturn(admin);
    }

    @Test
    void syntheticTermsCannotBeAcceptedOutsideTheExclusiveTestProfile() {
        AdminTermsDocument synthetic = mock(AdminTermsDocument.class);
        when(synthetic.isSynthetic()).thenReturn(true);
        when(documentRepository.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc(
                AdminTermsService.ADMIN_TERMS_DOCUMENT_KEY
        )).thenReturn(Optional.of(synthetic));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        assertThatThrownBy(() -> service.acceptCurrentTerms(new AcceptAdminTermsRequest(
                "SYNTHETIC-TEST-ONLY-0001",
                "0".repeat(64),
                true
        )))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;
                    org.assertj.core.api.Assertions.assertThat(applicationException.getStatus())
                            .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
                    org.assertj.core.api.Assertions.assertThat(applicationException.getCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });

        verify(userRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(acceptanceRepository, auditService);
    }

    @Test
    void syntheticTermsAreAlsoRejectedWhenTestIsCombinedWithAnotherProfile() {
        AdminTermsDocument synthetic = mock(AdminTermsDocument.class);
        when(synthetic.isSynthetic()).thenReturn(true);
        when(documentRepository.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc(
                AdminTermsService.ADMIN_TERMS_DOCUMENT_KEY
        )).thenReturn(Optional.of(synthetic));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test", "dev"});

        assertThatThrownBy(() -> service.acceptCurrentTerms(new AcceptAdminTermsRequest(
                "SYNTHETIC-TEST-ONLY-0001",
                "0".repeat(64),
                true
        )))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(userRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(acceptanceRepository, auditService);
    }
}
