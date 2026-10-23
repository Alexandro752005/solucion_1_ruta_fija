package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureJsonTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void readsNestedSyntheticStateWithoutChangingNumbersOrReferenceValues() throws Exception {
        Map<String, Object> state = json.readValue("{\"count\":3,\"reference\":\"synthetic\",\"nested\":{\"ok\":true}}", FixtureJson.OBJECT_TYPE);
        assertThat(state).containsEntry("count", 3).containsEntry("reference", "synthetic");
        assertThat(FixtureJson.object(state.get("nested"))).containsEntry("ok", true);
    }

    @Test void typedReadRefusesAnArrayInsteadOfAStateObject() {
        assertThatThrownBy(() -> json.readValue("[]", FixtureJson.OBJECT_TYPE))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class);
    }

    @Test void refusesNonStringKeysBeforeTheHarnessUsesAnObject() {
        assertThatThrownBy(() -> FixtureJson.object(Map.of(3, "synthetic")))
                .isInstanceOf(IllegalStateException.class).hasMessage("JSON_STRING_KEY_REQUIRED");
    }

    @Test void refusesMissingOrNonObjectStateRatherThanInventingAnEmptyState() {
        assertThatThrownBy(() -> FixtureJson.object(null)).hasMessage("JSON_OBJECT_REQUIRED");
        assertThatThrownBy(() -> FixtureJson.object(List.of())).hasMessage("JSON_OBJECT_REQUIRED");
    }

    @Test void refusesMalformedEventRecordsWithoutDiscardingThemSilently() {
        assertThatThrownBy(() -> FixtureJson.objects(Map.of())).hasMessage("JSON_OBJECT_LIST_REQUIRED");
        assertThatThrownBy(() -> FixtureJson.objects(List.of("not-an-event"))).hasMessage("JSON_OBJECT_REQUIRED");
    }

    @Test void resumedProgressRemainsMutableAndIsPersistedThroughItsOwningState() throws Exception {
        Map<String, Object> state = json.readValue("{\"steps\":[{\"phase\":\"previous\"}]}", FixtureJson.OBJECT_TYPE);
        var steps = FixtureJson.objects(state.get("steps"));
        state.put("steps", steps);
        steps.add(Map.of("phase", "next"));
        Map<String, Object> restored = json.readValue(json.writeValueAsBytes(state), FixtureJson.OBJECT_TYPE);
        assertThat(FixtureJson.objects(restored.get("steps"))).containsExactly(
                Map.of("phase", "previous"), Map.of("phase", "next"));
    }
}
