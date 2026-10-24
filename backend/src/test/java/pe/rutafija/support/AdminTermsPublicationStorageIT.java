package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import pe.rutafija.terms.domain.AdminTermsDocument;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** One synthetic document, always rolled back; no Flyway, global cleanup, account or acceptance. */
class AdminTermsPublicationStorageIT {
    @Test void postgresPreservesExactTextAndItsHashAndRejectsMutationThenRollsBack() throws Exception {
        Map<String,Object> properties=new HashMap<>();
        NativePostgresTestDatabase.load().registerSpringProperties((key,value)->properties.put(key,value.get()));
        try (Connection connection=DriverManager.getConnection((String)properties.get("spring.datasource.url"),
                (String)properties.get("spring.datasource.username"),(String)properties.get("spring.datasource.password"))) {
            connection.setAutoCommit(false);
            JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("ruta_fija_test");
            assertThat(jdbc.queryForObject("select current_user",String.class)).isEqualTo("rf_test");
            String text="SYNTHETIC TEST DOCUMENT ONLY.\r\nAceptación: Perú, solo prueba.\r\n\r\n";
            String version="SYNTHETIC-TEST-ONLY-"+UUID.randomUUID().toString().substring(0,12);
            var document=AdminTermsDocument.publish("ADMIN_TERMS",version,text,Instant.now());
            try {
                jdbc.update("""
                        insert into admin_terms_document(id,document_key,version,content,content_sha256,synthetic,created_at)
                        values (?,'ADMIN_TERMS',?,?,?,true,current_timestamp)
                        """,document.getId(),version,document.getContent(),document.getContentSha256());
                assertThat(jdbc.queryForObject("select content from admin_terms_document where id=?",String.class,document.getId())).isEqualTo(text);
                assertThat(jdbc.queryForObject("select content_sha256=encode(sha256(convert_to(content,'UTF8')),'hex') from admin_terms_document where id=?",Boolean.class,document.getId())).isTrue();
                for (String sql:new String[]{"update admin_terms_document set content='SYNTHETIC MUTATION' where id=?", "delete from admin_terms_document where id=?"}) {
                    var savepoint=connection.setSavepoint();
                    try (var statement=connection.prepareStatement(sql)) {
                        statement.setObject(1,document.getId());
                        SQLException failure=assertThrows(SQLException.class,statement::executeUpdate);
                        assertThat(failure.getSQLState()).isEqualTo("P0001");
                    } finally { connection.rollback(savepoint); }
                }
                assertThat(jdbc.queryForObject("select count(*) from admin_terms_acceptance where document_id=?",Integer.class,document.getId())).isZero();
            } finally { connection.rollback(); }
            assertThat(jdbc.queryForObject("select count(*) from admin_terms_document where id=?",Integer.class,document.getId())).isZero();
            connection.rollback();
            System.out.println("ADMIN_TERMS_STORAGE_TEST=PASS_EXACT_BYTES_HASH_IMMUTABLE_ROLLBACK_NO_ACCEPTANCE_NO_GLOBAL_CLEANUP");
        }
    }
}
