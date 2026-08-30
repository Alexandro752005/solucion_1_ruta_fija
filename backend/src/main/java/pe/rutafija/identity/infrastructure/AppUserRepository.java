package pe.rutafija.identity.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.identity.domain.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findOneById(UUID id);

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = "organization")
    @Query("""
            select user from AppUser user
             where user.organization.id = :organizationId
               and (:active is null or user.active = :active)
               and (:role is null or user.role = :role)
               and (
                    :search = ''
                    or lower(user.fullName) like lower(concat('%', :search, '%'))
                    or lower(user.email) like lower(concat('%', :search, '%'))
               )
            """)
    Page<AppUser> searchTenantUsers(
            @Param("organizationId") UUID organizationId,
            @Param("search") String search,
            @Param("active") Boolean active,
            @Param("role") pe.rutafija.identity.domain.UserRole role,
            Pageable pageable
    );

    boolean existsByEmailIgnoreCase(String email);
}
