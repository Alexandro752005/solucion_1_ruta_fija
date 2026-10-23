package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingAccessRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.exception.InvalidCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Applicant access grants no operational role, JWT, refresh token or session cookie. */
@Service
public class ConductorOnboardingAccessService {

    private static final String TOKEN_PREFIX = "oa1.";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("oa1\\.[A-Za-z0-9_-]{43}");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private final ConductorOnboardingAccessRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final OpaqueTokenService opaqueTokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public ConductorOnboardingAccessService(
            ConductorOnboardingAccessRepository repository,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter loginRateLimiter,
            OpaqueTokenService opaqueTokenService,
            Clock clock
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
        this.opaqueTokenService = opaqueTokenService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("ruta-fija-onboarding-dummy-credential");
    }

    @Transactional
    public ApplicantAccess login(String email, String password) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String rateLimitKey = "onboarding:" + normalizedEmail;
        loginRateLimiter.checkAndRecordAttempt(rateLimitKey);
        var credential = repository.findCredential(normalizedEmail).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(password,
                credential == null ? dummyPasswordHash : credential.passwordHash());
        if (credential == null || !passwordMatches || !credential.emailVerified() || !credential.hasRequest()
                || !credential.accountAllowed()) {
            throw new InvalidCredentialsException();
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = now.plus(ACCESS_TTL);
        String token = TOKEN_PREFIX + opaqueTokenService.generate();
        repository.createToken(credential, opaqueTokenService.hash(token), now, expiresAt);
        loginRateLimiter.recordSuccess(rateLimitKey);
        return new ApplicantAccess(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<ConductorOnboardingAccessRepository.ApplicantRequest> requests(String token) {
        String hash = tokenHash(token);
        var identityId = repository.findIdentityForActiveToken(hash, Instant.now(clock))
                .orElseThrow(this::invalidToken);
        return repository.listRequests(identityId);
    }

    @Transactional
    public void logout(String token) {
        // Logout is idempotent and never reveals whether a submitted token exists.
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return;
        }
        repository.revokeToken(opaqueTokenService.hash(token), Instant.now(clock));
    }

    private String tokenHash(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw invalidToken();
        }
        return opaqueTokenService.hash(token);
    }

    private ApplicationException invalidToken() {
        return new ApplicationException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_TOKEN_INVALID,
                "El acceso de postulante no es valido o ha expirado.");
    }

    public record ApplicantAccess(String accessToken, Instant expiresAt) {
    }
}
