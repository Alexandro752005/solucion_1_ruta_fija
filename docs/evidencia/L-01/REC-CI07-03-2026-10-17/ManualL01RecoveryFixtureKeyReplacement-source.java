package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static pe.rutafija.support.ManualL01HumanTrial.*;
import static pe.rutafija.support.ManualL01RecoveryFixtureRepair.*;

/** REC-CI07-03: authorized preparation of one synthetic fixture, never a production API. */
final class ManualL01RecoveryFixtureKeyReplacement {
    static final String PROPOSAL_ID = "REC-CI07-03";
    static final String PROPOSAL_SHA = "1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8";
    static final String APPROVAL_SHA = "434560CD1674A008405E5B1AA425CC5E8E6C7DDC17475A997EA2B7414C32BBA6";
    static final UUID CODE_REPAIR_AUDIT = UUID.fromString("d791653a-6379-4f02-ac1d-1db020dde403");
    static final String ACTION = "L01_SYNTHETIC_RECOVERY_KEY_FIXTURE_REPLACED";
    static final String SOURCE = "AUTHORIZED_TEST_FIXTURE_MAINTENANCE_NOT_HTTP_ENROLLMENT";
    static final String UPDATE_SQL = """
            update super_admin_recovery_credential set public_key_x509=?,updated_at=current_timestamp
            where id=1 and user_id=? and installation_id=? and public_key_x509=?
            """;
    static final String AUDIT_SQL = """
            insert into audit_event(id,organization_id,user_id,action,entity_type,entity_id,metadata_json,occurred_at)
            values(?,null,?,?,'L01_SYNTHETIC_FIXTURE',?,?::jsonb,current_timestamp)
            """;
    static final String PUBLIC_AUDITS_SQL = """
            select id::text audit_id,user_id::text owner_id,entity_id::text installation_id,occurred_at,
              metadata_json->>'proposalId' proposal_id,metadata_json->>'proposalSha256' proposal_sha256,
              metadata_json->>'approvalSha256' approval_sha256,metadata_json->>'caseRunId' case_run_id,
              metadata_json->>'recoveryObservationSegmentId' segment_id,
              metadata_json->>'oldPublicKeyFingerprintSha256' old_public_sha,
              metadata_json->>'newPublicKeyFingerprintSha256' new_public_sha,
              metadata_json->>'syntheticFixtureKeyReplacementCount' replacement_count,
              metadata_json->>'humanFingerprintConfirmationBeforeWrite' fingerprint_confirmed,
              metadata_json->>'humanPrimaryAndCopyChecksReported' pair_checks_reported,
              metadata_json->>'currentEmergencyCodeAvailableReported' emergency_available_reported,
              metadata_json->>'preparationSource' preparation_source
            from audit_event where user_id=? and action=? order by occurred_at,id
            """;
    static final List<String> REQUIRED = List.of("authorization", "codeRepairAuthorization", "exactCaseAndMarker",
            "environmentApproved", "harnessOwnsCleanupLock", "identitiesMatch", "identityReviewed",
            "credentialMatches", "termsAndAcceptancePreserved", "historicalLedgerPreserved", "noCompletedRecovery",
            "noLiveChallenge", "enrollmentObservedOnce", "observationHealthy", "codeRepairPreservedOnce",
            "segmentMatches", "noPriorKeyReplacement");

    record PublicCandidate(String encoded, String fingerprint) { }
    record Confirmation(boolean fingerprintMatches, boolean primaryAndCopyChecked, boolean emergencyAvailable) {
        boolean complete() { return fingerprintMatches && primaryAndCopyChecked && emergencyAvailable; }
    }
    interface PublicTerminal {
        String readPublicKey();
        String readFingerprint();
        Confirmation confirm(PublicCandidate candidate);
    }
    interface Authentication {
        void authenticate() throws Exception;
        void reauthenticate() throws Exception;
        void verifyCurrentSession() throws Exception;
    }
    interface Store {
        Map<String, Object> inspect() throws Exception;
        Map<String, Object> commit(PublicCandidate candidate) throws Exception;
    }

    static String fingerprint(String encoded) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(encoded)));
    }

    static PublicCandidate candidate(String encoded, String expectedFingerprint) {
        try {
            if (encoded == null || expectedFingerprint == null || encoded.length() > 100
                    || !expectedFingerprint.strip().matches("[A-Fa-f0-9]{64}")) throw new IllegalArgumentException();
            String canonical = encoded.strip();
            byte[] der = Base64.getDecoder().decode(canonical);
            if (der.length != 44 || !Base64.getEncoder().encodeToString(der).equals(canonical)) throw new IllegalArgumentException();
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
            if (!Set.of("EdDSA", "Ed25519").contains(key.getAlgorithm())) throw new IllegalArgumentException();
            String hash = fingerprint(canonical);
            if (!hash.equalsIgnoreCase(expectedFingerprint.strip()) || PUBLIC_SHA.equals(hash)) throw new IllegalArgumentException();
            return new PublicCandidate(canonical, hash);
        } catch (Exception invalid) { throw new IllegalStateException("KEY_PREP_PUBLIC_OR_FINGERPRINT_INVALID"); }
    }

    static String refusal(Map<String, Object> facts) {
        for (String key : REQUIRED) if (!Boolean.TRUE.equals(facts.get(key)))
            return "KEY_PREP_GUARD_" + key.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        return null;
    }

    static void requireReady(Map<String, Object> facts) {
        String reason = refusal(facts);
        if (reason != null) throw new IllegalStateException(reason);
    }

    static Map<String, Object> execute(PublicTerminal terminal, Authentication authentication, Store store) throws Exception {
        requireReady(store.inspect());
        authentication.authenticate();
        authentication.reauthenticate();
        PublicCandidate publicCandidate = candidate(terminal.readPublicKey(), terminal.readFingerprint());
        Confirmation confirmation = terminal.confirm(publicCandidate);
        if (confirmation == null || !confirmation.complete()) throw new IllegalStateException("KEY_PREP_CONFIRMATION_REJECTED_NO_WRITE");
        authentication.verifyCurrentSession();
        requireReady(store.inspect());
        return store.commit(publicCandidate);
    }

    static boolean authorizationValid() throws Exception {
        byte[] approval = Files.readAllBytes(ROOT.resolve("docs/operacion/L-01-REC-CI07-03-autorizacion-2026-10-17.json"));
        byte[] proposal = Files.readAllBytes(ROOT.resolve("docs/operacion/L-01-REC-CI07-03-clave-temporal-propuesta.md"));
        if (!APPROVAL_SHA.equalsIgnoreCase(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(approval)))
                || !PROPOSAL_SHA.equalsIgnoreCase(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(proposal)))) return false;
        var a = JSON.readTree(approval);
        return a.path("authorized").asBoolean() && PROPOSAL_ID.equals(a.path("decisionId").asText())
                && RUN.toString().equals(a.path("runId").asText()) && OWNER.toString().equals(a.path("ownerId").asText())
                && ORGANIZATION.toString().equals(a.path("organizationId").asText()) && ADMIN.toString().equals(a.path("adminId").asText())
                && INSTALLATION.toString().equals(a.path("installationId").asText())
                && PUBLIC_SHA.equals(a.path("originalPublicKeySha256").asText())
                && CODE_REPAIR_AUDIT.toString().equals(a.path("existingCodeRepairAuditId").asText())
                && a.path("maximumSuccessfulKeyReplacements").asInt() == 1
                && a.path("explicitSyntheticPreparationExceptionAccepted").asBoolean()
                && a.path("newPublicKeyIsNotSecondHttpEnrollment").asBoolean()
                && a.path("humanGenerationAndCustodyRequired").asBoolean()
                && a.path("humanPrimaryAndCopyChecksRequired").asBoolean()
                && a.path("humanAuthenticationAndReauthenticationRequired").asBoolean()
                && a.path("humanPublicFingerprintConfirmationBeforeWriteRequired").asBoolean()
                && a.path("keepExistingEmergencyCodeAndCodeRepair").asBoolean()
                && !a.path("additionalCodeRepairAuthorized").asBoolean()
                && !a.path("normalProtocolChangesAuthorized").asBoolean();
    }

    static Map<String, Object> publicResult(String audit, Instant at, String segment, String newFingerprint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syntheticFixtureKeyReplacementCount", 1);
        result.put("fixtureKeyReplacementAuditId", audit);
        result.put("fixtureKeyReplacedAtUtc", at.toString());
        result.put("fixtureKeyReplacementProposalId", PROPOSAL_ID);
        result.put("fixtureKeyReplacementProposalSha256", PROPOSAL_SHA);
        result.put("fixtureKeyReplacementApprovalSha256", APPROVAL_SHA);
        result.put("fixtureKeyReplacementSegmentId", segment);
        result.put("originalEnrollmentResponseCount", 1);
        result.put("originalEnrolledPublicKeyFingerprintSha256", PUBLIC_SHA);
        result.put("replacementPublicKeyFingerprintSha256", newFingerprint);
        result.put("currentRecoveryKeyFingerprintSha256", newFingerprint);
        result.put("fixtureKeyPreparationSource", SOURCE);
        result.put("fixtureKeyHumanFingerprintConfirmationBeforeWrite", true);
        result.put("fixtureKeyHumanPrimaryAndCopyChecksReported", true);
        result.put("fixtureKeyCurrentEmergencyCodeAvailableReported", true);
        return result;
    }

    static Map<String, Object> restorePublicReference(Map<String, Object> row, String actualFingerprint,
            Map<String, Object> state) {
        boolean valid = RUN.toString().equals(row.get("case_run_id")) && OWNER.toString().equals(row.get("owner_id"))
                && INSTALLATION.toString().equals(row.get("installation_id")) && PROPOSAL_ID.equals(row.get("proposal_id"))
                && PROPOSAL_SHA.equals(row.get("proposal_sha256")) && APPROVAL_SHA.equals(row.get("approval_sha256"))
                && PUBLIC_SHA.equals(row.get("old_public_sha")) && "1".equals(row.get("replacement_count"))
                && actualFingerprint.matches("[a-f0-9]{64}") && !PUBLIC_SHA.equals(actualFingerprint)
                && actualFingerprint.equals(row.get("new_public_sha")) && SOURCE.equals(row.get("preparation_source"))
                && "true".equals(row.get("fingerprint_confirmed")) && "true".equals(row.get("pair_checks_reported"))
                && "true".equals(row.get("emergency_available_reported"))
                && row.get("occurred_at") instanceof Timestamp;
        try { UUID.fromString(Objects.toString(row.get("audit_id"), "")); UUID.fromString(Objects.toString(row.get("segment_id"), "")); }
        catch (IllegalArgumentException invalid) { valid = false; }
        if (state.containsKey("replacementPublicKeyFingerprintSha256"))
            valid &= actualFingerprint.equals(state.get("replacementPublicKeyFingerprintSha256"));
        if (!valid) throw new IllegalStateException("KEY_PREP_RESTORED_PUBLIC_REFERENCE_REFUSED");
        var restored = publicResult(row.get("audit_id").toString(), ((Timestamp) row.get("occurred_at")).toInstant(),
                row.get("segment_id").toString(), actualFingerprint);
        restored.put("fixtureKeyReferenceSource", "PERSISTED_AUTHORIZED_PUBLIC_AUDIT_NO_SECRET");
        return restored;
    }

    static boolean evidenceComplete(Map<String, Object> state) {
        if (!state.containsKey("syntheticFixtureKeyReplacementCount")) return true;
        String replacement = Objects.toString(state.get("replacementPublicKeyFingerprintSha256"), "");
        return ((Number) state.getOrDefault("syntheticFixtureKeyReplacementCount", 0)).intValue() == 1
                && ((Number) state.getOrDefault("syntheticFixtureCodeRepairCount", 0)).intValue() == 1
                && CODE_REPAIR_AUDIT.toString().equals(state.get("fixtureRepairAuditId"))
                && PROPOSAL_ID.equals(state.get("fixtureKeyReplacementProposalId"))
                && PROPOSAL_SHA.equals(state.get("fixtureKeyReplacementProposalSha256"))
                && APPROVAL_SHA.equals(state.get("fixtureKeyReplacementApprovalSha256"))
                && ((Number) state.getOrDefault("originalEnrollmentResponseCount", 0)).intValue() == 1
                && PUBLIC_SHA.equals(state.get("originalEnrolledPublicKeyFingerprintSha256"))
                && replacement.matches("[a-f0-9]{64}") && !PUBLIC_SHA.equals(replacement)
                && replacement.equals(state.get("currentRecoveryKeyFingerprintSha256"))
                && replacement.equals(state.get("publicKeyPreservationBaselineSha256"))
                && replacement.equals(state.get("recoveryCompletedPublicKeySha256"))
                && SOURCE.equals(state.get("fixtureKeyPreparationSource"))
                && Boolean.TRUE.equals(state.get("fixtureKeyPersistedAuditCorrespondenceVerified"))
                && validUuid(state.get("fixtureKeyReplacementAuditId")) && validUuid(state.get("fixtureKeyReplacementSegmentId"))
                && List.of("fixtureKeyHumanFingerprintConfirmationBeforeWrite", "fixtureKeyHumanPrimaryAndCopyChecksReported",
                    "fixtureKeyCurrentEmergencyCodeAvailableReported").stream().allMatch(key -> Boolean.TRUE.equals(state.get(key)));
    }

    static boolean validUuid(Object value) {
        try { UUID.fromString(Objects.toString(value, "")); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    static class JdbcStore implements Store {
        final JdbcTemplate jdbc;
        final PlatformTransactionManager transactions;
        final Map<String, Object> state;
        final int cleanupLockPid;
        final UUID segment;

        JdbcStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, Map<String, Object> state, int cleanupLockPid, UUID segment) {
            this.jdbc = jdbc; this.transactions = transactions; this.state = state;
            this.cleanupLockPid = cleanupLockPid; this.segment = segment;
        }

        @Override public Map<String, Object> inspect() throws Exception {
            var base = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc, null, state, cleanupLockPid, segment).inspect();
            var facts = new LinkedHashMap<>(base);
            facts.remove("noPriorRepair");
            facts.remove("authorizationAndOutputCheck");
            facts.put("authorization", authorizationValid());
            facts.put("codeRepairAuthorization", base.get("authorizationAndOutputCheck"));
            facts.put("codeRepairPreservedOnce", count(jdbc, "select count(*) from audit_event where user_id=? and action=?", OWNER,
                    ManualL01RecoveryFixtureRepair.AUDIT_ACTION) == 1
                    && count(jdbc, """
                        select count(*) from audit_event where id=? and user_id=? and action=?
                          and metadata_json->>'caseRunId'=? and metadata_json->>'proposalId'='REC-CI07-02'
                          and metadata_json->>'syntheticFixtureCodeRepairCount'='1'
                          and metadata_json->>'publicKeyFingerprintSha256'=?
                        """, CODE_REPAIR_AUDIT, OWNER, ManualL01RecoveryFixtureRepair.AUDIT_ACTION, RUN.toString(), PUBLIC_SHA) == 1
                    && ((Number) state.getOrDefault("syntheticFixtureCodeRepairCount", 0)).intValue() == 1
                    && CODE_REPAIR_AUDIT.toString().equals(state.get("fixtureRepairAuditId")));
            facts.put("segmentMatches", segment != null && segment.toString().equals(state.get("recoveryObservationSegmentId")));
            facts.put("noPriorKeyReplacement", count(jdbc, "select count(*) from audit_event where user_id=? and action=?", OWNER, ACTION) == 0
                    && !state.containsKey("syntheticFixtureKeyReplacementCount"));
            return facts;
        }

        @Override public Map<String, Object> commit(PublicCandidate requested) throws Exception {
            PublicCandidate validated = candidate(requested.encoded(), requested.fingerprint());
            if (transactions == null || segment == null) throw new IllegalStateException("KEY_PREP_TRANSACTION_CONTEXT_REQUIRED");
            var tx = new TransactionTemplate(transactions);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(10);
            return Objects.requireNonNull(tx.execute(status -> {
                try {
                    lockHistoricalCreationCounters(jdbc);
                    jdbc.queryForList("select id from app_user where id=? for update", OWNER);
                    jdbc.queryForList("select id from super_admin_recovery_credential where id=1 for update");
                    jdbc.queryForList("select id from app_user where id=? for share", ADMIN);
                    jdbc.queryForList("select id from organization where id=? for share", ORGANIZATION);
                    requireReady(inspect());
                    String previousPublic = jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1", String.class);
                    if (jdbc.update(UPDATE_SQL, validated.encoded(), OWNER, INSTALLATION, previousPublic) != 1)
                        throw new IllegalStateException("KEY_PREP_UPDATE_REFUSED");
                    UUID audit = UUID.randomUUID(); var metadata = new LinkedHashMap<String, Object>();
                    metadata.put("proposalId", PROPOSAL_ID); metadata.put("proposalSha256", PROPOSAL_SHA);
                    metadata.put("approvalSha256", APPROVAL_SHA); metadata.put("caseRunId", RUN.toString());
                    metadata.put("reason", "TEMPORARY_KEY_ENCRYPTION_PASSWORD_UNAVAILABLE");
                    metadata.put("oldPublicKeyFingerprintSha256", PUBLIC_SHA);
                    metadata.put("newPublicKeyFingerprintSha256", validated.fingerprint());
                    metadata.put("recoveryObservationSegmentId", segment.toString());
                    metadata.put("syntheticFixtureKeyReplacementCount", 1);
                    metadata.put("humanFingerprintConfirmationBeforeWrite", true);
                    metadata.put("humanPrimaryAndCopyChecksReported", true);
                    metadata.put("currentEmergencyCodeAvailableReported", true);
                    metadata.put("preparationSource", SOURCE);
                    if (jdbc.update(AUDIT_SQL, audit, OWNER, ACTION, INSTALLATION, JSON.writeValueAsString(metadata)) != 1)
                        throw new IllegalStateException("KEY_PREP_AUDIT_REFUSED");
                    Timestamp at = jdbc.queryForObject("select occurred_at from audit_event where id=?", Timestamp.class, audit);
                    return publicResult(audit.toString(), Objects.requireNonNull(at).toInstant(), segment.toString(), validated.fingerprint());
                } catch (Exception failure) {
                    status.setRollbackOnly();
                    if (failure instanceof IllegalStateException known) throw known;
                    throw new IllegalStateException("KEY_PREP_TRANSACTION_REFUSED_DETAILS_REDACTED");
                }
            }));
        }
    }
}
