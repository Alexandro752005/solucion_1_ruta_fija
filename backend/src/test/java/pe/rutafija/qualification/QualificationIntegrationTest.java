package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;
import pe.rutafija.identity.application.JwtService;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.qualification.application.QualificationCatalog;
import pe.rutafija.qualification.application.QualificationMaintenanceService;
import pe.rutafija.qualification.application.OperationalQualificationPolicy;
import pe.rutafija.qualification.application.DriverQualificationService;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.support.ApprovedConductorFixture;
import pe.rutafija.support.L02PostgresIntegrationTest;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL and Spring HTTP filters. Only fixture/mail are synthetic.
 * Every case rolls back; existing L01 identities, acceptances and ledger survive.
 * This class does not claim parallel-transaction or human/device evidence.
 */
@SpringBootTest(properties={"debug=false","logging.level.root=ERROR","logging.level.org.hibernate.SQL=OFF",
        "app.qualification.validity-sweep=PT24H"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(QualificationIntegrationTest.ClockConfiguration.class)
class QualificationIntegrationTest extends L02PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired TransportGroupRepository groups;
    @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles;
    @Autowired PasswordEncoder passwords;
    @Autowired InvitationCodeHasher hasher;
    @Autowired JwtService jwt;
    @Autowired OperationalQualificationPolicy policy;
    @Autowired DriverQualificationService qualification;
    @Autowired AuditService audit;
    @Autowired pe.rutafija.qualification.infrastructure.QualificationRepository qualificationRepository;
    @Autowired pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository documentaryRepository;
    private QualificationMaintenanceService maintenance;
    @Autowired QualificationClock clock;
    @MockitoBean OtpEmailSender mail;

    protected Organization organization,otherOrganization;
    protected AppUser admin,conductor;
    protected Driver driver,otherDriver;
    protected Vehicle vehicle,otherVehicle;
    private UUID requestId;
    protected String dni,adminToken,mobileToken;
    protected Instant initial;
    private final String syntheticName="Synthetic Qualification Person";

    @BeforeEach
    void fixture(TestInfo info) throws Exception {
        initial=Instant.now().truncatedTo(ChronoUnit.MICROS);clock.set(initial);
        String suffix=UUID.randomUUID().toString().substring(0,8);
        dni=String.format(Locale.ROOT,"7%07d",Math.floorMod(UUID.randomUUID().hashCode(),10_000_000));
        organization=organizations.saveAndFlush(Organization.active("Synthetic L02 "+suffix,"Synthetic","America/Lima"));
        otherOrganization=organizations.saveAndFlush(Organization.active("Other Synthetic L02 "+suffix,"Other Synthetic","America/Lima"));
        admin=account("admin."+suffix,UserRole.ADMIN,"Synthetic Qualification Reviewer");
        conductor=account("conductor."+suffix,UserRole.CONDUCTOR,syntheticName);
        TransportGroup group=groups.saveAndFlush(TransportGroup.active(organization,"Synthetic L02 group "+suffix,null));
        TransportGroup otherGroup=groups.saveAndFlush(TransportGroup.active(otherOrganization,"Other Synthetic L02 group "+suffix,null));
        driver=drivers.saveAndFlush(Driver.register(organization,conductor,group,syntheticName,"999999999","DNI",dni,"SYNTHETIC-LICENSE"));
        driver.deactivate();drivers.flush();
        otherDriver=drivers.saveAndFlush(Driver.register(otherOrganization,null,otherGroup,"Other Synthetic Person",null,"DNI","8"+dni.substring(1),null));
        vehicle=vehicles.saveAndFlush(Vehicle.register(organization,"SYN-"+suffix.substring(0,6),"SYNTHETIC","SYNTHETIC",(short)2025,null));
        otherVehicle=vehicles.saveAndFlush(Vehicle.register(otherOrganization,"OTH-"+suffix.substring(0,6),null,null,null,null));
        String test=info.getTestMethod().orElseThrow().getName();
        if(!Set.of("missingIdentityIsRejectedByTheActualIdentityGate","marginMissingIdentityIsRejectedByTheActualIdentityGate","serviceMissingIdentityUsesTheActualGate","gpsMissingIdentityUsesTheActualGate","timesMissingIdentityUsesTheActualGate").contains(test))bindSyntheticIdentity(admin,"4"+dni.substring(1));
        bindSyntheticIdentity(conductor,dni);
        UUID document=jdbc.queryForObject("select id from admin_terms_document where document_key='ADMIN_TERMS' and version='1.0.0' and not synthetic",UUID.class);
        if(!Set.of("missingCurrentTermsAreRejectedByTheActualTermsGate","marginMissingCurrentTermsAreRejectedByTheActualTermsGate","serviceMissingTermsUsesTheActualGate","gpsMissingTermsUsesTheActualGate","timesMissingTermsUsesTheActualGate").contains(test))jdbc.update("""
                insert into admin_terms_acceptance(id,admin_user_id,organization_id,document_id,document_version,content_sha256,accepted_at)
                select ?,?,?,id,version,content_sha256,? from admin_terms_document where id=?
                """,UUID.randomUUID(),admin.getId(),organization.getId(),Timestamp.from(initial),document);
        requestId=ApprovedConductorFixture.recordHistoricalApproval(jdbc,admin,conductor,driver);
        jdbc.update("update conductor_onboarding_request set modality='TAXI' where id=?",requestId);
        for(var item:personalDocuments(initial.plus(Duration.ofDays(30)))) seedDocument(item,1);
        adminToken=jwt.issueAccessToken(admin);
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId());
        // Scheduled execution is disabled. Exercise the same service explicitly
        // inside this test's rollback transaction, without a background writer.
        maintenance=new QualificationMaintenanceService(jdbc,drivers,policy,qualification,audit);
        entities.flush();
    }

    @Test void ordinaryStatusSeparatesHistoricalApprovalAndOperationalEligibilityWithoutPii() throws Exception {
        var view=read().andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.operationalEnabled").value(false)).andExpect(jsonPath("$.personalDocumentsStatus").value("CURRENT"))
                .andExpect(jsonPath("$.personalDocuments").isEmpty());
        assertThat(json(view).toString()).doesNotContain(syntheticName,dni,"SYNTHETIC-LICENSE",conductor.getEmail(),"personHash");
        mvc.perform(get("/api/v1/drivers/{id}/incorporation-status",driver.getId()).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personalApproved").value(true))
                .andExpect(jsonPath("$.accountProvisioned").value(true)).andExpect(jsonPath("$.driverEnabled").value(false));
        verifyNoInteractions(mail);
    }

    @Test void realBearerFiltersAndObjectScopeRejectForeignDriversAndConductorAdministration() throws Exception {
        mvc.perform(get(path(otherDriver.getId(),"")).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get(path(driver.getId(),"")).header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(get(path(driver.getId(),""))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(driver.getId(),"/expediente")).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.personalDocuments").isNotEmpty());
    }

    @Test void missingCurrentTermsAreRejectedByTheActualTermsGate() throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true));
        mvc.perform(get("/api/v1/admin/terms/current").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
        read().andExpect(status().isForbidden());
    }

    @Test void missingIdentityIsRejectedByTheActualIdentityGate() throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(false));
        read().andExpect(status().isForbidden());
    }

    @Test void inactiveProvisionedConductorRetainsOwnLimitedQualificationButCannotOperate() throws Exception {
        mvc.perform(get("/api/v1/mobile/qualification").header("Authorization","Bearer "+mobileToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.driverId").value(driver.getId().toString()))
                .andExpect(jsonPath("$.operationalEnabled").value(false)).andExpect(jsonPath("$.personalDocuments").isEmpty());
        mvc.perform(put("/api/v1/mobile/availability").header("Authorization","Bearer "+mobileToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DRIVER_INACTIVE"));
    }

    @Test void mobileLoginAndRefreshRecomputeLimitedAndOperationalScopesAgainstCurrentServerState() throws Exception {
        JsonNode login=json(mvc.perform(post("/api/v1/mobile/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email",conductor.getEmail(),"password","Synthetic-L02-Password-2026"))))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.accessScope").value("INCORPORATION")));
        String limited=login.path("accessToken").asText();String refresh=login.path("refreshToken").asText();
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        // An old limited token does not silently gain operational scope.
        mvc.perform(get("/api/v1/mobile/availability").header("Authorization","Bearer "+limited))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DRIVER_INACTIVE"));
        JsonNode rotated=json(mvc.perform(post("/api/v1/mobile/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("refreshToken",refresh))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessScope").value("OPERATIONS")));
        String operational=rotated.path("accessToken").asText();
        mvc.perform(get("/api/v1/mobile/availability").header("Authorization","Bearer "+operational)).andExpect(status().isOk());
        var suspension=basic(4);suspension.put("evidenceReference","SYNTHETIC safety review");
        mutate("/suspend",suspension).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mobile/availability").header("Authorization","Bearer "+operational)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/mobile/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("refreshToken",rotated.path("refreshToken").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessScope").value("INCORPORATION"));
        verifyNoInteractions(mail);
    }

    @Test void unknownReviewDoesNotEnableAUnitOrTurnTheHistoricalDriverActive() throws Exception {
        var command=reviews("VEHICLE",vehicle.getId(),0,false);
        @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)command.get("documents");
        items.getFirst().put("applicability","POR_CONTRASTAR");items.getFirst().put("result","POR_CONTRASTAR");
        mutate("/reviews",command).andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driver.getId())).isFalse();
        mutate("/periods",period(1,vehicle.getId(),"OWN",initial.minusSeconds(1),initial.plusSeconds(1000)))
                .andExpect(status().isConflict());
    }

    @Test void sameOperationIsIdempotentAndChangedPayloadOrStaleVersionConflicts() throws Exception {
        var command=reviews("VEHICLE",vehicle.getId(),0,true);
        var first=json(mutate("/reviews",command).andExpect(status().isOk()));
        assertThat(first.path("version").asLong()).isEqualTo(1);
        var repeated=json(mutate("/reviews",command).andExpect(status().isOk()));
        assertThat(repeated.path("version").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from qualification_operation where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
        command.put("reason","SYNTHETIC altered reason");mutate("/reviews",command).andExpect(status().isConflict());
    }

    @Test void staleVersionCannotReplaceCurrentReview() throws Exception {
        mutate("/reviews",reviews("VEHICLE",vehicle.getId(),0,true)).andExpect(status().isOk());
        mutate("/reviews",reviews("COMPANY",null,0,true)).andExpect(status().isConflict());
    }

    @Test void categoryM2CannotBeApprovedByInferringCategoryFromWheelsOrRows() throws Exception {
        var command=reviews("VEHICLE",vehicle.getId(),0,true);
        @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)command.get("documents");
        @SuppressWarnings("unchecked") var fields=(Map<String,String>)items.getFirst().get("fields");
        fields.put("category","M2");
        mutate("/reviews",command).andExpect(status().isConflict());
    }

    @Test void companyPeriodRequiresEndEvenWithAllCurrentReviews() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"COMPANY",initial.minusSeconds(1),null)).andExpect(status().isBadRequest());
    }

    @Test void limaDateIncludesTheWholeFinalDayAndPreservesHistoricalPeriod() throws Exception {
        reviewAll();
        LocalDate date=initial.atZone(ZoneId.of("America/Lima")).toLocalDate();
        var command=basic(2);command.put("vehicleId",vehicle.getId());command.put("kind","COMPANY");
        command.put("startDate",date);command.put("endDate",date);
        JsonNode state=json(mutate("/periods",command).andExpect(status().isOk()));
        JsonNode saved=state.path("periods").get(0);
        assertThat(Instant.parse(saved.path("startsAt").asText())).isEqualTo(date.atStartOfDay(ZoneId.of("America/Lima")).toInstant());
        assertThat(Instant.parse(saved.path("endsAt").asText())).isEqualTo(date.plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).toInstant());
        assertThat(saved.path("status").asText()).isEqualTo("AUTHORIZED");
    }

    @Test void periodCannotExtendBeyondReviewedValidityWhileApprovalAndAccountSurvive() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),initial.plus(Duration.ofDays(60))))
                .andExpect(status().isConflict()); // Reviewed papers do not cover the entire proposed period.
        assertThat(jdbc.queryForObject("select personal_approved_at is not null from conductor_onboarding_request where id=?",Boolean.class,requestId)).isTrue();
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,conductor.getId())).isTrue();
    }

    @Test void activationRequiresCurrentLicenseAndExpirationKeepsPersonalApprovalAndLimitedConsultation() throws Exception {
        seedDocument(personalDocuments(initial.plus(Duration.ofHours(1))).get(1),2);
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(true));
        clock.set(initial.plus(Duration.ofHours(2)));
        read().andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false))
                .andExpect(jsonPath("$.blockers").value(org.hamcrest.Matchers.hasItem("CON-02_VALIDITY_REQUIRED")));
        mvc.perform(get("/api/v1/mobile/qualification").header("Authorization","Bearer "+mobileToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        mutate("/activate",basic(4)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select personal_approved_at is not null from conductor_onboarding_request where id=?",Boolean.class,requestId)).isTrue();
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,conductor.getId())).isTrue();
    }

    @Test void overlappingPeriodIsRejectedWithoutReplacingTheExistingPeriod() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial,initial.plusSeconds(3600))).andExpect(status().isOk());
        mutate("/periods",period(3,vehicle.getId(),"OWN",initial.plusSeconds(1800),initial.plusSeconds(5400)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void adjacentPeriodsAreAllowedAndRevokePreservesHistoryWithoutDeletingApproval() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial,initial.plusSeconds(3600))).andExpect(status().isOk());
        JsonNode state=json(mutate("/periods",period(3,vehicle.getId(),"COMPANY",initial.plusSeconds(3600),initial.plusSeconds(7200))).andExpect(status().isOk()));
        String period=state.path("periods").get(1).path("id").asText();
        mutate("/periods/"+period+"/revoke",basic(4)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=?",Integer.class,driver.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from driver_unit_period where id=?",String.class,UUID.fromString(period))).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select personal_approved_at is not null from conductor_onboarding_request where id=?",Boolean.class,requestId)).isTrue();
    }

    @Test void revokingOneFuturePeriodOnlyInvalidatesServicesThatLoseTheirOwnCoverage() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"COMPANY",initial.minusSeconds(60),initial.plusSeconds(3600))).andExpect(status().isOk());
        JsonNode second=json(mutate("/periods",period(3,vehicle.getId(),"COMPANY",initial.plusSeconds(3600),initial.plusSeconds(7200))).andExpect(status().isOk()));
        String futurePeriod=second.path("periods").get(1).path("id").asText();
        mutate("/activate",basic(4)).andExpect(status().isOk());
        UUID retained=assignment("SCHEDULED",initial.plusSeconds(1800),initial.plusSeconds(2700));
        UUID affected=assignment("SCHEDULED",initial.plusSeconds(4200),initial.plusSeconds(5400));
        mutate("/periods/"+futurePeriod+"/revoke",basic(5)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalEnabled").value(true));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,retained)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,affected)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from qualification_service_attention where assignment_id=?",Integer.class,retained)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from qualification_service_attention where assignment_id=?",Integer.class,affected)).isEqualTo(1);
    }

    @Test void operationalSuspensionDoesNotDisableAccountAndResumeRequiresAReviewedCurrentPeriod() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        var suspension=basic(4);suspension.put("evidenceReference","SYNTHETIC reviewed incident");
        mutate("/suspend",suspension).andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.operationalEnabled").value(false));
        mvc.perform(get("/api/v1/mobile/qualification").header("Authorization","Bearer "+mobileToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(true));
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,conductor.getId())).isTrue();
        mutate("/resume",basic(5)).andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(false))
                .andExpect(jsonPath("$.operationalEnabled").value(true));
    }

    @Test void suspensionCancelsFutureOccupationsAndPreservesInServiceForSafeHandover() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(86400),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        UUID pending=assignment("PENDING_RESPONSE",initial.plusSeconds(7200),initial.plusSeconds(10800));
        UUID future=assignment("SCHEDULED",initial.plusSeconds(14400),initial.plusSeconds(18000));
        UUID ongoing=assignment("EN_SERVICIO",initial.minusSeconds(3600),initial.plusSeconds(3600));
        jdbc.update("update driver set availability_status='EN_SERVICIO' where id=?",driver.getId());
        jdbc.update("update vehicle set status='EN_SERVICIO' where id=?",vehicle.getId());
        var suspension=basic(4);suspension.put("evidenceReference","SYNTHETIC reviewed safety issue");
        mutate("/suspend",suspension).andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(true));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,pending)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,future)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,ongoing)).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select count(*) from qualification_service_attention where driver_id=? and kind='PENDING_REASSIGNMENT'",Integer.class,driver.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from qualification_service_attention where driver_id=? and kind='SAFE_HANDOVER_REQUIRED'",Integer.class,driver.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from incident where assignment_id=? and category='OTRO' and status='OPEN'",Integer.class,ongoing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select status from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("EN_SERVICIO");
        mutate("/suspend",suspension).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from incident where assignment_id=?",Integer.class,ongoing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,conductor.getId())).isTrue();
    }

    @Test void periodCannotBeRevokedWhileItsUnitIsInService() throws Exception {
        reviewAll();
        var view=json(mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(86400),null)).andExpect(status().isOk()));
        String periodId=view.path("periods").get(0).path("id").asText();
        assignment("EN_SERVICIO",initial.minusSeconds(3600),initial.plusSeconds(3600));
        mutate("/periods/"+periodId+"/revoke",basic(3)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from driver_unit_period where id=?",String.class,UUID.fromString(periodId))).isEqualTo("AUTHORIZED");
    }

    @Test void expiryMaintenanceRequiresSafeHandoverAndAdminCannotCompleteOrDeleteTheUnitPeriod() throws Exception {
        seedDocument(personalDocuments(initial.plus(Duration.ofHours(1))).get(1),2);
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(86400),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        UUID ongoing=assignment("EN_SERVICIO",initial.minusSeconds(3600),initial.plusSeconds(10800));
        jdbc.update("update driver set availability_status='EN_SERVICIO' where id=?",driver.getId());
        jdbc.update("update vehicle set status='EN_SERVICIO' where id=?",vehicle.getId());
        entities.clear();clock.set(initial.plusSeconds(7200));
        maintenance.expireOperationalEligibility();maintenance.expireOperationalEligibility();
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,ongoing)).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select count(*) from incident where assignment_id=?",Integer.class,ongoing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select source from incident where assignment_id=?",String.class,ongoing)).isEqualTo("SYSTEM");
        assertThat(jdbc.queryForObject("select reported_by is null from incident where assignment_id=?",Boolean.class,ongoing)).isTrue();
        UUID systemIncident=jdbc.queryForObject("select id from incident where assignment_id=?",UUID.class,ongoing);
        mvc.perform(get("/api/v1/incidents/{id}",systemIncident).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("SYSTEM"))
                .andExpect(jsonPath("$.reportedById").doesNotExist()).andExpect(jsonPath("$.reportedByName").value("Sistema"));
        assertThat(jdbc.queryForObject("select kind from qualification_service_attention where assignment_id=?",String.class,ongoing)).isEqualTo("SAFE_HANDOVER_REQUIRED");
        entities.clear();
        mvc.perform(post("/api/v1/assignments/{id}/complete",ongoing).header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        // L05 reserves completion to CONDUCTOR. An ADMIN attempt cannot close
        // the service, resolve its attention or erase the authorized unit period.
        maintenance.expireOperationalEligibility();
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,ongoing)).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select status from qualification_service_attention where assignment_id=?",String.class,ongoing)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and status='AUTHORIZED'",Integer.class,driver.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select personal_approved_at is not null from conductor_onboarding_request where id=?",Boolean.class,requestId)).isTrue();
    }

    @Test void personalRenewalIsAppendOnlyAndKeepsStableIdentityAndDriver() throws Exception {
        String before=jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,conductor.getId());
        var command=basic(0);command.put("samePersonConfirmed",true);command.put("originalReviewed",true);
        command.put("reviewReference","SYNTHETIC restricted visual review");
        var docs=personalDocuments(initial.plus(Duration.ofDays(60)));
        command.put("documents",List.of(docs.get(1)));
        mutate("/personal-documents",command).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,conductor.getId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from conductor_document_revision where request_id=? and requirement_id='CON-02'",Integer.class,requestId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select linked_user_id from conductor_onboarding_request where id=?",UUID.class,requestId)).isEqualTo(conductor.getId());
        assertThat(jdbc.queryForObject("select linked_driver_id from conductor_onboarding_request where id=?",UUID.class,requestId)).isEqualTo(driver.getId());
    }

    @Test void rootDniCannotBeReplacedByMaterialCorrection() throws Exception {
        var document=personalDocuments(initial.plus(Duration.ofDays(60))).getFirst();
        @SuppressWarnings("unchecked") var fields=(Map<String,String>)document.get("fields");fields.put("documentNumber","8"+dni.substring(1));
        var command=basic(0);command.put("samePersonConfirmed",true);command.put("originalReviewed",true);
        command.put("reviewReference","SYNTHETIC restricted review");command.put("documents",List.of(document));
        mutate("/personal-documents",command).andExpect(status().isConflict());
    }

    @Test void materialTypoCanBeCorrectedBackToReviewedRootWithoutReplacingThePerson() throws Exception {
        String before=jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,conductor.getId());
        jdbc.update("update driver set document_number=? where id=?","8"+dni.substring(1),driver.getId());
        entities.clear();var command=basic(0);command.put("samePersonConfirmed",true);command.put("originalReviewed",true);
        command.put("reviewReference","SYNTHETIC original cotejo");command.put("documents",List.of(personalDocuments(initial.plus(Duration.ofDays(60))).getFirst()));
        mutate("/personal-documents",command).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select document_number from driver where id=?",String.class,driver.getId())).isEqualTo(dni);
        assertThat(jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,conductor.getId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from conductor_document_revision where request_id=? and requirement_id='CON-01'",Integer.class,requestId)).isEqualTo(2);
    }

    @Test void plateCorrectionKeepsVehicleAndOriginalServiceSnapshotAndRequiresReviewAgain() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        declareAvailable();UUID routeVersion=routeFixture();
        var creation=Map.of("serviceTypeVersionId",serviceTypeFixture(),"routeVersionId",routeVersion,"driverId",driver.getId(),"vehicleId",vehicle.getId(),"originText","SYNTHETIC origin","destinationText","SYNTHETIC destination",
                "scheduledAt",initial.plusSeconds(3600),"scheduledEndAt",initial.plusSeconds(7200),"responseMode","MOBILE_CONFIRMATION");
        JsonNode service=json(mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(creation))).andExpect(status().isCreated()));
        String old=vehicle.getPlate();var correction=basic(4);correction.put("vehicleId",vehicle.getId());correction.put("plate","SYN-NEW");
        correction.put("originalReviewed",true);correction.put("reviewReference","SYNTHETIC same unit correction");
        mutate("/vehicle-correction",correction).andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        mvc.perform(get("/api/v1/assignments/{id}",service.path("id").asText()).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicleId").value(vehicle.getId().toString())).andExpect(jsonPath("$.vehiclePlate").value(old));
        assertThat(jdbc.queryForObject("select plate from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("SYN-NEW");
        assertThat(jdbc.queryForObject("select previous_plate from vehicle_plate_revision where vehicle_id=?",String.class,vehicle.getId())).isEqualTo(old);
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void explicitRenewalDuringOverrunPreservesBothPeriodsAndPhysicalOccupation() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"COMPANY",initial.minusSeconds(3600),initial.plusSeconds(3600))).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        UUID service=assignment("EN_SERVICIO",initial.minusSeconds(1800),initial.plusSeconds(1800));
        jdbc.update("update driver set availability_status='EN_SERVICIO' where id=?",driver.getId());
        jdbc.update("update vehicle set status='EN_SERVICIO' where id=?",vehicle.getId());entities.clear();clock.set(initial.plusSeconds(7200));
        read().andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        mutate("/periods",period(4,vehicle.getId(),"COMPANY",initial.plusSeconds(3600),initial.plusSeconds(10800))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and status='AUTHORIZED'",Integer.class,driver.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,service)).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select status from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("EN_SERVICIO");
        read().andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(true));
    }

    @Test void mobileAcceptanceChecksTheEntireDrivingWindowEvenWhenLicenseIsValidNow() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        declareAvailable();UUID routeVersion=routeFixture();
        var creation=Map.of("serviceTypeVersionId",serviceTypeFixture(),"routeVersionId",routeVersion,"driverId",driver.getId(),"vehicleId",vehicle.getId(),"originText","SYNTHETIC origin","destinationText","SYNTHETIC destination",
                "scheduledAt",initial.plusSeconds(7200),"scheduledEndAt",initial.plusSeconds(10800),"responseMode","MOBILE_CONFIRMATION","responseDeadlineAt",initial.plusSeconds(3600));
        JsonNode service=json(mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(creation))).andExpect(status().isCreated()));
        seedDocument(personalDocuments(initial.plusSeconds(1800)).get(1),2);
        String operational=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        var command=Map.of("clientEventId",UUID.randomUUID(),"version",service.path("version").asLong(),"occurredAt",initial);
        mvc.perform(post("/api/v1/mobile/assignments/{id}/accept",service.path("id").asText()).header("Authorization","Bearer "+operational)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CON-02_VALIDITY_REQUIRED")));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(service.path("id").asText()))).isEqualTo("PENDING_RESPONSE");
    }

    @Test void operationalTokenCannotEnableTheOfflineRouteExcludedByTheContract() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        String operational=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        mvc.perform(post("/api/v1/mobile/sync/events").header("Authorization","Bearer "+operational)
                .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test void operationalReturnSwitchFailsClosedAndPreservesTheApprovedDossier() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        var stopped=new OperationalQualificationPolicy(jdbc,qualificationRepository,documentaryRepository,clock,false);
        assertThat(stopped.blockers(driver.getId(),organization.getId(),vehicle.getId(),initial,initial.plusSeconds(1),true)).contains("OPERATIONAL_FEATURE_DISABLED");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->stopped.requireActivation(driver.getId(),organization.getId()))
                .isInstanceOf(pe.rutafija.shared.exception.ApplicationException.class);
        assertThat(jdbc.queryForObject("select personal_approved_at is not null from conductor_onboarding_request where id=?",Boolean.class,requestId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void incorporationSummaryUsesTheSameEligibilityAndCurrentTemporalCardinality() throws Exception {
        String url="/api/v1/drivers/"+driver.getId()+"/incorporation-status";
        mvc.perform(get(url).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalEnabled").value(false)).andExpect(jsonPath("$.activeVehicleLinks").value(0));
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalEnabled").value(true)).andExpect(jsonPath("$.activeVehicleLinks").value(1));
    }

    @Test void unitRequestIsOwnScopedIdempotentAndDoesNotCreateVehicleOrActivateDriver() throws Exception {
        int vehiclesBefore=jdbc.queryForObject("select count(*) from vehicle",Integer.class);
        var command=basic(0);command.put("kind","OWN");command.put("reference","SYNTHETIC original review pending");
        var first=mvc.perform(post("/api/v1/mobile/qualification/unit-requests").header("Authorization","Bearer "+mobileToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andExpect(status().isOk());
        UUID id=UUID.fromString(json(first).path("unitRequests").get(0).path("id").asText());
        mvc.perform(post("/api/v1/mobile/qualification/unit-requests").header("Authorization","Bearer "+mobileToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_request where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vehicle",Integer.class)).isEqualTo(vehiclesBefore);
        var decision=basic(1);decision.put("approved",false);
        mutate("/unit-requests/"+id+"/decision",decision).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from driver_unit_request where id=?",String.class,id)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driver.getId())).isFalse();
    }

    @Test void foreignVehicleCannotBeSubmittedForReview() throws Exception {
        mutate("/reviews",reviews("VEHICLE",otherVehicle.getId(),0,true)).andExpect(status().isNotFound());
    }

    @Test void aRejectedUnitRequestCannotBeLinkedToAnotherOrganizationsVehicle() throws Exception {
        var command=basic(0);command.put("kind","OWN");command.put("reference","SYNTHETIC original review pending");
        JsonNode state=json(mvc.perform(post("/api/v1/mobile/qualification/unit-requests").header("Authorization","Bearer "+mobileToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andExpect(status().isOk()));
        String request=state.path("unitRequests").get(0).path("id").asText();
        var decision=basic(1);decision.put("approved",false);decision.put("vehicleId",otherVehicle.getId());
        mutate("/unit-requests/"+request+"/decision",decision).andExpect(status().isNotFound());
    }

    @Test void ordinaryHistoryReferencesCannotRepeatRestrictedPersonalInformation() throws Exception {
        var command=basic(0);command.put("kind","OWN");command.put("reference","Revisar a "+syntheticName);
        mvc.perform(post("/api/v1/mobile/qualification/unit-requests").header("Authorization","Bearer "+mobileToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_request where driver_id=?",Integer.class,driver.getId())).isZero();
    }

    @Test void unknownInsuranceCannotBeApprovedAsSoatOrCat() throws Exception {
        var command=reviews("VEHICLE",vehicle.getId(),0,true);
        @SuppressWarnings("unchecked") var documents=(List<Map<String,Object>>)command.get("documents");
        @SuppressWarnings("unchecked") var fields=(Map<String,String>)documents.get(1).get("fields");fields.put("insuranceType","UNKNOWN");
        mutate("/reviews",command).andExpect(status().isConflict());
    }

    @Test void catNeedsExplicitApplicableCoverage() throws Exception {
        var command=reviews("VEHICLE",vehicle.getId(),0,true);
        @SuppressWarnings("unchecked") var documents=(List<Map<String,Object>>)command.get("documents");
        @SuppressWarnings("unchecked") var fields=(Map<String,String>)documents.get(1).get("fields");fields.put("insuranceType","CAT");documents.get(1).put("condition",null);
        mutate("/reviews",command).andExpect(status().isConflict());
    }

    @Test void anUnapprovedInspectionCannotBeMarkedConforming() throws Exception {
        var command=reviews("VEHICLE",vehicle.getId(),0,true);
        @SuppressWarnings("unchecked") var documents=(List<Map<String,Object>>)command.get("documents");
        @SuppressWarnings("unchecked") var fields=(Map<String,String>)documents.get(2).get("fields");fields.put("inspectionResult","DESAPROBADO");
        mutate("/reviews",command).andExpect(status().isConflict());
    }

    @Test void nullDocumentItemsAreRejectedBeforeAnyReviewMutation() throws Exception {
        var review=basic(0);review.put("scope","COMPANY");review.put("documents",Collections.singletonList(null));
        mutate("/reviews",review).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        var personal=basic(0);personal.put("samePersonConfirmed",true);personal.put("originalReviewed",true);
        personal.put("reviewReference","SYNTHETIC visual review");personal.put("documents",Collections.singletonList(null));
        mutate("/personal-documents",personal).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("select count(*) from qualification_review where driver_id=?",Integer.class,driver.getId())).isZero();
    }

    @Test void legacyDriverWithoutLinkedRequestRemainsUnapprovedAndCannotBeAdoptedByReading() throws Exception {
        var legacy=drivers.saveAndFlush(Driver.register(organization,null,driver.getGroup(),"SYNTHETIC unlinked record",null,"DNI","9"+dni.substring(1),null));
        mvc.perform(get("/api/v1/drivers/{id}/incorporation-status",legacy.getId()).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incorporationStatus").value("NO_LINKED_REQUEST"))
                .andExpect(jsonPath("$.personalApproved").value(false)).andExpect(jsonPath("$.operationalEnabled").value(false));
        mvc.perform(get(path(legacy.getId(),"")).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").doesNotExist()).andExpect(jsonPath("$.operationalEnabled").value(false));
    }

    @Test void ambiguousHistoricalRequestsRemainBlockedWithoutChoosingOneImplicitly() throws Exception {
        // V25 uniqueness makes duplicate provisioning unrepresentable. The
        // expected PostgreSQL rejection is evidence; no constraint is disabled.
        UUID invitation=UUID.randomUUID();
        jdbc.update("""
            insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at,used_at)
            select ?,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at,used_at
            from organization_invitation where id=(select invitation_id from conductor_onboarding_request where id=?)
            """,invitation,requestId);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("""
            insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,status,review_rounds,
                submitted_at,reviewed_by_admin_id,reviewed_at,personal_approved_at,linked_user_id,linked_driver_id,provisioned_at,modality)
            select ?,identity_id,organization_id,?,?,'APPROVED',review_rounds,submitted_at,reviewed_by_admin_id,reviewed_at,
                personal_approved_at,linked_user_id,linked_driver_id,provisioned_at,modality from conductor_onboarding_request where id=?
            """,UUID.randomUUID(),invitation,"SYNTHETIC-AMBIGUOUS-"+UUID.randomUUID(),requestId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_onboarding_provisioned_identity");
    }

    @Test void administrativeReservationCannotReplaceConductorAcceptanceEvenWithInvalidDocuments() throws Exception {
        JsonNode service=reviewedService(false);seedDocument(personalDocuments(initial.plusSeconds(1800)).get(1),2);
        mvc.perform(post("/api/v1/assignments/{id}/reserve",service.path("id").asText()).header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",service.path("version").asLong()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("aceptación expresa")));
        assertThat(jdbc.queryForObject("select reserved_at is null from assignment where id=?",Boolean.class,UUID.fromString(service.path("id").asText()))).isTrue();
    }

    @Test void administrativeStartCannotReplaceConductorEvenAfterAnAuthenticAcceptance() throws Exception {
        JsonNode service=reviewedService(false);
        service=json(mvc.perform(post("/api/v1/mobile/assignments/{id}/accept",service.path("id").asText()).header("Authorization","Bearer "+jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",service.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",initial)))).andExpect(status().isOk()));
        seedDocument(personalDocuments(initial.plusSeconds(1800)).get(1),2);
        mvc.perform(post("/api/v1/assignments/{id}/start",service.path("id").asText()).header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",service.path("version").asLong()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no puede iniciar")));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(service.path("id").asText()))).isEqualTo("SCHEDULED");
    }

    @Test void mobileStartRechecksTheWholeWindowAfterAnAuthenticAcceptance() throws Exception {
        JsonNode service=reviewedService(true);String token=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        service=json(mvc.perform(post("/api/v1/mobile/assignments/{id}/accept",service.path("id").asText()).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",service.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",initial)))).andExpect(status().isOk()));
        seedDocument(personalDocuments(initial.plusSeconds(1800)).get(1),2);
        mvc.perform(post("/api/v1/mobile/assignments/{id}/start",service.path("id").asText()).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",service.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",initial))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CON-02_VALIDITY_REQUIRED")));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(service.path("id").asText()))).isEqualTo("SCHEDULED");
    }

    private JsonNode reviewedService(boolean mobile) throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(1),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/mobile/availability").header("Authorization","Bearer "+jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}")).andExpect(status().isOk());entities.flush();entities.clear();
        var command=new LinkedHashMap<String,Object>();command.put("driverId",driver.getId());command.put("vehicleId",vehicle.getId());
        command.put("routeVersionId",routeFixture());command.put("serviceTypeVersionId",serviceTypeFixture());
        command.put("originText","SYNTHETIC origin");command.put("destinationText","SYNTHETIC destination");
        command.put("scheduledAt",initial.plusSeconds(7200));command.put("scheduledEndAt",initial.plusSeconds(10800));
        command.put("responseMode","MOBILE_CONFIRMATION");if(mobile)command.put("responseDeadlineAt",initial.plusSeconds(3600));
        return json(mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andExpect(status().isCreated()));
    }

    @Test void revokingAFuturePeriodDoesNotDetachTheSameUnitFromTheCurrentService() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"COMPANY",initial.minusSeconds(3600),initial.plusSeconds(3600))).andExpect(status().isOk());
        JsonNode future=json(mutate("/periods",period(3,vehicle.getId(),"COMPANY",initial.plusSeconds(3600),initial.plusSeconds(7200))).andExpect(status().isOk()));
        mutate("/activate",basic(4)).andExpect(status().isOk());UUID ongoing=assignment("EN_SERVICIO",initial.minusSeconds(1800),initial.plusSeconds(1800));
        UUID planned=assignment("SCHEDULED",initial.plusSeconds(4200),initial.plusSeconds(5400));
        jdbc.update("update driver set availability_status='EN_SERVICIO' where id=?",driver.getId());jdbc.update("update vehicle set status='EN_SERVICIO' where id=?",vehicle.getId());
        mutate("/periods/"+future.path("periods").get(1).path("id").asText()+"/revoke",basic(5)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,ongoing)).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select status from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,planned)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from incident where assignment_id=?",Integer.class,ongoing)).isZero();
    }

    private AppUser account(String address,UserRole role,String name) {
        AppUser account=AppUser.organizationUser(organization,address+"@rutafija.test",passwords.encode("Synthetic-L02-Password-2026"),name,role);
        account.verifyEmail(initial);return users.saveAndFlush(account);
    }
    private void bindSyntheticIdentity(AppUser target,String document) {
        jdbc.update("""
                insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at)
                values(?,?,'DNI',?,'IN_PERSON','SYNTHETIC QA historical fixture',?)
                """,hasher.hashOrigin("conductor-person:DNI:"+document),target.getId(),admin.getId(),Timestamp.from(initial));
    }
    protected List<Map<String,Object>> personalDocuments(Instant expiry) {
        List<Map<String,Object>> items=new ArrayList<>();
        items.add(document("CON-01",new LinkedHashMap<>(Map.of("documentType","DNI","documentNumber",dni,"fullName",syntheticName,"issuer","SYNTHETIC")),"NO_EXPIRY",null,"CONFORME"));
        items.add(document("CON-02",new LinkedHashMap<>(Map.of("licenseNumber","SYNTHETIC-LICENSE","licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC")),"DATED",expiry,"CONFORME"));
        for(String id:List.of("CON-03","CON-04","CON-05"))items.add(document(id,new LinkedHashMap<>(),"NO_EXPIRY",null,"NO_APLICA_JUSTIFICADO"));
        return items;
    }
    private Map<String,Object> document(String id,Map<String,String> fields,String expiry,Instant until,String result) {
        Map<String,Object> item=new LinkedHashMap<>();item.put("requirementId",id);item.put("fields",fields);
        item.put("expiryMode",expiry);item.put("validUntil",until);item.put("applicability","CONFIRMED");
        item.put("condition","SYNTHETIC explicit applicability review");item.put("sourceReference","SYNTHETIC reference only");
        item.put("result",result);item.put("reviewReference","SYNTHETIC visual review");
        item.put("observation","SYNTHETIC explicit applicability justification");return item;
    }
    protected void seedDocument(Map<String,Object> item,int revision)throws Exception {
        jdbc.update("""
                insert into conductor_document_revision(id,request_id,requirement_id,revision_no,fields,expiry_mode,valid_until,
                    applicability,applicability_condition,source_reference,result,review_reference,observation,reviewer_id,actor_kind,recorded_at,
                    catalog_id,catalog_version,catalog_hash)
                values(?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,'ADMIN',?,?,?,?)
                """,UUID.randomUUID(),requestId,item.get("requirementId"),revision,mapper.writeValueAsString(item.get("fields")),item.get("expiryMode"),
                item.get("validUntil") instanceof Instant until?Timestamp.from(until):null,item.get("applicability"),item.get("condition"),
                item.get("sourceReference"),item.get("result"),item.get("reviewReference"),item.get("observation"),admin.getId(),Timestamp.from(initial),
                ConductorDocumentaryCatalog.ID,ConductorDocumentaryCatalog.VERSION,ConductorDocumentaryCatalog.HASH);
        jdbc.update("update conductor_onboarding_request set version=greatest(version,?) where id=?",revision,requestId);
    }
    protected Map<String,Object> basic(long version) {
        Map<String,Object> command=new LinkedHashMap<>();command.put("expectedVersion",version);
        command.put("operationKey","synthetic-"+UUID.randomUUID());command.put("reason","SYNTHETIC contract verification");return command;
    }
    protected void declareAvailable()throws Exception {
        mvc.perform(put("/api/v1/mobile/availability").header("Authorization","Bearer "+jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}")).andExpect(status().isOk());
        entities.flush();entities.clear();
    }
    /** Explicit synthetic-only prerequisite for start regressions; never grants consent for a real user. */
    protected void syntheticTrackingConsent()throws Exception {
        mvc.perform(put("/api/v1/mobile/location/consent").header("Authorization","Bearer "+mobileToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}")).andExpect(status().isOk());
        entities.flush();entities.clear();
    }
    protected UUID routeFixture()throws Exception {
        var stops=List.of(Map.of("key","start","name","SYNTHETIC origin","kind","START"),Map.of("key","middle","name","SYNTHETIC middle","kind","INTERMEDIATE"),Map.of("key","end","name","SYNTHETIC destination","kind","END"));
        var c=Map.of("name","SYNTHETIC route "+UUID.randomUUID().toString().replaceAll("[0-9]","x"),"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-TEST","stops",stops);
        var v=json(mvc.perform(post("/api/v1/routes").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c))).andExpect(status().isOk()));
        return UUID.fromString(v.path("currentVersionId").asText());
    }
    private UUID syntheticZeroMarginVersion;
    /** Explicit synthetic-only configuration through the normal ADMIN API, never a runtime default. */
    protected UUID serviceTypeFixture()throws Exception {
        if(syntheticZeroMarginVersion==null) {
            var c=Map.of("name","SYNTHETIC ZERO MARGIN "+UUID.randomUUID().toString().replaceAll("[0-9]","x"),
                "beforeMinutes",0,"afterMinutes",0,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-REGRESSION-CONFIG");
            syntheticZeroMarginVersion=UUID.fromString(json(mvc.perform(post("/api/v1/service-types").header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c))).andExpect(status().isOk())).path("currentVersionId").asText());
        }
        return syntheticZeroMarginVersion;
    }
    protected UUID assignment(String state,Instant start,Instant end) {
        UUID id=UUID.randomUUID();boolean pending="PENDING_RESPONSE".equals(state),inService="EN_SERVICIO".equals(state);
        jdbc.update("""
                insert into assignment(id,organization_id,driver_id,vehicle_id,created_by,status,origin_text,destination_text,
                   scheduled_at,scheduled_end_at,response_mode,response_deadline_at,reserved_at,started_at,created_at,updated_at)
                values(?,?,?,?,?,?,'SYNTHETIC origin','SYNTHETIC destination',?,?,?,?,?,?,?,?)
                """,id,organization.getId(),driver.getId(),vehicle.getId(),admin.getId(),state,Timestamp.from(start),Timestamp.from(end),
                pending?"MOBILE_CONFIRMATION":"ADMIN_DIRECT",pending?Timestamp.from(start.minusSeconds(60)):null,
                inService?Timestamp.from(start):null,inService?Timestamp.from(start):null,Timestamp.from(initial),Timestamp.from(initial));
        return id;
    }
    protected Map<String,Object> reviews(String scope,UUID unit,long version,boolean conforming) {
        Map<String,Object> command=basic(version);command.put("scope",scope);command.put("vehicleId",unit);
        List<Map<String,Object>> items=new ArrayList<>();
        for(var requirement:QualificationCatalog.REQUIREMENTS)if(scope.equals(requirement.scope())) {
            Map<String,String> fields=new LinkedHashMap<>();
            for(String key:requirement.fieldKeys())fields.put(key,switch(key){
                case "plate"->vehicle.getPlate();case "category"->"M1";case "bodyType"->"SEDAN";
                case "brand"->vehicle.getBrand();case "model"->vehicle.getModel();case "year"->"2025";case "insuranceType"->"SOAT";case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"SYNTHETIC CHICLAYO";
                default->"SYNTHETIC reference";});
            Map<String,Object> item=document(requirement.id(),fields,"DATED",initial.plus(Duration.ofDays(30)),conforming?"CONFORME":"POR_CONTRASTAR");
            if("EMP-03".equals(requirement.id()))item.put("result","NO_APLICA_JUSTIFICADO");
            item.remove("observation");items.add(item);
        }
        command.put("documents",items);return command;
    }
    protected Map<String,Object> period(long version,UUID unit,String kind,Instant start,Instant end) {
        var command=basic(version);command.put("vehicleId",unit);command.put("kind",kind);command.put("startAt",start);command.put("endAt",end);return command;
    }
    protected void reviewAll()throws Exception {
        mutate("/reviews",reviews("VEHICLE",vehicle.getId(),0,true)).andExpect(status().isOk());
        mutate("/reviews",reviews("COMPANY",null,1,true)).andExpect(status().isOk());
    }
    protected record AdditionalParticipant(AppUser account,Driver driver,Vehicle vehicle,String mobileToken){}
    /** Additional synthetic historical fixture, entirely inside the existing rollback transaction. */
    protected AdditionalParticipant prepareAdditionalSyntheticConductor()throws Exception {
        var previousDriver=driver;var previousVehicle=vehicle;var previousConductor=conductor;
        var previousRequest=requestId;var previousDni=dni;
        try {
            String suffix=UUID.randomUUID().toString().substring(0,8);dni="6"+previousDni.substring(1);
            conductor=account("synthetic.additional."+suffix,UserRole.CONDUCTOR,syntheticName);
            driver=drivers.saveAndFlush(Driver.register(organization,conductor,previousDriver.getGroup(),syntheticName,null,"DNI",dni,"SYNTHETIC-LICENSE"));
            driver.deactivate();drivers.flush();bindSyntheticIdentity(conductor,dni);
            vehicle=vehicles.saveAndFlush(Vehicle.register(organization,"ADD-"+suffix.substring(0,6),"SYNTHETIC","SYNTHETIC",(short)2025,null));
            requestId=ApprovedConductorFixture.recordHistoricalApproval(jdbc,admin,conductor,driver);
            jdbc.update("update conductor_onboarding_request set modality='TAXI' where id=?",requestId);
            for(var item:personalDocuments(initial.plus(Duration.ofDays(30))))seedDocument(item,1);
            reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
            mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();
            return new AdditionalParticipant(conductor,driver,vehicle,jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS"));
        } finally {driver=previousDriver;vehicle=previousVehicle;conductor=previousConductor;requestId=previousRequest;dni=previousDni;}
    }
    private ResultActions read()throws Exception {return mvc.perform(get(path(driver.getId(),"")).header("Authorization","Bearer "+adminToken));}
    protected ResultActions mutate(String suffix,Object command)throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(post(path(driver.getId(),suffix)).header("Authorization","Bearer "+adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)));
    }
    private String path(UUID id,String suffix){return "/api/v1/drivers/"+id+"/qualification"+suffix;}
    protected JsonNode json(ResultActions action)throws Exception {return mapper.readTree(action.andReturn().getResponse().getContentAsByteArray());}

    @TestConfiguration static class ClockConfiguration {
        @Bean @Primary QualificationClock qualificationClock(){return new QualificationClock();}
    }
    static final class QualificationClock extends Clock {
        private volatile Instant value=Instant.now();
        void set(Instant next){value=next;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return value;}
    }
}
