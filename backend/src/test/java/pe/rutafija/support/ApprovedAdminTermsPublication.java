package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.domain.AdminTermsContentHash;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Local publication only in ruta_fija_test, after explicit approval. Never accepts for an ADMIN. */
public final class ApprovedAdminTermsPublication {
    static final String APPROVED_SHA="20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417";
    private ApprovedAdminTermsPublication() { }

    public static void main(String[] args) {
        if (args.length!=1 || !("--publish".equals(args[0]) || "--check".equals(args[0]))) {
            System.err.println("ADMIN_TERMS_PUBLICATION=REFUSED_EXPECT_CHECK_OR_PUBLISH"); System.exit(2);
        }
        try { execute("--publish".equals(args[0])); }
        catch (Exception failure) {
            System.err.println("ADMIN_TERMS_PUBLICATION=REFUSED_NO_OVERWRITE_NO_ACCEPTANCE_DETAILS_REDACTED"); System.exit(1);
        }
    }

    static String validateApproval(byte[] bytes, JsonNode approval, String expectedSha) throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String content=new String(bytes,StandardCharsets.UTF_8);
        if (!hash.equalsIgnoreCase(expectedSha) || !hash.equalsIgnoreCase(approval.path("contentSha256").asText())
                || !hash.equals(AdminTermsContentHash.sha256(content)) || content.isBlank()
                || !"ADMIN_TERMS".equals(approval.path("documentKey").asText())
                || !"1.0.0".equals(approval.path("version").asText())
                || !"terminos-admin-v1.0.0.md".equals(approval.path("contentFile").asText())
                || !"APROBADO_PARA_PUBLICACION_LOCAL_INMUTABLE".equals(approval.path("decision").asText())
                || approval.path("approvalSource").asText().isBlank() || approval.path("approvalQuote").asText().isBlank()
                || !approval.has("adminAcceptanceGranted") || approval.path("adminAcceptanceGranted").asBoolean(true)
                || !approval.has("L02Authorized") || approval.path("L02Authorized").asBoolean(true)
                || bytes.length!=approval.path("contentBytes").asInt(-1))
            throw new IllegalStateException("APPROVAL_OR_BYTES_MISMATCH");
        return content;
    }

    private static void execute(boolean publish) throws Exception {
        Path folder=Path.of("../docs/legal/publicado").toAbsolutePath().normalize();
        byte[] bytes=Files.readAllBytes(folder.resolve("terminos-admin-v1.0.0.md"));
        JsonNode approval=new ObjectMapper().readTree(Files.readAllBytes(folder.resolve("terminos-admin-v1.0.0.aprobacion.json")));
        String content=validateApproval(bytes,approval,APPROVED_SHA);
        AdminTermsDocument candidate=AdminTermsDocument.publish("ADMIN_TERMS","1.0.0",content,Instant.now());
        if (!candidate.getContent().equals(content) || !candidate.getContentSha256().equalsIgnoreCase(APPROVED_SHA))
            throw new IllegalStateException("DOMAIN_CONTENT_NORMALIZATION_REJECTED");
        Map<String,Object> properties=new HashMap<>();
        NativePostgresTestDatabase.load().registerSpringProperties((key,value)->properties.put(key,value.get()));
        try (Connection connection=DriverManager.getConnection((String)properties.get("spring.datasource.url"),
                (String)properties.get("spring.datasource.username"),(String)properties.get("spring.datasource.password"))) {
            connection.setReadOnly(!publish);
            connection.setAutoCommit(false);
            JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            if (!"ruta_fija_test".equals(jdbc.queryForObject("select current_database()",String.class))
                    || !"rf_test".equals(jdbc.queryForObject("select current_user",String.class)))
                throw new IllegalStateException("DATABASE_TARGET_REJECTED");
            if (publish) jdbc.execute("select pg_advisory_xact_lock(751846,1)");
            var documents=jdbc.query("select id,content,content_sha256,synthetic from admin_terms_document where document_key='ADMIN_TERMS' and version='1.0.0'",
                    (rs,row)->new Existing(rs.getObject("id",UUID.class),rs.getString("content"),rs.getString("content_sha256"),rs.getBoolean("synthetic")));
            UUID documentId;
            boolean inserted=false;
            if (documents.isEmpty()) {
                if (!publish) throw new IllegalStateException("APPROVED_DOCUMENT_NOT_REGISTERED");
                documentId=candidate.getId();
                jdbc.update("""
                        insert into admin_terms_document(id,document_key,version,content,content_sha256,synthetic,created_at)
                        values (?,'ADMIN_TERMS','1.0.0',?,?,false,current_timestamp)
                        """,documentId,content,candidate.getContentSha256());
                inserted=true;
            } else {
                Existing existing=documents.getFirst();
                if (documents.size()!=1 || existing.synthetic || !existing.content.equals(content)
                        || !existing.hash.equals(candidate.getContentSha256()))
                    throw new IllegalStateException("IMMUTABLE_VERSION_CONFLICT");
                documentId=existing.id;
            }
            var stored=jdbc.queryForMap("""
                    select created_at,content_sha256=encode(sha256(convert_to(content,'UTF8')),'hex') hash_matches
                    from admin_terms_document where id=?
                    """,documentId);
            if (!Boolean.TRUE.equals(stored.get("hash_matches"))) throw new IllegalStateException("PERSISTED_HASH_MISMATCH");
            UUID current=jdbc.queryForObject("select id from admin_terms_document where document_key='ADMIN_TERMS' order by created_at desc,id desc limit 1",UUID.class);
            if (!documentId.equals(current)) throw new IllegalStateException("APPROVED_DOCUMENT_NOT_CURRENT");
            int acceptances=jdbc.queryForObject("select count(*) from admin_terms_acceptance where document_id=?",Integer.class,documentId);
            Instant created=((Timestamp)stored.get("created_at")).toInstant();
            if (publish) connection.commit(); else connection.rollback();
            System.out.println("ADMIN_TERMS_DOCUMENT_ID="+documentId);
            System.out.println("ADMIN_TERMS_VERSION=1.0.0");
            System.out.println("ADMIN_TERMS_CONTENT_SHA256="+APPROVED_SHA);
            System.out.println("ADMIN_TERMS_PUBLISHED_AT_UTC="+created);
            System.out.println("ADMIN_TERMS_SYNTHETIC=false; STORED_HASH_AND_CONTENT_MATCH=true; CURRENT=true");
            System.out.println("ADMIN_TERMS_ACCEPTANCES_PRESENT="+acceptances+"; ACCEPTANCES_CREATED_BY_THIS_COMMAND=0");
            System.out.println("ADMIN_TERMS_PUBLICATION_DATABASE=ruta_fija_test; ROLE=rf_test; NEW_DOCUMENT="+inserted);
            System.out.println("ADMIN_TERMS_SMTP_ATTEMPTS=0; SPRING_STARTED=false; FLYWAY_STARTED=false; CLEANUP_EXECUTED=false");
            System.out.println("ADMIN_TERMS_PUBLICATION=PASS_"+(publish?"APPROVED_IMMUTABLE_DOCUMENT_REGISTERED":"READ_ONLY_CORRESPONDENCE"));
        }
    }
    private record Existing(UUID id,String content,String hash,boolean synthetic) { }
}
