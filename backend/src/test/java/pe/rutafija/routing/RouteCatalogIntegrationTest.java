package pe.rutafija.routing;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import pe.rutafija.support.L02PostgresIntegrationTest;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.root=ERROR","logging.level.org.springframework=ERROR","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
class RouteCatalogIntegrationTest extends L02PostgresIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired AppUserRepository users;@Autowired JwtService jwt;
    @MockitoBean OtpEmailSender mail;
    private AppUser admin;private String token;private int ledger;
    @BeforeEach void fixture(){
        assertThat(jdbc.queryForObject("select exists(select 1 from flyway_schema_history where version='30' and success)",Boolean.class)).isTrue();
        admin=users.findOneById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();token=jwt.issueAccessToken(admin);
        ledger=jdbc.queryForObject("select count(*) from pilot_account_creation",Integer.class);
    }
    @AfterEach void preserve(){assertThat(jdbc.queryForObject("select count(*) from pilot_account_creation",Integer.class)).isEqualTo(ledger);verifyNoInteractions(mail);}
    @Test void createsAnOrderedRouteAndIdempotentRetryKeepsTheSameImmutableVersion()throws Exception{
        var c=createCommand();JsonNode first=body(postJson("/api/v1/routes",c).andExpect(status().isOk()));
        JsonNode second=body(postJson("/api/v1/routes",c).andExpect(status().isOk()));
        assertThat(first.path("id").asText()).isEqualTo(second.path("id").asText());assertThat(second.path("versions").size()).isEqualTo(1);
        assertThat(first.at("/versions/0/stops/1/kind").asText()).isEqualTo("INTERMEDIATE");assertThat(first.at("/versions/0/contentSha256").asText()).matches("[a-f0-9]{64}");
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }
    @Test void editingAppendsWithoutChangingTheOldDefinitionOrItsHash()throws Exception{
        JsonNode first=body(postJson("/api/v1/routes",createCommand()).andExpect(status().isOk()));String id=first.path("id").asText();
        var c=new LinkedHashMap<>(createCommand());c.put("expectedVersion",1);c.put("name",first.path("name").asText()+" revised");
        JsonNode next=body(postJson("/api/v1/routes/"+id+"/versions",c).andExpect(status().isOk()));
        assertThat(next.path("version").asInt()).isEqualTo(2);assertThat(next.path("versions").size()).isEqualTo(2);
        assertThat(next.at("/versions/0")).isEqualTo(first.at("/versions/0"));assertThat(next.path("currentVersionId").asText()).isEqualTo(next.at("/versions/1/id").asText());
    }
    @Test void invalidStopOrderDuplicatesPartialCoordinatesAndTooFewStopsAreRejected()throws Exception{
        var c=createCommand();c.put("stops",List.of(stop("s","Start","START"),stop("e","End","END")));postJson("/api/v1/routes",c).andExpect(status().isBadRequest());
        c=createCommand();c.put("stops",List.of(stop("s","Start","START"),stop("s","Middle","INTERMEDIATE"),stop("e","End","END")));postJson("/api/v1/routes",c).andExpect(status().isConflict());
        c=createCommand();c.put("stops",List.of(stop("s","Start","START"),stop("m","Middle","END"),stop("e","End","END")));postJson("/api/v1/routes",c).andExpect(status().isConflict());
        c=createCommand();var middle=new LinkedHashMap<>(stop("m","Middle","INTERMEDIATE"));middle.put("latitude",-6.7);c.put("stops",List.of(stop("s","Start","START"),middle,stop("e","End","END")));postJson("/api/v1/routes",c).andExpect(status().isConflict());
    }
    @Test void staleVersionOrReusedKeyWithOtherContentCannotReplaceTheCurrentRoute()throws Exception{
        var c=createCommand();JsonNode first=body(postJson("/api/v1/routes",c).andExpect(status().isOk()));
        c.put("name",c.get("name")+" changed");postJson("/api/v1/routes",c).andExpect(status().isConflict());
        var revision=createCommand();revision.put("expectedVersion",9);postJson("/api/v1/routes/"+first.path("id").asText()+"/versions",revision).andExpect(status().isConflict());
    }
    @Test void retiringPreservesVersionsAndPreventsImplicitEditing()throws Exception{
        JsonNode first=body(postJson("/api/v1/routes",createCommand()).andExpect(status().isOk()));String path="/api/v1/routes/"+first.path("id").asText();
        postJson(path+"/status",Map.of("expectedVersion",1,"operationKey",UUID.randomUUID().toString(),"reason","SYNTHETIC_RETIRE","active",false)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.versions.length()").value(1));
        var c=createCommand();c.put("expectedVersion",2);postJson(path+"/versions",c).andExpect(status().isConflict());
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void foreignRouteIsHiddenAndSuperAdminCannotUseTenantCatalog()throws Exception{
        JsonNode first=body(postJson("/api/v1/routes",createCommand()).andExpect(status().isOk()));
        // A catalog head within its deferred creation transaction: no foreign user or account is fabricated.
        // Without the tenant predicate it would be returned as a View, so 404 tests the actual object boundary.
        UUID foreign=UUID.randomUUID(),foreignRoute=UUID.randomUUID();
        jdbc.update("insert into organization(id,legal_name,status,timezone,created_at,updated_at) values(?,'SYNTHETIC FOREIGN ROUTE','ACTIVE','America/Lima',now(),now())",foreign);
        jdbc.update("insert into route_definition(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,'SYNTHETIC FOREIGN ROUTE',?,now(),now())",foreignRoute,foreign,UUID.randomUUID());
        mvc.perform(get("/api/v1/routes/"+foreignRoute).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/routes/"+UUID.randomUUID()).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        String superToken=jwt.issueAccessToken(users.findOneById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow());
        mvc.perform(get("/api/v1/routes/"+first.path("id").asText()).header("Authorization","Bearer "+superToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/routes")).andExpect(status().isUnauthorized());
    }
    @Test void immutableVersionCannotBeOverwrittenEvenThroughTheTestDatabase()throws Exception{
        JsonNode first=body(postJson("/api/v1/routes",createCommand()).andExpect(status().isOk()));UUID id=UUID.fromString(first.path("currentVersionId").asText());
        jdbc.execute("SAVEPOINT route_immutable");
        assertThatThrownBy(()->jdbc.update("update route_version set name='SYNTHETIC REWRITE' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT route_immutable");
        assertThat(jdbc.queryForObject("select name from route_version where id=?",String.class,id)).isEqualTo(first.path("name").asText());
    }
    private Map<String,Object> createCommand(){return new LinkedHashMap<>(Map.of("name","Synthetic route "+UUID.randomUUID().toString().substring(0,6),"operationKey",UUID.randomUUID().toString(),"reason","SYNTHETIC_REVIEW","stops",List.of(stop("start","Synthetic start","START"),stop("middle","Synthetic middle","INTERMEDIATE"),stop("end","Synthetic end","END"))));}
    private Map<String,Object> stop(String key,String name,String kind){return Map.of("key",key,"name",name,"kind",kind);}
    private ResultActions postJson(String path,Map<String,Object> c)throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(c)));}
    private JsonNode body(ResultActions r)throws Exception{return json.readTree(r.andReturn().getResponse().getContentAsByteArray());}
}
