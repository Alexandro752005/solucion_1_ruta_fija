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
               and (
                    announcement.audienceType = pe.rutafija.operation.domain.AnnouncementAudienceType.ORGANIZATION
                    or (
                        announcement.audienceType = pe.rutafija.operation.domain.AnnouncementAudienceType.GROUP
                        and exists (
                            select 1 from GroupCoordinator membership
                             where membership.group.id = announcement.audienceId
                               and membership.user.id = :coordinatorId
                        )
                    )
               )
               and (:audienceType is null or announcement.audienceType = :audienceType)
            """)
    Page<Announcement> searchVisibleToCoordinator(
            @Param("organizationId") UUID organizationId,
            @Param("coordinatorId") UUID coordinatorId,
            @Param("audienceType") AnnouncementAudienceType audienceType,
            Pageable pageable
    );
}
