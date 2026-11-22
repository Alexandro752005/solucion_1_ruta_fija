-- Manual. Use psql -X -v ON_ERROR_STOP=1 with a private service alias.
-- Required variables: expected_database and phase (baseline, empty, restored).
\set ON_ERROR_STOP on
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '5s';

SELECT current_database() = :'expected_database' AS expected_target,
       :'phase' IN ('baseline', 'empty', 'restored') AS valid_phase,
       :'phase' = 'empty' AS expect_empty
\gset
\if :expected_target
\else
DO $$ BEGIN RAISE EXCEPTION 'PREFLIGHT_UNEXPECTED_DATABASE'; END $$;
\endif
\if :valid_phase
\else
DO $$ BEGIN RAISE EXCEPTION 'PREFLIGHT_INVALID_PHASE'; END $$;
\endif

SELECT current_setting('server_version_num') AS server_version,
       current_setting('server_encoding') AS encoding,
       current_setting('TimeZone') AS timezone,
       pg_database_size(current_database()) AS database_bytes,
       current_setting('transaction_read_only') AS read_only;
SELECT ssl, version AS tls FROM pg_stat_ssl WHERE pid = pg_backend_pid();
SELECT name, default_version, installed_version
FROM pg_available_extensions WHERE name = 'btree_gist';
SELECT nspname, pg_get_userbyid(nspowner) AS owner, nspacl
FROM pg_namespace WHERE nspname = 'public';
SELECT r.rolname, r.rolsuper, r.rolcreatedb, r.rolcreaterole, r.rolbypassrls, r.rolreplication,
       COALESCE(pg_has_role(r.oid, n.oid, 'MEMBER'), false) AS neon_elevated_member,
       COALESCE(pg_has_role(r.oid, m.oid, 'MEMBER'), false) AS migrator_member,
       has_schema_privilege(r.oid, 'public', 'CREATE') AS public_schema_create
FROM pg_roles r
LEFT JOIN pg_roles n ON n.rolname = 'neon_superuser'
LEFT JOIN pg_roles m ON m.rolname = 'rf_migrator'
WHERE r.rolname IN ('rf_app', 'rf_migrator', 'rf_test')
ORDER BY r.rolname;
SELECT child.rolname AS member, parent.rolname AS role, x.admin_option,
       to_jsonb(x)->>'inherit_option' AS inherit_option,
       to_jsonb(x)->>'set_option' AS set_option
FROM pg_auth_members x
JOIN pg_roles child ON child.oid = x.member
JOIN pg_roles parent ON parent.oid = x.roleid
WHERE child.rolname IN ('rf_app', 'rf_migrator', 'rf_test')
ORDER BY 1, 2;

\if :expect_empty
SELECT NOT EXISTS (
    SELECT 1 FROM pg_class
    WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r', 'p', 'S', 'v', 'm', 'f')
) AS public_empty
\gset
\if :public_empty
\else
DO $$ BEGIN RAISE EXCEPTION 'PREFLIGHT_EMPTY_DESTINATION_REQUIRED'; END $$;
\endif
\else
SELECT installed_rank, version, type, script, checksum, success
FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT (COUNT(*) = 51 AND COUNT(DISTINCT version) = 51
        AND bool_and(success AND type = 'SQL' AND checksum IS NOT NULL)
        AND string_agg(version, ',' ORDER BY installed_rank) =
            (SELECT string_agg(v::text, ',' ORDER BY v) FROM generate_series(1, 51) v)
        AND EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist')) AS history_expected
FROM public.flyway_schema_history
\gset
\if :history_expected
\else
DO $$ BEGIN RAISE EXCEPTION 'PREFLIGHT_V1_V51_HISTORY_OR_EXTENSION_MISMATCH'; END $$;
\endif

SELECT c.relkind, count(*) AS objects
FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace
GROUP BY c.relkind ORDER BY c.relkind;
SELECT c.relname, c.relkind, pg_get_userbyid(c.relowner) AS owner, c.relacl,
       c.relrowsecurity, c.relforcerowsecurity
FROM pg_class c
WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r','p','S','v','m','f')
ORDER BY c.relname;
SELECT defaclrole::regrole, defaclobjtype, defaclacl
FROM pg_default_acl WHERE defaclnamespace IN (0, 'public'::regnamespace)
ORDER BY 1, 2;
SELECT c.conrelid::regclass AS relation, c.conname, c.contype, c.convalidated,
       md5(pg_get_constraintdef(c.oid)) AS definition_id
FROM pg_constraint c WHERE c.connamespace = 'public'::regnamespace ORDER BY 1, 2;
SELECT i.indexrelid::regclass AS index, a.amname AS access_method, i.indisvalid, i.indisready,
       md5(pg_get_indexdef(i.indexrelid)) AS definition_id
FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid JOIN pg_am a ON a.oid = c.relam
WHERE c.relnamespace = 'public'::regnamespace
ORDER BY 1;
SELECT tgrelid::regclass AS relation, tgname, tgenabled, md5(pg_get_triggerdef(oid)) AS definition_id
FROM pg_trigger WHERE NOT tgisinternal
AND tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace = 'public'::regnamespace) ORDER BY 1, 2;
SELECT p.oid::regprocedure AS function, pg_get_userbyid(p.proowner) AS owner, p.prosecdef,
       p.proacl, md5(pg_get_functiondef(p.oid)) AS definition_id,
       EXISTS (SELECT FROM aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
               WHERE a.grantee = 0 AND a.privilege_type = 'EXECUTE') AS public_execute
FROM pg_proc p WHERE p.pronamespace = 'public'::regnamespace AND p.prokind IN ('f','p') ORDER BY 1;

SELECT c.relname,
       has_table_privilege(r.oid, c.oid, 'SELECT') AS runtime_select,
       has_table_privilege(r.oid, c.oid, 'INSERT') AS runtime_insert,
       has_table_privilege(r.oid, c.oid, 'UPDATE') AS runtime_update,
       has_table_privilege(r.oid, c.oid, 'DELETE') AS runtime_delete,
       has_table_privilege(r.oid, c.oid, 'TRUNCATE') AS runtime_truncate,
       pg_has_role(r.oid, c.relowner, 'MEMBER') AS runtime_has_owner_membership
FROM pg_class c CROSS JOIN pg_roles r
WHERE c.relnamespace = 'public'::regnamespace AND r.rolname = 'rf_app'
  AND c.relname IN ('audit_event', 'flyway_schema_history', 'person_identity',
                    'person_identity_review_correction', 'retention_execution_authority',
                    'retention_personal_row_authority')
ORDER BY c.relname;
SELECT p.oid::regprocedure AS function,
       has_function_privilege(r.oid, p.oid, 'EXECUTE') AS runtime_execute
FROM pg_proc p CROSS JOIN pg_roles r
WHERE p.pronamespace = 'public'::regnamespace AND p.prosecdef AND r.rolname = 'rf_app'
ORDER BY 1;

SELECT role, active, email_verified_at IS NOT NULL AS verified, count(*)
FROM public.app_user GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;
SELECT status, count(*) AS requests, count(linked_user_id) AS linked_users, count(linked_driver_id) AS linked_drivers
FROM public.conductor_onboarding_request GROUP BY status ORDER BY status;
SELECT 'person_identity' AS relation, count(*) FROM public.person_identity
UNION ALL SELECT 'person_identity_review_correction', count(*) FROM public.person_identity_review_correction
UNION ALL SELECT 'admin_terms_acceptance', count(*) FROM public.admin_terms_acceptance
UNION ALL SELECT 'conductor_person_binding', count(*) FROM public.conductor_person_binding
UNION ALL SELECT 'driver_unit_period', count(*) FROM public.driver_unit_period
UNION ALL SELECT 'audit_event', count(*) FROM public.audit_event
UNION ALL SELECT 'report_emission', count(*) FROM public.report_emission;
SELECT count(*) AS linked_drivers_outside_tenant
FROM public.driver d LEFT JOIN public.app_user u ON u.id = d.user_id
WHERE d.user_id IS NOT NULL
AND (u.id IS NULL OR u.organization_id IS DISTINCT FROM d.organization_id OR u.role <> 'CONDUCTOR');
SELECT status, response_mode, count(*), count(accepted_at) AS accepted
FROM public.assignment GROUP BY 1, 2 ORDER BY 1, 2;
\endif

ROLLBACK;
