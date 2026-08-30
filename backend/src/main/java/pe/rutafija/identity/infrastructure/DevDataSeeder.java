package pe.rutafija.identity.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DevSeedProperties properties;

    public DevDataSeeder(
            OrganizationRepository organizationRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DevSeedProperties properties
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.demoPassword() == null || properties.demoPassword().isBlank()) {
            log.warn("Development seed skipped: set DEMO_USER_PASSWORD to create demo users");
            return;
        }
        if (properties.demoPassword().length() < 12) {
            throw new IllegalStateException("DEMO_USER_PASSWORD must contain at least 12 characters");
        }

        Organization north = organization(
                "Empresa de Transporte Ruta Norte S.A.C.",
                "Ruta Norte"
        );
        Organization south = organization(
                "Empresa de Transporte Ruta Sur S.A.C.",
                "Ruta Sur"
        );
        String passwordHash = passwordEncoder.encode(properties.demoPassword());

        globalUser("superadmin@rutafija.local", "Superadministrador Ruta Fija", passwordHash);
        organizationUser(north, "admin.norte@rutafija.local", "Administrador Ruta Norte", UserRole.ADMINISTRADOR, passwordHash);
        organizationUser(north, "coordinador.norte@rutafija.local", "Coordinador Ruta Norte", UserRole.COORDINADOR, passwordHash);
        organizationUser(south, "admin.sur@rutafija.local", "Administrador Ruta Sur", UserRole.ADMINISTRADOR, passwordHash);
        organizationUser(south, "coordinador.sur@rutafija.local", "Coordinador Ruta Sur", UserRole.COORDINADOR, passwordHash);

        log.info("Development seed is ready: 2 organizations and 5 demo users");
    }

    private Organization organization(String legalName, String tradeName) {
        return organizationRepository.findByLegalNameIgnoreCase(legalName)
                .orElseGet(() -> organizationRepository.save(
                        Organization.active(legalName, tradeName, "America/Lima")
                ));
    }

    private void globalUser(String email, String fullName, String passwordHash) {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            userRepository.save(AppUser.superAdmin(email, passwordHash, fullName));
        }
    }

    private void organizationUser(
            Organization organization,
            String email,
            String fullName,
            UserRole role,
            String passwordHash
    ) {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            userRepository.save(AppUser.organizationUser(
                    organization,
                    email,
                    passwordHash,
                    fullName,
                    role
            ));
        }
    }
}
