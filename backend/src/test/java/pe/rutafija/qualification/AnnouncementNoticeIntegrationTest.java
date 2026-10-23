package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.rutafija.operation.application.*;
import pe.rutafija.operation.domain.*;
import pe.rutafija.operation.infrastructure.AnnouncementRepository;
import pe.rutafija.fleet.domain.TransportGroup;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP filters/controllers, services and real PostgreSQL; every fixture rolls back. */
class AnnouncementNoticeIntegrationTest extends QualificationIntegrationTest {
    @Autowired OperationEventPublisher events;
    @Autowired AnnouncementRepository announcements;
    @MockitoSpyBean ExternalNoticeChannel channel;
    private Map<String,Object> publication(UUID group){var b=new LinkedHashMap<String,Object>();b.put("title","SYNTHETIC notice");b.put("body","SYNTHETIC operational information without personal data");b.put("audienceType",group==null?"ORGANIZATION":"GROUP");if(group!=null)b.put("audienceId",group);b.put("requireReadAck",true);b.put("operationKey",UUID.randomUUID());return b;}
    private JsonNode publish(UUID group)throws Exception{return json(mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(publication(group)))).andExpect(status().isCreated()));}
    private JsonNode own(UUID id)throws Exception{return json(mvc.perform(get("/api/v1/mobile/announcements/"+id).header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()));}
    private JsonNode attention(UUID id)throws Exception{return json(mvc.perform(get("/api/v1/announcements/"+id+"/attention").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));}
    private UUID id(JsonNode r){return UUID.fromString(r.path("id").asText());}
    private UUID secondary()throws Exception{var group=groups.saveAndFlush(TransportGroup.active(organization,"SYNTHETIC secondary group "+UUID.randomUUID(),null));membership(group.getId(),true);return group.getId();}
    private void membership(UUID group,boolean active)throws Exception{
        var r=json(mvc.perform(get("/api/v1/groups/"+group+"/resources").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));
        mvc.perform(post("/api/v1/groups/"+group+"/memberships").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(Map.of("expectedVersion",r.path("version").asLong(),"operationKey",UUID.randomUUID().toString(),"resourceKind","DRIVER","resourceId",driver.getId(),"active",active,"reason","SYNTHETIC membership review")))).andExpect(status().isOk());
    }
    @Test void announcementOrganizationSnapshotIncludesLimitedApprovedAccountButNoAutomaticReading()throws Exception{
        var a=publish(null);var view=own(id(a));assertThat(view.path("readAt").isNull()||view.path("readAt").isMissingNode()).isTrue();assertThat(view.path("deliveredAt").asText()).isNotBlank();
        assertThat(attention(id(a)).path("pending").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select provenance from announcement_recipient where announcement_id=? and user_id=?",String.class,id(a),conductor.getId())).isEqualTo("PUBLISH_SNAPSHOT");
    }
    @Test void announcementSecondaryMembershipIsCapturedAndPreviousReceptionSurvivesLeavingGroup()throws Exception{
        UUID group=secondary();var a=publish(group);own(id(a));membership(group,false);own(id(a));
        var after=publish(group);mvc.perform(get("/api/v1/mobile/announcements/"+id(after)).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        var listed=json(mvc.perform(get("/api/v1/mobile/announcements").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()));
        assertThat(listed.path("items").toString()).contains(id(a).toString()).doesNotContain(id(after).toString());
    }
    @Test void announcementJoiningAfterPublicationDoesNotInventPriorReception()throws Exception{
        var group=groups.saveAndFlush(TransportGroup.active(organization,"SYNTHETIC later group "+UUID.randomUUID(),null));var old=publish(group.getId());membership(group.getId(),true);
        mvc.perform(get("/api/v1/mobile/announcements/"+id(old)).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());own(id(publish(group.getId())));
    }
    @Test void announcementExplicitReadingIsIdempotentAndDoesNotChangeOffers()throws Exception{
        var a=publish(null);long before=jdbc.queryForObject("select coalesce(sum(version),0) from assignment",Long.class);
        var read=json(mvc.perform(post("/api/v1/mobile/announcements/"+id(a)+"/read").header("Authorization","Bearer "+mobileToken).contentType("application/json").content("{}")).andExpect(status().isOk()));
        var again=json(mvc.perform(post("/api/v1/mobile/announcements/"+id(a)+"/read").header("Authorization","Bearer "+mobileToken).contentType("application/json").content("{}")).andExpect(status().isOk()));
        assertThat(read.path("readAt").asText()).isNotBlank().isEqualTo(again.path("readAt").asText());assertThat(attention(id(a)).path("pending").asInt()).isZero();
        assertThat(jdbc.queryForObject("select coalesce(sum(version),0) from assignment",Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from announcement_receipt where announcement_id=?",Integer.class,id(a))).isEqualTo(1);
    }
    @Test void announcementSecurityWithdrawalIsVersionedAuditedAndDoesNotEraseReceipts()throws Exception{
        var a=publish(null);own(id(a));var c=Map.of("operationKey",UUID.randomUUID(),"expectedVersion",0,"reason","SYNTHETIC restricted withdrawal");String path="/api/v1/announcements/"+id(a)+"/recipients/"+conductor.getId()+"/withdraw";
        mvc.perform(post(path).header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(c))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].accessStatus").value("WITHDRAWN"));
        mvc.perform(post(path).header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(c))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mobile/announcements/"+id(a)).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/mobile/announcements/"+id(a)+"/read").header("Authorization","Bearer "+mobileToken).contentType("application/json").content("{}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from announcement_receipt where announcement_id=?",Integer.class,id(a))).isEqualTo(1);
        assertThat(attention(id(a)).path("pending").asInt()).isZero();
        var inbox=json(mvc.perform(get("/api/v1/mobile/notices").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()));assertThat(inbox.path("items").toString()).doesNotContain(id(a).toString());
    }
    @Test void announcementPublicationKeyReplaysExactlyAndRejectsChangedContent()throws Exception{
        var b=publication(null);var original=json(mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(b))).andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(b))).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id(original).toString()));
        mvc.perform(get("/api/v1/announcements/operations/"+b.get("operationKey")).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.announcementId").value(id(original).toString()));
        b.put("body","SYNTHETIC changed body");mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(b))).andExpect(status().isConflict());
    }
    @Test void announcementTenantRolesAndPersonalDataAreEnforcedByTheActualApi()throws Exception{
        var a=publish(null);var outsider=users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();
        mvc.perform(get("/api/v1/announcements/"+id(a)+"/attention").header("Authorization","Bearer "+jwt.issueAccessToken(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/"+id(a)+"/attention").header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+mobileToken).contentType("application/json").content(mapper.writeValueAsBytes(publication(null)))).andExpect(status().isForbidden());
        var b=publication(null);b.put("body","person@example.test");mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(b))).andExpect(status().isBadRequest());
        b=publication(otherDriver.getGroup().getId());mvc.perform(post("/api/v1/announcements").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(b))).andExpect(status().isNotFound());
        assertThat(attention(id(a)).toString()).doesNotContain(dni,conductor.getEmail(),conductor.getFullName());
    }
    @Test void announcementLegacyWithoutProvenReceptionIsNotGrantedByCurrentGroup()throws Exception{
        var legacy=announcements.saveAndFlush(Announcement.publish(organization,admin,"SYNTHETIC legacy","SYNTHETIC unproven receipt",AnnouncementAudienceType.ORGANIZATION,null,true));
        mvc.perform(get("/api/v1/mobile/announcements/"+legacy.getId()).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
    }
    @Test void noticeDurableInboxDoesNotReadAnnouncementAndShowsPendingToAdmin()throws Exception{
        var a=publish(null);var inbox=json(mvc.perform(get("/api/v1/mobile/notices").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()));
        assertThat(inbox.path("items")).hasSize(1);var n=inbox.path("items").get(0);assertThat(n.path("eventType").asText()).isEqualTo("announcement.published");
        var r=json(mvc.perform(post("/api/v1/mobile/notices/"+n.path("id").asText()+"/read").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()));
        mvc.perform(post("/api/v1/mobile/notices/"+n.path("id").asText()+"/read").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.readAt").value(r.path("readAt").asText()));
        assertThat(attention(id(a)).path("pending").asInt()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from announcement_receipt where announcement_id=?",Integer.class,id(a))).isZero();
        var adminInbox=json(mvc.perform(get("/api/v1/notices").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));assertThat(adminInbox.path("items").toString()).contains("announcement.read.pending");
    }
    @Test void noticeProviderNotConfiguredIsVisibleWithoutExternalDeliveryOrFalseSuccess()throws Exception{
        events.publish(organization.getId(),null,"assignment.pending-response",Map.of("driverId",driver.getId(),"email","NOT_ALLOWED","latitude",-6.7,"longitude",-79.8));
        var failures=json(mvc.perform(get("/api/v1/notices?failures=true").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));
        assertThat(failures.path("items")).hasSize(2);assertThat(failures.toString()).contains("NOT_CONFIGURED","PROVIDER_AND_DEVICE_NOT_PREPARED").doesNotContain("latitude","longitude","NOT_ALLOWED","PROVIDER_ACCEPTED");
        verify(channel).availability();verifyNoInteractions(mail);
    }
    @Test void noticeChannelFailureRemainsVisibleAndDoesNotPretendDeviceDelivery()throws Exception{
        doThrow(new IllegalStateException("SYNTHETIC failure")).when(channel).availability();
        events.publish(organization.getId(),null,"driver.suspended",Map.of("driverId",driver.getId()));
        var failures=json(mvc.perform(get("/api/v1/notices?failures=true").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));
        assertThat(failures.toString()).contains("FAILED","CHANNEL_AVAILABILITY_CHECK_FAILED").doesNotContain("SYNTHETIC failure","PROVIDER_ACCEPTED");
    }
    @Test void noticeLocationTelemetryIsExcludedAndForeignOwnReadIsRejected()throws Exception{
        events.publish(organization.getId(),null,"driver.location.updated",Map.of("driverId",driver.getId(),"latitude",-6.7));
        assertThat(jdbc.queryForObject("select count(*) from operation_notice where organization_id=?",Integer.class,organization.getId())).isZero();
        publish(null);UUID adminNotice=jdbc.queryForObject("select id from operation_notice where organization_id=? and user_id=? and event_type='announcement.published'",UUID.class,organization.getId(),admin.getId());
        mvc.perform(post("/api/v1/mobile/notices/"+adminNotice+"/read").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notices?failures=true").header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
    }
    @Test void noticeCriticalActualHttpReportUsesExplicitSeverityWithoutInterruptingService()throws Exception{
        mvc.perform(post("/api/v1/incidents").header("Authorization","Bearer "+adminToken).contentType("application/json").content(mapper.writeValueAsBytes(Map.of("operationKey",UUID.randomUUID(),"driverId",driver.getId(),"category","FALLO_PLATAFORMA","severity","CRITICAL","description","SYNTHETIC explicitly evaluated critical report")))).andExpect(status().isCreated());
        var failure=json(mvc.perform(get("/api/v1/notices?failures=true").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));assertThat(failure.path("items")).hasSize(2);assertThat(failure.toString()).contains("incident.reported","NOT_CONFIGURED");
        assertThat(jdbc.queryForObject("select count(*) from service_attention where driver_id=?",Integer.class,driver.getId())).isZero();verifyNoInteractions(mail);
    }
    @Test void announcementDatabaseProtectsRecipientsPublicationAndFirstReadTimestamps()throws Exception{
        var a=publish(null);own(id(a));
        assertThat(deniedSql("update announcement set body='SYNTHETIC forbidden rewrite' where id=?",id(a))).contains("23514");
        assertThat(deniedSql("update announcement_recipient set provenance='LEGACY_RECEIPT' where announcement_id=?",id(a))).contains("23514");
        assertThat(deniedSql("delete from announcement_recipient where announcement_id=?",id(a))).contains("23514");
        assertThat(deniedSql("update announcement_receipt set delivered_at=delivered_at-interval '1 minute' where announcement_id=?",id(a))).contains("23514");
        assertThat(deniedSql("delete from announcement_receipt where announcement_id=?",id(a))).contains("23514");
    }
    private String deniedSql(String sql,Object parameter)throws Exception{
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource());var savepoint=connection.setSavepoint();
        try{jdbc.update(sql,parameter);return "NOT_REJECTED";}catch(Exception failure){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();return cause instanceof java.sql.SQLException s?s.getSQLState():cause.getClass().getSimpleName();}
        finally{connection.rollback(savepoint);connection.releaseSavepoint(savepoint);entities.clear();}
    }
}
