package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Pure synthetic payload and clock decisions. No Spring context, database, API or fixture. */
class L08ConnectedServicePreparationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROUTE = "10000000-0000-0000-0000-000000000001";
    private static final String VERSION = "10000000-0000-0000-0000-000000000002";
    private static final String PREVIOUS = "10000000-0000-0000-0000-000000000003";
    private static final String TYPE = "10000000-0000-0000-0000-000000000004";
    private static final String DRIVER = "10000000-0000-0000-0000-000000000005";
    private static final String VEHICLE = "10000000-0000-0000-0000-000000000006";
    private static final Instant NOW = Instant.parse("2026-10-22T21:00:00Z");
    private static final Instant PLANNED = NOW.plusSeconds(1800);

    @Test void buildsPayloadFromExactCurrentVersionRatherThanFirstVersionOrConstants() {
        ObjectNode view = routeView();
        view.set("versions", JSON.valueToTree(List.of(
                version(PREVIOUS, stops("SYNTHETIC old origin", "SYNTHETIC old terminal")),
                version(VERSION, stops("SYNTHETIC reviewed origin", "SYNTHETIC reviewed terminal")))));

        Map<String, Object> payload = payload(view);

        assertThat(payload).containsEntry("originText", "SYNTHETIC reviewed origin")
                .containsEntry("destinationText", "SYNTHETIC reviewed terminal")
                .containsEntry("routeVersionId", VERSION)
                .containsEntry("serviceTypeVersionId", TYPE)
                .containsEntry("driverId", DRIVER)
                .containsEntry("vehicleId", VEHICLE)
                .containsEntry("scheduledAt", PLANNED)
                .containsEntry("scheduledEndAt", PLANNED.plusSeconds(1800));
        assertThat(payload(view)).isEqualTo(payload);
    }

    @Test void rejectsAnotherCatalogEvenIfItsVersionIdentifierMatches() {
        ObjectNode view = routeView();
        view.put("id", "20000000-0000-0000-0000-000000000001");
        assertThatIllegalStateException().isThrownBy(() -> payload(view));
    }

    @Test void rejectsAnInactiveCatalog() {
        ObjectNode view = routeView();
        view.put("active", false);
        assertThatIllegalStateException().isThrownBy(() -> payload(view));
    }

    @Test void rejectsHistoricalVersionInsteadOfSilentlySelectingTheNewCurrentVersion() {
        ObjectNode view = routeView();
        view.put("currentVersionId", PREVIOUS);
        view.set("versions", JSON.valueToTree(List.of(
                version(VERSION, stops("SYNTHETIC origin", "SYNTHETIC terminal")),
                version(PREVIOUS, stops("SYNTHETIC newer origin", "SYNTHETIC newer terminal")))));
        assertThatIllegalStateException().isThrownBy(() -> payload(view));
    }

    @Test void rejectsAbsentOrDuplicatedRequestedVersion() {
        ObjectNode absent = routeView();
        absent.set("versions", JSON.valueToTree(List.of(
                version(PREVIOUS, stops("SYNTHETIC origin", "SYNTHETIC terminal")))));
        assertThatIllegalStateException().isThrownBy(() -> payload(absent));

        ObjectNode duplicate = routeView();
        duplicate.set("versions", JSON.valueToTree(List.of(
                version(VERSION, stops("SYNTHETIC origin", "SYNTHETIC terminal")),
                version(VERSION, stops("SYNTHETIC another origin", "SYNTHETIC another terminal")))));
        assertThatIllegalStateException().isThrownBy(() -> payload(duplicate));
    }

    @Test void rejectsFewerThanThreeStops() {
        ObjectNode view = routeView();
        view.set("versions", JSON.valueToTree(List.of(version(VERSION, List.of(
                stop("start", "START", "SYNTHETIC origin"),
                stop("end", "END", "SYNTHETIC terminal"))))));
        assertThatIllegalStateException().isThrownBy(() -> payload(view));
    }

    @Test void rejectsEitherIncorrectTerminalKind() {
        ObjectNode wrongStart = routeView();
        wrongStart.set("versions", JSON.valueToTree(List.of(version(VERSION, List.of(
                stop("start", "INTERMEDIATE", "SYNTHETIC origin"),
                stop("middle", "INTERMEDIATE", "SYNTHETIC middle"),
                stop("end", "END", "SYNTHETIC terminal"))))));
        assertThatIllegalStateException().isThrownBy(() -> payload(wrongStart));

        ObjectNode wrongEnd = routeView();
        wrongEnd.set("versions", JSON.valueToTree(List.of(version(VERSION, List.of(
                stop("start", "START", "SYNTHETIC origin"),
                stop("middle", "INTERMEDIATE", "SYNTHETIC middle"),
                stop("end", "INTERMEDIATE", "SYNTHETIC terminal"))))));
        assertThatIllegalStateException().isThrownBy(() -> payload(wrongEnd));
    }

    @Test void rejectsBlankOriginOrDestinationNames() {
        ObjectNode blankOrigin = routeView();
        blankOrigin.set("versions", JSON.valueToTree(List.of(
                version(VERSION, stops("  ", "SYNTHETIC terminal")))));
        assertThatIllegalStateException().isThrownBy(() -> payload(blankOrigin));

        ObjectNode blankDestination = routeView();
        blankDestination.set("versions", JSON.valueToTree(List.of(
                version(VERSION, stops("SYNTHETIC origin", "")))));
        assertThatIllegalStateException().isThrownBy(() -> payload(blankDestination));
    }

    @Test void initializesAnUnpreparedStartThirtyMinutesInTheFuture() {
        assertThat(L08ConnectedTrial.uncommittedServiceStart(null, NOW, false))
                .isEqualTo(PLANNED);
    }

    @Test void preservesExistingFutureStartRegardlessOfPriorRejection() {
        assertThat(L08ConnectedTrial.uncommittedServiceStart(PLANNED, NOW, false))
                .isEqualTo(PLANNED);
        assertThat(L08ConnectedTrial.uncommittedServiceStart(PLANNED, NOW, true))
                .isEqualTo(PLANNED);
    }

    @Test void refusesToRenewAnExpiredOrBoundaryStartWhenOutcomeIsUncertain() {
        assertThatIllegalStateException().isThrownBy(() ->
                L08ConnectedTrial.uncommittedServiceStart(NOW.minusSeconds(1), NOW, false));
        assertThatIllegalStateException().isThrownBy(() ->
                L08ConnectedTrial.uncommittedServiceStart(NOW, NOW, false));
    }

    @Test void renewsExpiredOrBoundaryStartOnlyForKnownRejectedPrecommitAttempt() {
        assertThat(L08ConnectedTrial.uncommittedServiceStart(NOW.minusSeconds(1), NOW, true))
                .isEqualTo(PLANNED);
        assertThat(L08ConnectedTrial.uncommittedServiceStart(NOW, NOW, true))
                .isEqualTo(PLANNED);
    }

    @Test void refusesAnotherSubmissionWhenThePreviousOutcomeIsUnconfirmed() {
        assertThatIllegalStateException().isThrownBy(() ->
                L08ConnectedTrial.requireNoUnconfirmedServiceSubmission(true));
    }

    @Test void permitsPreparationWhenNoPriorSubmissionRemainsUnconfirmed() {
        assertThatNoException().isThrownBy(() ->
                L08ConnectedTrial.requireNoUnconfirmedServiceSubmission(false));
    }

    private static Map<String, Object> payload(JsonNode view) {
        return L08ConnectedTrial.serviceOfferPayload(
                view, ROUTE, VERSION, TYPE, DRIVER, VEHICLE, PLANNED);
    }

    private static ObjectNode routeView() {
        return JSON.valueToTree(Map.of(
                "id", ROUTE,
                "active", true,
                "currentVersionId", VERSION,
                "versions", List.of(version(VERSION, stops("SYNTHETIC origin", "SYNTHETIC terminal")))));
    }

    private static Map<String, Object> version(String id, List<Map<String, String>> stops) {
        return Map.of("id", id, "stops", stops);
    }

    private static List<Map<String, String>> stops(String origin, String destination) {
        return List.of(stop("start", "START", origin),
                stop("middle", "INTERMEDIATE", "SYNTHETIC middle"),
                stop("end", "END", destination));
    }

    private static Map<String, String> stop(String key, String kind, String name) {
        return Map.of("key", key, "kind", kind, "name", name);
    }
}
