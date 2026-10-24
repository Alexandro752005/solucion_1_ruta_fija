package pe.rutafija.operation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.operation.domain.Announcement;
import pe.rutafija.operation.domain.AnnouncementAudienceType;

import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @EntityGraph(attributePaths = {"organization", "createdBy"})
    @Query("""
            select announcement from Announcement announcement
             where announcement.organization.id = :organizationId
               and (:audienceType is null or announcement.audienceType = :audienceType)
            """)
    Page<Announcement> search(
            @Param("organizationId") UUID organizationId,
            @Param("audienceType") AnnouncementAudienceType audienceType,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "createdBy"})
    @Query("""
            select announcement from Announcement announcement
             where announcement.organization.id = :organizationId
               and exists(select r from AnnouncementRecipient r where r.announcementId=announcement.id
                 and r.userId=:userId and r.organizationId=:organizationId and r.withdrawnAt is null)
            """)
    Page<Announcement> findVisibleToDriver(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "createdBy"})
    @Query("""
            select announcement from Announcement announcement
             where announcement.id = :announcementId
               and announcement.organization.id = :organizationId
               and exists(select r from AnnouncementRecipient r where r.announcementId=announcement.id
                 and r.userId=:userId and r.organizationId=:organizationId and r.withdrawnAt is null)
            """)
    java.util.Optional<Announcement> findVisibleById(
            @Param("announcementId") UUID announcementId,
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );

    java.util.Optional<Announcement> findByIdAndOrganization_Id(UUID id,UUID organizationId);
    java.util.Optional<Announcement> findByOrganization_IdAndOperationKey(UUID organizationId,UUID operationKey);
}
