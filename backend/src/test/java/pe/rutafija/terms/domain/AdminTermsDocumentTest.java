package pe.rutafija.terms.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminTermsDocumentTest {
    @Test void preservesLeadingAndTrailingWhitespaceAndLfWithoutChangingItsHash() {
        String content="  SYNTHETIC TEST DOCUMENT ONLY.\nTexto de prueba.\n\n";
        var document=AdminTermsDocument.publish("ADMIN_TERMS","SYNTHETIC-TEST-ONLY-LF",content,Instant.now());
        assertThat(document.getContent()).isEqualTo(content);
        assertThat(document.getContentSha256()).isEqualTo(AdminTermsContentHash.sha256(document.getContent()));
    }
    @Test void preservesCrLfUnicodeAndFinalNewline() {
        String content="SYNTHETIC TEST DOCUMENT ONLY.\r\nAceptación expresa: Perú.\r\n";
        var document=AdminTermsDocument.publish("ADMIN_TERMS","SYNTHETIC-TEST-ONLY-CRLF",content,Instant.now());
        assertThat(document.getContent()).isEqualTo(content);
        assertThat(document.getContentSha256()).isEqualTo(AdminTermsContentHash.sha256(content));
    }
    @Test void rejectsEmptyOrBlankContent() {
        for (String content:new String[]{""," ","\r\n\t"})
            assertThatThrownBy(() -> AdminTermsDocument.publish("ADMIN_TERMS","SYNTHETIC-TEST-ONLY-EMPTY",content,Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsNullContent() {
        assertThatThrownBy(() -> AdminTermsDocument.publish("ADMIN_TERMS","SYNTHETIC-TEST-ONLY-NULL",null,Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
