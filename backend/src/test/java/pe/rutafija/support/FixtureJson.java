package pe.rutafija.support;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed JSON boundaries for local harness state; never opens a fixture or database. */
final class FixtureJson {
    static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() { };

    private FixtureJson() { }

    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) throw new IllegalStateException("JSON_OBJECT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (!(key instanceof String name)) throw new IllegalStateException("JSON_STRING_KEY_REQUIRED");
            result.put(name, item);
        });
        return result;
    }

    static List<Map<String, Object>> objects(Object value) {
        if (!(value instanceof List<?> source)) throw new IllegalStateException("JSON_OBJECT_LIST_REQUIRED");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) result.add(object(item));
        return result;
    }
}
