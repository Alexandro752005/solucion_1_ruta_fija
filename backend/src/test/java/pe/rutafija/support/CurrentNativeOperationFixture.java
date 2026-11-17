package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;
import pe.rutafija.qualification.application.QualificationCatalog;
import pe.rutafija.qualification.application.OperationalQualificationPolicy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Explicit synthetic historical identity/document prerequisites plus the real
 * current qualification/catalog APIs. Never packaged in the application.
 * Onboarding/identity approval itself is exercised by its own lifecycle suite.
 */
public final class CurrentNativeOperationFixture {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final ApplicationContext context;
    private final Map<UUID, JsonNode> catalogs = new HashMap<>();
    private final Map<UUID, UUID> qualifiedUnits = new HashMap<>();

    public CurrentNativeOperationFixture(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc, ApplicationContext context) {
        this.mvc=mvc;this.json=json;this.jdbc=jdbc;this.context=context;
        if (!Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("test"))
            throw new IllegalStateException("CURRENT_NATIVE_FIXTURE_REQUIRES_TEST_PROFILE");
        // Reuse the Native harness's configured-target and role guards. Its
        // before/after hooks retain the existing human-trial cleanup protection.
        NativePostgresTestDatabase.load().assertTestRolePermissions();
        var isolated=IsolatedAuditPostgresTarget.fromEnvironment(System.getenv());
        var target=jdbc.queryForMap("select current_database() db,current_user principal,inet_server_port() port");
        if(!"ruta_fija_test".equals(target.get("db")) || !"rf_test".equals(target.get("principal"))
            || ((Number)target.get("port")).intValue()!=(isolated.isPresent()?55441:5432))
            throw new IllegalStateException("CURRENT_NATIVE_FIXTURE_REQUIRES_EXACT_NATIVE_TEST_TARGET");
        if(isolated.isPresent())jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
            isolated.orElseThrow().verify(connection,"rf_test");return null;
        });
    }

    public static RequestPostProcessor admin(AppUser actor) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
            .jwt(token->token.subject(actor.getId().toString()).claim("organizationId",actor.getOrganizationId().toString())
                .claim("email",actor.getEmail()).claim("role","ADMIN").claim("sessionVersion",actor.getSessionVersion()));
    }

    public RequestPostProcessor mobile(AppUser actor, Driver driver) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CONDUCTOR"))
            .jwt(token->token.subject(actor.getId().toString()).claim("organizationId",actor.getOrganizationId().toString())
                .claim("email",actor.getEmail()).claim("role","CONDUCTOR").claim("sessionVersion",actor.getSessionVersion())
                .claim("sessionChannel","MOBILE").claim("accessScope","OPERATIONS").claim("driverId",driver.getId().toString()));
    }

    public void qualify(AppUser reviewer, AppUser account, Driver driver, Vehicle vehicle) throws Exception {
        if(!account.getEmail().endsWith("@rutafija.test") || !reviewer.getEmail().endsWith("@rutafija.test")
            || !Objects.equals(account.getOrganizationId(),reviewer.getOrganizationId())
            || !Objects.equals(driver.getUser().getId(),account.getId())
            || !Objects.equals(vehicle.getOrganizationId(),account.getOrganizationId()))
            throw new IllegalArgumentException("SYNTHETIC_SAME_TENANT_LINKED_FIXTURE_REQUIRED");
        if (qualifiedUnits.containsKey(driver.getId())) {
            if (!vehicle.getId().equals(qualifiedUnits.get(driver.getId())))
                throw new IllegalArgumentException("FIXTURE_UNIT_ALREADY_QUALIFIED");
            assertThat(context.getBean(OperationalQualificationPolicy.class).eligible(driver.getId(),driver.getOrganizationId(),true)).isTrue();
            return;
        }
        Instant now=Instant.now(context.getBean(Clock.class)), expiry=now.plus(Duration.ofDays(30));
        var hasher=context.getBean(InvitationCodeHasher.class);
        jdbc.update("""
            insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at)
            values(?,?,'DNI',?,'IN_PERSON','SYNTHETIC historical regression fixture',?) on conflict(user_id) do nothing
            """,hasher.hashOrigin("conductor-person:DNI:"+driver.getDocumentNumber()),account.getId(),reviewer.getId(),Timestamp.from(now));
        UUID request=ApprovedConductorFixture.recordHistoricalApproval(jdbc,reviewer,account,driver);
        jdbc.update("update conductor_onboarding_request set modality='TAXI' where id=?",request);
        for(var item:ConductorDocumentaryCatalog.REQUIREMENTS) {
            String id=item.requirementId();boolean applicable=id.equals("CON-01")||id.equals("CON-02");
            Map<String,String> fields=id.equals("CON-01")?Map.of("documentType","DNI","documentNumber",driver.getDocumentNumber(),"fullName",driver.getFullName(),"issuer","SYNTHETIC")
                :id.equals("CON-02")?Map.of("licenseNumber","SYNTHETIC-LICENSE","licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"):Map.of();
            jdbc.update("""
                insert into conductor_document_revision(id,request_id,requirement_id,revision_no,fields,expiry_mode,valid_until,
                    applicability,applicability_condition,source_reference,result,review_reference,observation,reviewer_id,actor_kind,recorded_at,
                    catalog_id,catalog_version,catalog_hash)
                values(?,?,?,1,?::jsonb,?,?,'CONFIRMED','SYNTHETIC applicability','SYNTHETIC source',?,
                    'SYNTHETIC reviewed fixture','SYNTHETIC explicit historical justification',?,'ADMIN',?,?,?,?)
                """,UUID.randomUUID(),request,id,json.writeValueAsString(fields),id.equals("CON-02")?"DATED":"NO_EXPIRY",
                id.equals("CON-02")?Timestamp.from(expiry):null,applicable?"CONFORME":"NO_APLICA_JUSTIFICADO",
                reviewer.getId(),Timestamp.from(now),ConductorDocumentaryCatalog.ID,ConductorDocumentaryCatalog.VERSION,ConductorDocumentaryCatalog.HASH);
        }
        long version=readQualification(reviewer,driver).path("version").asLong();
        for(String scope:List.of("VEHICLE","COMPANY")) {
            List<Map<String,Object>> docs=new ArrayList<>();
            for(var requirement:QualificationCatalog.REQUIREMENTS) if(scope.equals(requirement.scope())) {
                Map<String,String> fields=new LinkedHashMap<>();
                for(String key:requirement.fieldKeys()) fields.put(key,switch(key) {
                    case "plate"->vehicle.getPlate();case "brand"->Objects.toString(vehicle.getBrand(),"SYNTHETIC");
                    case "model"->Objects.toString(vehicle.getModel(),"SYNTHETIC");case "year"->Objects.toString(vehicle.getYear(),"2025");
                    case "category"->"M1";case "bodyType"->"SEDAN";case "insuranceType"->"SOAT";
                    case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"CHICLAYO";default->"SYNTHETIC reference";
                });
                Map<String,Object> doc=new LinkedHashMap<>();doc.put("requirementId",requirement.id());doc.put("fields",fields);
                doc.put("expiryMode","DATED");doc.put("validUntil",expiry);doc.put("applicability","CONFIRMED");
                doc.put("condition","SYNTHETIC applicability");doc.put("sourceReference","SYNTHETIC source");
                doc.put("reviewReference","SYNTHETIC reviewed fixture");doc.put("result",requirement.id().equals("EMP-03")?"NO_APLICA_JUSTIFICADO":"CONFORME");docs.add(doc);
            }
            var body=basic(version);body.put("scope",scope);body.put("vehicleId",scope.equals("VEHICLE")?vehicle.getId():null);body.put("documents",docs);
            version=qualificationPost(reviewer,driver,"/reviews",body).path("version").asLong();
        }
        var period=basic(version);period.put("vehicleId",vehicle.getId());period.put("kind","OWN");period.put("startAt",now.minusSeconds(60));
        version=qualificationPost(reviewer,driver,"/periods",period).path("version").asLong();
        qualificationPost(reviewer,driver,"/activate",basic(version));
        assertThat(context.getBean(OperationalQualificationPolicy.class).eligible(driver.getId(),driver.getOrganizationId(),true))
            .as("Real current qualification must pass; a scope claim alone never grants eligibility").isTrue();
        mvc.perform(put("/api/v1/mobile/availability").with(mobile(account,driver))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}"))
            .andExpect(status().isOk());
        qualifiedUnits.put(driver.getId(),vehicle.getId());
    }

    private JsonNode readQualification(AppUser actor,Driver driver) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/drivers/"+driver.getId()+"/qualification").with(admin(actor)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode qualificationPost(AppUser actor,Driver driver,String suffix,Object body) throws Exception {
        return post(actor,"/api/v1/drivers/"+driver.getId()+"/qualification"+suffix,body,200);
    }
    private static Map<String,Object> basic(long version) {
        var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",version);body.put("operationKey","synthetic-"+UUID.randomUUID());body.put("reason","SYNTHETIC current regression fixture");return body;
    }
    public JsonNode post(AppUser actor,String path,Object body,int expected) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(admin(actor)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    public Map<String,Object> offerFields(AppUser actor) throws Exception {
        JsonNode config=catalogs.get(actor.getOrganizationId());
        if(config==null) {
            String suffix=UUID.randomUUID().toString().replaceAll("[0-9]","x");
            var type=post(actor,"/api/v1/service-types",Map.of("name","SYNTHETIC type "+suffix,"beforeMinutes",0,"afterMinutes",0,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC regression configuration"),200);
            var route=post(actor,"/api/v1/routes",Map.of("name","SYNTHETIC route "+suffix,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC regression configuration",
                "stops",List.of(Map.of("key","start","name","Terminal Norte","kind","START"),
                    Map.of("key","middle","name","Paradero Central","kind","INTERMEDIATE"),
                    Map.of("key","end","name","Terminal Sur","kind","END"))),200);
            var object=json.createObjectNode();object.put("serviceTypeVersionId",type.path("currentVersionId").asText());object.put("routeVersionId",route.path("currentVersionId").asText());config=object;catalogs.put(actor.getOrganizationId(),config);
        }
        return new LinkedHashMap<>(Map.of("serviceTypeVersionId",config.path("serviceTypeVersionId").asText(),"routeVersionId",config.path("routeVersionId").asText()));
    }
}
