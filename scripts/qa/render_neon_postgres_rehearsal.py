"""Exercise the deploy candidate using only a newly initialized PostgreSQL 16 cluster."""

import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid


HOST = "127.0.0.1"
PORT = 55441  # Existing isolated Java test guard accepts only this alternate port.
ADMIN = "rf_audit_cluster_admin"
SOURCE = "rf_restore_fixture"
TARGET = "rf_restore_trial"
READER = "rf_inspector"


def command(label, args, env, log, *, cwd=None, sql=None):
    result = subprocess.run(args, env=env, cwd=cwd, input=sql, encoding="utf-8", errors="replace",
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=900)
    log.write_text(result.stdout, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"{label} failed (exit {result.returncode})")
    return result.stdout


def isolated_env():
    keys = ("SystemRoot", "WINDIR", "COMSPEC", "PATH", "PATHEXT", "USERPROFILE",
            "LOCALAPPDATA", "APPDATA", "ProgramFiles", "ProgramData", "TEMP", "TMP",
            "HOME", "SHELL", "NUMBER_OF_PROCESSORS")
    return {key: os.environ[key] for key in keys if key in os.environ}


def free_port(port=0):
    with socket.socket() as server:
        server.bind((HOST, port))
        return server.getsockname()[1]


def wait_health(process, port):
    for _ in range(120):
        if process.poll() is not None:
            raise RuntimeError("Backend process exited before readiness")
        try:
            for group in ("liveness", "readiness"):
                with urlopen(f"http://{HOST}:{port}/actuator/health/{group}", timeout=2) as result:
                    if json.load(result)["status"] != "UP":
                        raise ValueError("Health is not UP")
            return
        except (URLError, ValueError, OSError):
            time.sleep(1)
    raise RuntimeError("Backend did not report liveness and readiness UP")


def run(args):
    root = args.project.resolve(strict=True)
    if any((root / name).exists() for name in (".git", ".env", "backend/.local")):
        raise RuntimeError("Provide a clean exported project, without Git or private configuration")

    free_port(PORT)
    work = root / ".runtime" / "auditoria-integral-2026-11-13" / "postgresql"
    work.mkdir(parents=True, exist_ok=False)
    data = work / "data"
    run_id = str(uuid.uuid4())
    password = {role: secrets.token_hex(24) for role in (ADMIN, "rf_migrator", "rf_app", "rf_test", READER)}
    env = isolated_env()
    env.update(JAVA_HOME=str(args.java.parent.parent), JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8",
               PGCLIENTENCODING="UTF8")

    def pg(name):
        return str(args.pg_bin / (name + (".exe" if os.name == "nt" else "")))

    def control(action, options):
        # On Windows the postmaster can inherit a pipe even after pg_ctl exits.
        with (work / (f"pg_ctl-{action}.log")).open("wb") as log:
            subprocess.run([pg("pg_ctl"), "-D", str(data), action, *options],
                           env=env, stdout=log, stderr=subprocess.STDOUT, timeout=45, check=True)

    def sql(label, database, role, statement):
        return command(label, [pg("psql"), "-X", "-A", "-t", "-q", "-v", "ON_ERROR_STOP=1",
                               "-h", HOST, "-p", str(PORT), "-U", role, "-d", database],
                       env | {"PGPASSWORD": password[role]}, work / (label + ".log"),
                       sql=statement).strip()

    def verify(database, role):
        observed = sql("target-" + database + "-" + role, database, role,
                       "select current_database(), current_user, inet_server_port(), "
                       "current_setting('data_directory'), current_setting('server_version_num');").split("|")
        if len(observed) != 5 or observed[:3] != [database, role, str(PORT)] \
                or Path(observed[3]).resolve(strict=True) != data.resolve(strict=True) \
                or not observed[4].startswith("16"):
            raise RuntimeError("Disposable database identity did not match")

    def backend(database, role, profiles, label, inspect=False):
        verify(database, role)
        port = free_port()
        process_env = env | {
            "SPRING_PROFILES_ACTIVE": profiles, "PORT": str(port), "SERVER_ADDRESS": HOST,
            "SPRING_DATASOURCE_URL": f"jdbc:postgresql://{HOST}:{PORT}/{database}",
            "SPRING_DATASOURCE_USERNAME": role, "SPRING_DATASOURCE_PASSWORD": password[role],
            "JWT_SECRET_BASE64": base64.b64encode(secrets.token_bytes(32)).decode(),
            "APP_CORS_ALLOWED_ORIGINS": "https://crm.rutafija.test", "APP_SEED_ENABLED": "false",
            "MOBILE_PENDING_EXPIRY_SWEEP": "PT15S",
            "APP_QUALIFICATION_VALIDITY_SWEEP": "PT0.2S",
        }
        if inspect:
            process_env.update(PORT="0", SERVER_PORT=str(port))
        jar, = (root / "backend/target").glob("ruta-fija-backend-*.jar")
        with (work / (label + ".log")).open("wb") as log:
            process = subprocess.Popen([str(args.java), "-jar", str(jar)], cwd=root / "backend",
                                       env=process_env, stdout=log, stderr=subprocess.STDOUT)
            try:
                wait_health(process, port)
                if inspect:
                    for method, path in (("GET", "/api/v1/assignments"), ("POST", "/api/v1/auth/login"),
                                         ("GET", "/ws/operations"), ("GET", "/actuator/info"),
                                         ("POST", "/logout")):
                        try:
                            with urlopen(Request(f"http://{HOST}:{port}{path}", method=method), timeout=5):
                                raise RuntimeError("Unexpectedly exposed business endpoint")
                        except HTTPError as error:
                            if error.code != 403:
                                raise RuntimeError(f"Unexpected inspection status: {error.code}") from None
                    time.sleep(16)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()

    def preflight(database, phase):
        verify(database, "rf_migrator")
        return command("preflight", [pg("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1", "-h", HOST,
                                     "-p", str(PORT), "-U", "rf_migrator", "-d", database,
                                     "-v", f"expected_database={database}", "-v", f"phase={phase}",
                                     "-f", str(root / "scripts/sql/RenderNeonPreflight.sql")],
                       env | {"PGPASSWORD": password["rf_migrator"]},
                       work / (f"preflight-{database}-{phase}.log"))

    pwfile = work / "init.pw"
    pwfile.write_text(password[ADMIN] + "\n", encoding="ascii")
    try:
        command("initdb", [pg("initdb"), "-D", str(data), "-U", ADMIN, "--auth=scram-sha-256",
                           "--encoding=UTF8", "--locale=C", "--pwfile", str(pwfile)],
                env, work / "initdb.log")
    finally:
        pwfile.unlink()
    with (data / "postgresql.conf").open("a", encoding="ascii") as config:
        config.write(f"\nlisten_addresses = '{HOST}'\nport = {PORT}\nlog_line_prefix = '%d|%u| '\n")

    marker = {"schemaVersion": 1, "port": PORT, "runId": run_id, "database": "ruta_fija_test",
              "admin": ADMIN, "migrator": "rf_migrator", "testUser": "rf_test", "appUser": "rf_app",
              "dataDirectory": str(data.resolve(strict=True))}
    (work / "connection.json").write_text(json.dumps(marker), encoding="utf-8")
    started = False
    try:
        free_port(PORT)
        control("start", ["-l", str(work / "postgresql.log"), "-w", "-t", "30"])
        started = True
        verify("postgres", ADMIN)
        for role in ("rf_migrator", "rf_test", "rf_app", READER):
            # Values are generated for this cluster only. Never retain SQL or stderr from this statement.
            result = subprocess.run([pg("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1", "-h", HOST, "-p", str(PORT),
                                     "-U", ADMIN, "-d", "postgres"], env=env | {"PGPASSWORD": password[ADMIN]},
                                    input=f"CREATE ROLE {role} LOGIN PASSWORD '{password[role]}';",
                                    text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
            if result.returncode:
                raise RuntimeError("Could not create disposable role")
        sql("fixture-roles", "postgres", ADMIN,
            "GRANT pg_read_all_settings TO rf_migrator, rf_app, rf_test, rf_inspector;\n"
            "GRANT pg_read_all_stats TO rf_migrator;\n"
            "ALTER ROLE rf_inspector SET log_statement = 'mod';")
        for database in ("ruta_fija_test", SOURCE, TARGET):
            sql("create-" + database, "postgres", ADMIN, f"CREATE DATABASE {database} OWNER rf_migrator;")
        print("PASS disposable PostgreSQL identity", flush=True)

        preflight(TARGET, "empty")
        backend(SOURCE, "rf_migrator", "prod", "bootstrap-synthetic-v51")
        sql("synthetic-account", SOURCE, "rf_migrator", """
            INSERT INTO organization (id, legal_name, status, created_at, updated_at)
            VALUES ('10000000-0000-0000-0000-000000000001', 'Synthetic Restore', 'ACTIVE', now(), now());
            INSERT INTO app_user (id, organization_id, email, password_hash, full_name, role,
                                  active, created_at, updated_at, email_verified_at)
            VALUES ('10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
                    'synthetic.restore@example.test', 'synthetic-not-a-password', 'Synthetic Restore',
                    'ADMIN', true, now(), now(), now());
            REVOKE ALL ON TABLE audit_event FROM PUBLIC;
            GRANT SELECT, INSERT ON TABLE audit_event TO rf_app;
        """)
        preflight(SOURCE, "baseline")
        verify(SOURCE, "rf_migrator")
        archive = work / "synthetic.dump"
        endpoint = ["-h", HOST, "-p", str(PORT), "-U", "rf_migrator"]
        pg_env = env | {"PGPASSWORD": password["rf_migrator"]}
        command("pg_dump", [pg("pg_dump"), *endpoint, "-Fc", "-f", str(archive), SOURCE],
                pg_env, work / "dump.log")
        verify(TARGET, "rf_migrator")
        command("pg_restore", [pg("pg_restore"), *endpoint, "--single-transaction", "--exit-on-error",
                               "--no-owner", "--no-acl", "-d", TARGET, str(archive)],
                pg_env, work / "restore.log")

        toc = command("pg_restore list", [pg("pg_restore"), "--list", str(archive)], pg_env, work / "restore-list.log")
        acl_list = work / "acl-only.list"
        acl_entries = [line for line in toc.splitlines()
                       if re.match(r"^\d+;\s+\d+\s+\d+\s+(?:DEFAULT ACL|ACL)\s", line)]
        if not acl_entries:
            raise RuntimeError("Expected protected ACL entries in the synthetic archive")
        acl_list.write_text("\n".join(acl_entries) + "\n", encoding="utf-8")
        for pass_number in (1, 2):
            verify(TARGET, "rf_migrator")
            command("ACL replay", [pg("pg_restore"), *endpoint, "--single-transaction", "--exit-on-error",
                                   "--schema-only", "--no-owner", "--use-list", str(acl_list),
                                   "-d", TARGET, str(archive)], pg_env, work / f"acl-replay-{pass_number}.log")
        preflight(TARGET, "restored")

        history = "SELECT installed_rank, version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"
        if sql("source-history", SOURCE, "rf_migrator", history) != sql("target-history", TARGET, "rf_migrator", history):
            raise RuntimeError("Flyway history changed after synthetic restore")
        accounts = ("SELECT u.id, o.id, u.role, u.active, u.email_verified_at IS NOT NULL "
                    "FROM app_user u JOIN organization o ON o.id = u.organization_id ORDER BY u.id;")
        source_accounts = sql("source-accounts", SOURCE, "rf_migrator", accounts)
        if not source_accounts.endswith("|ADMIN|t|t") or source_accounts != sql("target-accounts", TARGET, "rf_migrator", accounts):
            raise RuntimeError("Synthetic account or its organization changed after restore")

        signature = ("public.correct_unprovisioned_conductor_identity"
                     "(uuid,uuid,uuid,uuid,uuid,text,text,text,text,text,text,text,timestamp with time zone)")
        sql("trial-permissions", TARGET, "rf_migrator",
            "GRANT USAGE ON SCHEMA public TO rf_inspector;\n"
            "GRANT SELECT ON ALL TABLES IN SCHEMA public TO rf_inspector;")
        result = sql("trial-effective-permissions", TARGET, "rf_migrator",
                     f"SELECT has_function_privilege('rf_app', '{signature}', 'EXECUTE'), "
                     f"has_function_privilege('rf_inspector', '{signature}', 'EXECUTE'), "
                     "has_table_privilege('rf_app', 'person_identity_review_correction', 'INSERT'), "
                     "has_table_privilege('rf_inspector', 'audit_event', 'INSERT'), "
                     "has_table_privilege('rf_app', 'audit_event', 'INSERT'), "
                     "has_table_privilege('rf_app', 'audit_event', 'UPDATE'), "
                     "has_table_privilege('rf_app', 'retention_execution_authority', 'SELECT'), "
                     "has_table_privilege('rf_app', 'retention_personal_row_authority', 'SELECT'), "
                     "has_table_privilege('rf_app', 'flyway_schema_history', 'UPDATE'), "
                     "pg_has_role('rf_app', 'rf_migrator', 'MEMBER');")
        if result != "t|f|f|f|t|f|f|f|f|f":
            raise RuntimeError("Protected correction privilege check failed")
        print("PASS synthetic account and V1–V51 history, with repeatable ACL replay and protected privileges", flush=True)

        if sql("trial-inspector-log-policy", TARGET, READER, "SHOW log_statement;") != "mod":
            raise RuntimeError("Mutation observation was not enabled for the synthetic inspector")
        backend(TARGET, READER, "prod,restore-inspection", "restore-inspection", inspect=True)
        log = (work / "postgresql.log").read_text(encoding="utf-8", errors="replace")
        if re.search(r"\|rf_inspector\|.*statement:", log):
            raise RuntimeError("Mutation statement observed during read-only inspection")
        print("PASS restore inspection liveness, readiness, denied HTTP and no scheduled DML", flush=True)

        if args.maven:
            verify("ruta_fija_test", "rf_migrator")
            url = f"jdbc:postgresql://{HOST}:{PORT}/ruta_fija_test"
            test_env = env | {
                "RF_AUDIT_ISOLATED_MODE": "EXCLUSIVE_AUDIT_55441",
                "RF_AUDIT_ISOLATED_DATA_DIRECTORY": str(data.resolve(strict=True)),
                "RF_AUDIT_JWT_SECRET_BASE64": base64.b64encode(secrets.token_bytes(32)).decode(),
                "RF_AUDIT_ISOLATED_RUN_ID": run_id, "RF_AUDIT_NATIVE_SCHEMA_RESET": "REBUILD_OWNED_DISPOSABLE_SCHEMA_55441",
                "RF_TEST_DATASOURCE_URL": url, "RF_TEST_DATASOURCE_USERNAME": "rf_test",
                "RF_TEST_DATASOURCE_PASSWORD": password["rf_test"],
                "RF_TEST_MIGRATOR_URL": url, "RF_TEST_MIGRATOR_USERNAME": "rf_migrator",
                "RF_TEST_MIGRATOR_PASSWORD": password["rf_migrator"],
            }
            settings = work / "settings.xml"
            settings.write_text("<settings/>", encoding="ascii")
            command("integration", [str(args.maven), "-B", "-ntp", "-o", "-s", str(settings),
                                    "-Dtest=ReportExportObservabilityTest",
                                    "-Dit.test=AuthFlowIT,MobileAuthFlowIT,MobileContractReportIT,PersonIdentityFlowIT",
                                    "verify"], test_env, work / "integration.log", cwd=root / "backend")
            print("PASS targeted Maven integration", flush=True)
    finally:
        if started:
            control("stop", ["-m", "fast", "-w", "-t", "30"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True, help="Clean exported and packaged project")
    parser.add_argument("--pg-bin", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--maven", type=Path, help="Also run the isolated Maven integration selection")
    run(parser.parse_args())
