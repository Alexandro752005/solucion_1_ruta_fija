package pe.rutafija.shared.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

public final class PageableFactory {

    private PageableFactory() {
    }

    public static Pageable create(
            int page,
            int size,
            String sortParameter,
            Set<String> allowedProperties,
            String defaultProperty
    ) {
        String sort = sortParameter == null || sortParameter.isBlank()
                ? defaultProperty + ",desc"
                : sortParameter.strip();
        String[] values = sort.split(",", -1);
        if (values.length > 2 || values[0].isBlank() || !allowedProperties.contains(values[0])) {
            throw new IllegalArgumentException("El criterio de ordenamiento no está permitido");
        }

        Sort.Direction direction = values.length == 2
                ? Sort.Direction.fromOptionalString(values[1].strip().toUpperCase(Locale.ROOT))
                        .orElseThrow(() -> new IllegalArgumentException("La dirección de ordenamiento no es válida"))
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, values[0].strip()));
    }
}
