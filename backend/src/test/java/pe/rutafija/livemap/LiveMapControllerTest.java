package pe.rutafija.livemap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.livemap.api.LiveMapController;
import pe.rutafija.livemap.application.LiveMapService;
import pe.rutafija.livemap.infrastructure.LiveMapReadDao;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.shared.exception.GlobalExceptionHandler;
import pe.rutafija.shared.security.CurrentUserService;
import java.time.Clock;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** MVC binding/advice only; real bearer authorization is exercised by the isolated PostgreSQL suite. */
class LiveMapControllerTest {
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LiveMapReadDao dao = mock(LiveMapReadDao.class);
    private MockMvc mvc;
    @BeforeEach void setup() {
        var actor=AppUser.organizationUser(Organization.active("Synthetic map",null,"America/Lima"),
                "map@example.invalid","synthetic-hash","Synthetic admin",UserRole.ADMIN);
        when(current.requireTenantActor()).thenReturn(actor);
        when(dao.vehicles(any(),anyInt(),any(),anyString(),any())).thenReturn(List.of());
        when(dao.groups(any())).thenReturn(List.of());
        mvc=standaloneSetup(new LiveMapController(new LiveMapService(current,dao,Clock.systemUTC())))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void defaultFeedAndGroupsAreNoStoreAndHaveNoHistoricalPayload()throws Exception {
        mvc.perform(get("/api/v1/live-map/vehicles")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.limit").value(5)).andExpect(jsonPath("$.serverTime").isNotEmpty()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.history").doesNotExist()).andExpect(jsonPath("$.organizationId").doesNotExist());
        mvc.perform(get("/api/v1/live-map/groups")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void invalidFiltersAre400RatherThanAnUnboundedQuery()throws Exception {
        mvc.perform(get("/api/v1/live-map/vehicles").param("limit","6")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/live-map/vehicles").param("driverName","a".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/live-map/vehicles").param("groupId","not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(dao);
    }
}
