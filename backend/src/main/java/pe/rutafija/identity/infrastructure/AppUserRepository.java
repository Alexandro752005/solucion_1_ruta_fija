package pe.rutafija.identity.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pe.rutafija.identity.domain.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findOneById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Lock only the account. Fetching organization in this locking query makes
    // Hibernate upgrade the already-held organization SHARE lock to UPDATE,
    // deadlocking concurrent writes. Organization access stays lazy in the
    // surrounding service transaction; its lifecycle lock is explicit there.
    @Query("select user from AppUser user where user.id = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findByIdAndOrganization_IdAndRole(UUID id, UUID organizationId, pe.rutafija.identity.domain.UserRole role);

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

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
