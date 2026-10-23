package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pe.rutafija.terms.domain.AdminTermsContentHash;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic approval/text, pure validation; no publication or acceptance in these tests. */
class ApprovedAdminTermsPublicationTest {
    private final String text="SYNTHETIC TEST DOCUMENT ONLY.\r\nSynthetic approval fixture.\r\n";
    private final byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
    private final String hash=AdminTermsContentHash.sha256(text);
    private ObjectNode approval() {
        return new ObjectMapper().createObjectNode().put("documentKey","ADMIN_TERMS").put("version","1.0.0")
                .put("contentFile","terminos-admin-v1.0.0.md").put("decision","APROBADO_PARA_PUBLICACION_LOCAL_INMUTABLE")
                .put("contentSha256",hash).put("contentBytes",bytes.length).put("approvalSource","SYNTHETIC UNIT ONLY")
                .put("approvalQuote","SYNTHETIC UNIT ONLY").put("adminAcceptanceGranted",false).put("L02Authorized",false);
    }
    @Test void validatesExactSyntheticBytesWithoutTrimming() throws Exception {
        assertThat(ApprovedAdminTermsPublication.validateApproval(bytes,approval(),hash)).isEqualTo(text);
    }
    @Test void refusesAChangedByteSequence() {
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(text.strip().getBytes(StandardCharsets.UTF_8),approval(),hash))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesAnUnapprovedDecision() {
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(bytes,approval().put("decision","BORRADOR"),hash))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesAHashThatWasNotApproved() {
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(bytes,approval(),"0".repeat(64)))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void doesNotTurnPublicationIntoAcceptanceOrLotApproval() {
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(bytes,approval().put("adminAcceptanceGranted",true),hash))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(bytes,approval().put("L02Authorized",true),hash))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesAnotherVersionOrContentFile() {
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(bytes,approval().put("version","2.0.0"),hash))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->ApprovedAdminTermsPublication.validateApproval(bytes,approval().put("contentFile","../other.md"),hash))
                .isInstanceOf(IllegalStateException.class);
    }
}
