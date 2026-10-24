package pe.rutafija.invitation.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationServiceBoundaryTest {

    @Test
    void accountAndOriginGuardsExpireAtTheirExactBoundary() {
        Instant boundary = Instant.parse("2026-10-14T12:00:00Z");

        assertThat(InvitationService.laterActiveBlock(boundary, null, boundary)).isNull();
        assertThat(InvitationService.laterActiveBlock(null, boundary, boundary)).isNull();
        assertThat(InvitationService.laterActiveBlock(boundary.plusNanos(1), null, boundary))
                .isEqualTo(boundary.plusNanos(1));
        assertThat(InvitationService.laterActiveBlock(null, boundary.plusNanos(1), boundary))
                .isEqualTo(boundary.plusNanos(1));
    }
}
