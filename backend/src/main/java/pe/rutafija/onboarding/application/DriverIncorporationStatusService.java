package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.onboarding.api.dto.DriverIncorporationStatusResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** A snapshot of separate incorporation facts. Reading this cannot grant operational privileges. */
@Service
public class DriverIncorporationStatusService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService current;
    private final Clock clock;
    private final pe.rutafija.qualification.application.OperationalQualificationPolicy qualification;

    public DriverIncorporationStatusService(JdbcTemplate jdbc, CurrentUserService current, Clock clock,pe.rutafija.qualification.application.OperationalQualificationPolicy qualification) {
        this.jdbc = jdbc;
        this.current = current;
        this.clock = clock;
        this.qualification = qualification;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DriverIncorporationStatusResponse status(UUID driverId) {
        var actor = current.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN || !actor.isEmailVerified()) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN_ROLE,
                    "Se requiere ADMIN verificado de la organizacion.");
        }
        Instant now = Instant.now(clock);
        var drivers = jdbc.queryForList("""
                select d.id,d.user_id,d.active driver_enabled,
                       exists(select 1 from group_driver_membership gm join transport_group gg on gg.id=gm.group_id and gg.organization_id=gm.organization_id
                         where gm.driver_id=d.id and gm.organization_id=d.organization_id and gm.active and gg.active) group_active,
                       coalesce(u.active,false) account_enabled,
                       coalesce(u.role='CONDUCTOR' and u.organization_id=d.organization_id,false) account_matches,
                       exists(select 1 from person_identity p where p.user_id=u.id) identity_reviewed,
                       (select count(*) from driver_unit_period l where l.driver_id=d.id and l.status='AUTHORIZED' and l.starts_at<=? and (l.ends_at is null or l.ends_at>?)) active_links
                  from driver d join transport_group g on g.id=d.group_id and g.organization_id=d.organization_id
                  left join app_user u on u.id=d.user_id and u.organization_id=d.organization_id
                 where d.id=? and d.organization_id=?
                """, Timestamp.from(now),Timestamp.from(now),driverId, actor.getOrganizationId());
        if (drivers.size() != 1) throw new ApplicationException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "El conductor no existe en el alcance autorizado.");
        var driver = drivers.getFirst();
        UUID accountId = (UUID) driver.get("user_id");
        // Exact tenant + account + Driver correspondence. A pending legacy association
        // additionally requires its immutable ADMIN opening event; email/name never link it.
        var requests = jdbc.queryForList("""
                select r.id,r.status,r.personal_approved_at,r.provisioned_at,r.catalog_id,r.catalog_version,
                       r.catalog_hash,r.modality,r.linked_driver_id,r.linked_user_id
                  from conductor_onboarding_request r
                 where r.organization_id=? and (
                       (r.linked_driver_id=? and r.linked_user_id=?)
                    or exists(select 1 from conductor_person_binding b
                               where b.identity_id=r.identity_id and b.organization_id=r.organization_id
                                 and b.driver_id=? and b.user_id=?
                                 and exists(select 1 from conductor_onboarding_review_event e
                                             where e.request_id=r.id and e.resulting_request_id=r.id
                                               and e.actor_kind='ADMIN' and e.action='LEGACY_REGULARIZATION_OPENED')))
                """, actor.getOrganizationId(), driverId, accountId, driverId, accountId);
        boolean unambiguous = requests.size() == 1;
        Map<String,Object> request = unambiguous ? requests.getFirst() : Map.of();
        UUID requestId = unambiguous ? (UUID) request.get("id") : null;
        String state = unambiguous ? String.valueOf(request.get("status"))
                : requests.isEmpty() ? "NO_LINKED_REQUEST" : "REQUIRES_REVIEW";
        boolean personalApproved = "APPROVED".equals(state) && request.get("personal_approved_at") != null;
        boolean provisioned = personalApproved && request.get("provisioned_at") != null
                && driverId.equals(request.get("linked_driver_id")) && Objects.equals(accountId,request.get("linked_user_id"))
                && Boolean.TRUE.equals(driver.get("account_matches"));
        String documents = requestId == null ? "UNKNOWN" : documentaryState(requestId, request, now);
        long links = ((Number)driver.get("active_links")).longValue();
        List<String> blockers = new ArrayList<>();
        if (!unambiguous) blockers.add("INCORPORATION_REQUIRES_REVIEW");
        if (!personalApproved) blockers.add("PERSONAL_APPROVAL_MISSING");
        if (!provisioned) blockers.add("ACCOUNT_NOT_PROVISIONED");
        if (!Boolean.TRUE.equals(driver.get("account_enabled"))) blockers.add("ACCOUNT_DISABLED");
        if (!Boolean.TRUE.equals(driver.get("identity_reviewed"))) blockers.add("IDENTITY_NOT_REVIEWED");
        if (!Boolean.TRUE.equals(driver.get("group_active"))) blockers.add("GROUP_INACTIVE");
        if (!"CURRENT".equals(documents)) blockers.add("EXPIRED".equals(documents)
                ? "PERSONAL_DOCUMENTS_EXPIRED" : "PERSONAL_DOCUMENTS_REVIEW_REQUIRED");
        if (!Boolean.TRUE.equals(driver.get("driver_enabled"))) blockers.add("DRIVER_DISABLED");
        if (links == 0) blockers.add("UNIT_LINK_MISSING");
        if (links > 1) blockers.add("UNIT_LINK_CARDINALITY_CONFLICT");
        // Same fail-closed fact as ConductorProvisioningPolicy.requireOperationalEligibility().
        // Active legacy links do not prove unit/title review or authorized periods.
        blockers.addAll(qualification.blockers(driverId,actor.getOrganizationId(),null,now,now.plusNanos(1000),true));
        return new DriverIncorporationStatusResponse(driverId, accountId, requestId, state,
                personalApproved, provisioned, Boolean.TRUE.equals(driver.get("account_enabled")),
                Boolean.TRUE.equals(driver.get("identity_reviewed")), Boolean.TRUE.equals(driver.get("group_active")),
                Boolean.TRUE.equals(driver.get("driver_enabled")), documents, links, links <= 1,
                blockers.isEmpty(), blockers, now);
    }

    private String documentaryState(UUID requestId, Map<String,Object> request, Instant now) {
        if (!ConductorDocumentaryCatalog.ID.equals(request.get("catalog_id"))
                || !ConductorDocumentaryCatalog.VERSION.equals(request.get("catalog_version"))
                || !ConductorDocumentaryCatalog.HASH.equals(request.get("catalog_hash"))) return "UNKNOWN";
        // Read metadata only: no DNI, license, names, original documents or HMACs leave the restricted dossier.
        var rows = jdbc.queryForList("""
                select distinct on (requirement_id) requirement_id,result,expiry_mode,valid_until,applicability,
                       catalog_id,catalog_version,catalog_hash,actor_kind,
                       source_reference is not null and length(btrim(source_reference))>0 source_recorded,
                       review_reference is not null and length(btrim(review_reference))>0 review_recorded
                  from conductor_document_revision where request_id=? order by requirement_id,revision_no desc
                """, requestId);
        boolean expired = rows.stream().anyMatch(row -> "CONFORME".equals(row.get("result")) && "DATED".equals(row.get("expiry_mode"))
                && row.get("valid_until") instanceof Timestamp expiry && !expiry.toInstant().isAfter(now));
        if (expired) return "EXPIRED";
        for (var definition : ConductorDocumentaryCatalog.REQUIREMENTS) {
            var row = rows.stream().filter(value -> definition.requirementId().equals(value.get("requirement_id")))
                    .findFirst().orElse(null);
            if (row == null || !"CONFIRMED".equals(row.get("applicability"))
                    || !"ADMIN".equals(row.get("actor_kind"))
                    || !ConductorDocumentaryCatalog.ID.equals(row.get("catalog_id"))
                    || !ConductorDocumentaryCatalog.VERSION.equals(row.get("catalog_version"))
                    || !ConductorDocumentaryCatalog.HASH.equals(row.get("catalog_hash"))
                    || !Boolean.TRUE.equals(row.get("source_recorded")) || !Boolean.TRUE.equals(row.get("review_recorded")))
                return "REVIEW_REQUIRED";
            if ("NO_APLICA_JUSTIFICADO".equals(row.get("result")) && !definition.mandatory()) {
                if ("CON-05".equals(definition.requirementId()) && "COLECTIVO".equals(request.get("modality")))
                    return "REVIEW_REQUIRED";
                continue;
            }
            if (!"CONFORME".equals(row.get("result"))) return "REVIEW_REQUIRED";
            if ("CON-02".equals(definition.requirementId()) && !"DATED".equals(row.get("expiry_mode")))
                return "REVIEW_REQUIRED";
            if (!"NO_EXPIRY".equals(row.get("expiry_mode")) && !("DATED".equals(row.get("expiry_mode"))
                    && row.get("valid_until") instanceof Timestamp expiry && expiry.toInstant().isAfter(now))) return "UNKNOWN";
        }
        return "CURRENT";
    }
}
