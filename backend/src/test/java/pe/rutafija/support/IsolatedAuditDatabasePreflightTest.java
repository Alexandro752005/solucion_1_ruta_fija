package pe.rutafija.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit disposable-cluster preflight. It cannot use the historical shared branch. */
@EnabledIfEnvironmentVariable(named = "RF_AUDIT_ISOLATED_MODE", matches = "EXCLUSIVE_AUDIT_55441")
class IsolatedAuditDatabasePreflightTest {
    @Test void migratesOnlyTheProvenExclusiveClusterAndValidatesAllCurrentMigrations() throws Exception {
        var target = IsolatedAuditPostgresTarget.fromEnvironment(System.getenv()).orElseThrow();
        Map<String, Object> properties = new HashMap<>();
        var database = NativePostgresTestDatabase.load(); // Live identity before Flyway, no private fallback.
        String schemaOnlyMode=System.getenv("RF_AUDIT_PREFLIGHT_SCHEMA_ONLY");
        boolean schemaOnly=schemaOnlyMode!=null;
        if(schemaOnly) {
            if(!"OWNED_EMPTY_SCHEMA_V51".equals(schemaOnlyMode)
                    ||!"REBUILD_OWNED_DISPOSABLE_SCHEMA_55441".equals(System.getenv("RF_AUDIT_NATIVE_SCHEMA_RESET")))
                throw new IllegalStateException("EXPLICIT_OWNED_EMPTY_SCHEMA_REQUIRED");
            database.cleanBusinessData();
        }
        database.registerSpringProperties((key, supplier) -> properties.put(key, supplier.get()));
        String url = (String) properties.get("spring.flyway.url");
        String user = (String) properties.get("spring.flyway.user");
        String password = (String) properties.get("spring.flyway.password");
        target.validateJdbcUrl(url);
        try (var connection = DriverManager.getConnection(url, user, password)) {
            target.verify(connection, "rf_migrator");
        }
        var flyway = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration", "classpath:db/test-migration")
                .target("51").cleanDisabled(true).connectRetries(0).load();
        flyway.migrate();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("51");
        if(schemaOnly) {
            System.out.println("AUDIT_SCHEMA_ONLY=PASS_V51_NO_BUSINESS_FIXTURES");
            return;
        }
        try (var connection = DriverManager.getConnection(url, user, password)) {
            target.verify(connection, "rf_migrator");
            // The historical test repeatable predates the protected retention tables.
            // Restore least-privilege parity only in this newly proven disposable cluster.
            // No trigger, constraint, immutable row or historical SQL file is changed.
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        DO $$ DECLARE t record; p text; BEGIN
                          FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP
                            FOREACH p IN ARRAY ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER'] LOOP
                              IF has_table_privilege('rf_test',format('public.%I',t.tablename),p)
                                 AND NOT has_table_privilege('rf_app',format('public.%I',t.tablename),p) THEN
                                EXECUTE format('REVOKE %s ON TABLE public.%I FROM rf_test',p,t.tablename);
                              END IF;
                            END LOOP;
                          END LOOP;
                        END $$
                        """);
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                    SELECT count(*) FILTER (WHERE version IS NOT NULL AND success),
                           count(*) FILTER (WHERE NOT success),
                           count(*) FILTER (WHERE version IS NULL AND success
                             AND script='R__native_test_role_privileges.sql')
                    FROM flyway_schema_history
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(51);
                assertThat(rows.getInt(2)).isZero();
                assertThat(rows.getInt(3)).isEqualTo(1);
            }
            // Rollback suites use the exact already-approved publication as a prerequisite.
            // No real account or acceptance is introduced by this isolated setup.
            Path publication = Path.of(System.getenv("RF_AUDIT_LEGAL_SOURCE_DIRECTORY"));
            byte[] content = Files.readAllBytes(publication.resolve("terminos-admin-v1.0.0.md"));
            var approval = new ObjectMapper().readTree(
                    publication.resolve("terminos-admin-v1.0.0.aprobacion.json").toFile());
            String verifiedContent = ApprovedAdminTermsPublication.validateApproval(content,
                    approval, ApprovedAdminTermsPublication.APPROVED_SHA);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO admin_terms_document(id,document_key,version,content,content_sha256,synthetic,created_at)
                    VALUES (?,'ADMIN_TERMS','1.0.0',?,?,false,current_timestamp)
                    ON CONFLICT (document_key,version) DO NOTHING
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, verifiedContent);
                statement.setString(3, ApprovedAdminTermsPublication.APPROVED_SHA.toLowerCase(java.util.Locale.ROOT));
                statement.executeUpdate();
            }
            // Stable IDs are aliases expected by historical regression sources.
            // Their names/emails are newly invented, not copied from any shared database.
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO organization(id,legal_name,trade_name,status,timezone,created_at,updated_at)
                        VALUES ('1a759298-7831-4aaf-9299-c3920a9cc28a','EXCLUSIVE AUDIT FOREIGN ORGANIZATION',
                          'SYNTHETIC ONLY','ACTIVE','America/Lima',current_timestamp,current_timestamp)
                        ON CONFLICT(id) DO NOTHING
                        """);
                statement.executeUpdate("""
                        INSERT INTO app_user(id,organization_id,email,password_hash,full_name,role,active,email_verified_at,created_at,updated_at)
                        SELECT incoming.id,incoming.organization_id,incoming.email,'!AUDIT-NO-LOGIN',
                          incoming.full_name,incoming.role,true,current_timestamp,current_timestamp,current_timestamp
                        FROM (VALUES
                          ('088518c1-821c-4316-9f87-090f51ac44e0'::uuid,NULL::uuid,
                           'exclusive.audit.owner@rutafija.test','EXCLUSIVE AUDIT SYNTHETIC OWNER','SUPER_ADMIN'),
                          ('92ebc07c-1319-4850-a734-f792cb299979'::uuid,'1a759298-7831-4aaf-9299-c3920a9cc28a'::uuid,
                           'exclusive.audit.foreign@rutafija.test','EXCLUSIVE AUDIT SYNTHETIC FOREIGN ADMIN','ADMIN')
                        ) incoming(id,organization_id,email,full_name,role)
                        WHERE NOT EXISTS(SELECT 1 FROM app_user existing WHERE existing.id=incoming.id)
                        ON CONFLICT(id) DO NOTHING
                        """);
                statement.executeUpdate("""
                        INSERT INTO person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at)
                        VALUES (repeat('a',64),'088518c1-821c-4316-9f87-090f51ac44e0','DNI',
                          '088518c1-821c-4316-9f87-090f51ac44e0','OWNER_SELF_REVIEW','EXCLUSIVE SYNTHETIC AUDIT FIXTURE',current_timestamp),
                         (repeat('b',64),'92ebc07c-1319-4850-a734-f792cb299979','DNI',
                          '088518c1-821c-4316-9f87-090f51ac44e0','IN_PERSON','EXCLUSIVE SYNTHETIC AUDIT FIXTURE',current_timestamp)
                        ON CONFLICT(user_id) DO NOTHING
                        """);
                statement.executeUpdate("""
                        INSERT INTO admin_terms_acceptance(id,admin_user_id,organization_id,document_id,document_version,content_sha256,accepted_at)
                        SELECT 'cf3f632c-d9cd-46ae-871f-30ef7a9e1131','92ebc07c-1319-4850-a734-f792cb299979',
                          '1a759298-7831-4aaf-9299-c3920a9cc28a',id,version,content_sha256,current_timestamp
                        FROM admin_terms_document WHERE document_key='ADMIN_TERMS' AND version='1.0.0'
                        ON CONFLICT(admin_user_id,document_id) DO NOTHING
                        """);
                // One historical tenant-isolation case expects a foreign route
                // and service type. Create invented prerequisites here instead
                // of reading catalog rows from the user's shared database.
                statement.execute("""
                        DO $$ DECLARE r uuid:=gen_random_uuid(); rv uuid:=gen_random_uuid();
                          t uuid:=gen_random_uuid(); tv uuid:=gen_random_uuid();
                          org uuid:='1a759298-7831-4aaf-9299-c3920a9cc28a';
                          actor uuid:='92ebc07c-1319-4850-a734-f792cb299979';
                        BEGIN
                          IF NOT EXISTS(SELECT 1 FROM route_version WHERE organization_id=org) THEN
                            INSERT INTO route_definition(id,organization_id,name,current_version_id,created_at,updated_at)
                            VALUES(r,org,'EXCLUSIVE SYNTHETIC FOREIGN ROUTE',rv,current_timestamp,current_timestamp);
                            INSERT INTO route_version(id,route_id,organization_id,revision,name,stops,content_sha256,created_by,created_at)
                            VALUES(rv,r,org,1,'EXCLUSIVE SYNTHETIC FOREIGN ROUTE',
                              '[{"name":"SYNTHETIC A"},{"name":"SYNTHETIC B"},{"name":"SYNTHETIC C"}]'::jsonb,
                              repeat('c',64),actor,current_timestamp);
                          END IF;
                          IF NOT EXISTS(SELECT 1 FROM service_type_version WHERE organization_id=org) THEN
                            INSERT INTO service_type(id,organization_id,name,current_version_id,created_at,updated_at)
                            VALUES(t,org,'EXCLUSIVE SYNTHETIC FOREIGN TYPE',tv,current_timestamp,current_timestamp);
                            INSERT INTO service_type_version(id,type_id,organization_id,revision,name,before_minutes,after_minutes,
                              content_sha256,created_by,created_at)
                            VALUES(tv,t,org,1,'EXCLUSIVE SYNTHETIC FOREIGN TYPE',0,0,repeat('d',64),actor,current_timestamp);
                          END IF;
                        END $$
                        """);
            }
            // Retention suites preserve an enrolled credential and stable HMAC
            // key. Supply newly generated, unusable-for-human-login test material
            // rather than inheriting the workstation owner's installation.
            var secret = new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder()
                    .decode(System.getenv("RF_AUDIT_JWT_SECRET_BASE64")), "HmacSHA256");
            String fingerprint = new pe.rutafija.invitation.application.InvitationCodeHasher(secret)
                    .hashOrigin("person-identity:key-fingerprint:v1");
            try (var statement = connection.prepareStatement("""
                    INSERT INTO person_identity_hash_key(id,key_fingerprint,recorded_at)
                    VALUES(1,?,current_timestamp) ON CONFLICT(id) DO NOTHING
                    """)) {
                statement.setString(1,fingerprint);
                statement.executeUpdate();
            }
            var generator=java.security.KeyPairGenerator.getInstance("Ed25519");
            String publicKey=java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
            String emergencyHash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .encode(UUID.randomUUID().toString());
            try (var statement=connection.prepareStatement("""
                    INSERT INTO super_admin_recovery_credential(id,installation_id,user_id,public_key_x509,
                      emergency_code_hash,enrolled_at,updated_at)
                    VALUES(1,?,'088518c1-821c-4316-9f87-090f51ac44e0',?,?,current_timestamp,current_timestamp)
                    ON CONFLICT(id) DO NOTHING
                    """)) {
                statement.setObject(1,UUID.randomUUID());
                statement.setString(2,publicKey);
                statement.setString(3,emergencyHash);
                statement.executeUpdate();
            }
        }
        database.assertTestRolePermissions();
    }
}
