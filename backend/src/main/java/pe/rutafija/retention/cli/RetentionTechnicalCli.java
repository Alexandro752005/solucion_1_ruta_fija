package pe.rutafija.retention.cli;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.retention.application.RetentionProcedure;
import java.io.Console;
import java.time.Instant;
import java.util.*;

/** Explicit local maintenance entry. Public arguments contain only references/codes; secrets use Console. */
public final class RetentionTechnicalCli {
    private RetentionTechnicalCli() { }
    public static void main(String[] args){
        Console console=System.console();
        if(console==null||!"LOCAL_TEST_EXACT_PLAN".equals(System.getProperty("rutaFija.retention.authorized"))){
            System.out.println("RETENTION_PROCEDURE=REFUSED_INTERACTIVE_LOCAL_AUTHORIZATION_REQUIRED");return;
        }
        if(args.length==0){System.out.println("RETENTION_PROCEDURE=REFUSED_COMMAND_REQUIRED");return;}
        if(activeCase()){System.out.println("RETENTION_PROCEDURE=REFUSED_CASE_PROTECTION_ACTIVE");return;}
        String configuredUrl=System.getenv("RF_TEST_DATASOURCE_URL");
        if(configuredUrl==null||!configuredUrl.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/ruta_fija_test(\\?.*)?")
            ||!"rf_test".equals(System.getenv("RF_TEST_DATASOURCE_USERNAME"))){
            System.out.println("RETENTION_PROCEDURE=REFUSED_TEST_CONFIGURATION_REQUIRED");return;
        }
        try(var context=new SpringApplicationBuilder(RutaFijaApplication.class).profiles("test").run(
            "--spring.flyway.enabled=false","--app.seed.enabled=false",
            "--app.qualification.maintenance.enabled=false","--app.mobile.maintenance-enabled=false",
            "--app.security.admin-terms-gate.enabled=true","--app.security.person-identity-gate.enabled=true",
            "--app.retention.technical-enabled=true","--server.address=127.0.0.1","--server.port=0",
            "--spring.main.banner-mode=off","--logging.level.root=OFF","--logging.level.org.hibernate.SQL=OFF",
            "--logging.level.org.hibernate.orm.jdbc.bind=OFF","--logging.level.org.springframework.jdbc.core=OFF");
            var protection=context.getBean(javax.sql.DataSource.class).getConnection()){
            try(var q=protection.createStatement();var r=q.executeQuery("select pg_try_advisory_lock(7518440514)")){
                if(!r.next()||!r.getBoolean(1)||activeCase())throw new IllegalStateException();
            }
            try{
            var jdbc=context.getBean(JdbcTemplate.class);
            var db=jdbc.queryForMap("select current_database() db,current_user principal");
            if(!"ruta_fija_test".equals(db.get("db"))||!"rf_test".equals(db.get("principal")))throw new IllegalStateException();
            Integer failures=jdbc.queryForObject("select count(*) from flyway_schema_history where not success",Integer.class);
            if(failures==null||failures!=0||!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from flyway_schema_history where version='49' and success)",Boolean.class)))throw new IllegalStateException();
            char[] bearer=console.readPassword("Token de sesion local (entrada invisible; sin caracteres ni asteriscos): ");
            try{
                if(bearer==null||bearer.length==0)throw new IllegalStateException();
                var token=context.getBean(JwtDecoder.class).decode(new String(bearer));
                String role=token.getClaimAsString("role");
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
            }finally{if(bearer!=null)Arrays.fill(bearer,'\0');}
            var service=context.getBean(RetentionProcedure.class);
            switch(args[0]){
                case "request-plan" -> {
                    exact(args,7);
                    if(!"EXACT_SELECTION".equals(args[5]))throw new IllegalArgumentException();
                    var result=service.requestPlan(id(args[2]),RetentionProcedure.Category.valueOf(args[1]),ids(args[6]),id(args[3]),args[4],password(console));
                    plan(console,result);
                }
                case "request-platform-plan" -> {
                    exact(args,5);plan(console,service.requestPlatformAuditPlan(id(args[1]),ids(args[4]),id(args[2]),args[3],password(console)));
                }
                case "request-platform-recovery-plan" -> {
                    exact(args,5);plan(console,service.requestPlatformRecoveryPlan(id(args[1]),ids(args[4]),id(args[2]),args[3],password(console)));
                }
                case "delegate-personal-selection" -> {
                    exact(args,6);
                    console.printf("RETENTION_PERSONAL_DELEGATION_ID=%s%n",service.delegatePersonalSelection(id(args[1]),RetentionProcedure.Category.valueOf(args[2]),ids(args[5]),id(args[3]),args[4],password(console)));
                }
                case "request-delegated-personal-plan" -> {
                    exact(args,3);plan(console,service.requestDelegatedPersonalPlan(id(args[1]),id(args[2]),password(console)));
                }
                case "request-personal-hold" -> {
                    exact(args,7);var result=service.requestPersonalHold(id(args[1]),RetentionProcedure.Category.valueOf(args[2]),id(args[3]),id(args[4]),args[5],Instant.parse(args[6]),password(console));
                    console.printf("RETENTION_PERSONAL_HOLD_REQUEST_ID=%s%n",result);
                }
                case "approve-personal-hold" -> {
                    exact(args,2);
                    if(!"FINALIDAD_Y_VENCIMIENTO_EXACTOS_VERIFICADOS".equals(console.readLine("Confirmacion publica FINALIDAD_Y_VENCIMIENTO_EXACTOS_VERIFICADOS: ")))throw new IllegalArgumentException();
                    console.printf("RETENTION_PERSONAL_HOLD_APPROVED=%s%n",service.approvePersonalHold(id(args[1]),password(console)));
                }
                case "request-hold" -> {
                    exact(args,6);var result=service.requestHold(id(args[1]),id(args[2]),id(args[3]),args[4],Instant.parse(args[5]),password(console));
                    console.printf("RETENTION_HOLD_REQUEST_ID=%s%n",result);
                }
                case "approve-hold" -> {
                    exact(args,2);
                    if(!"CASO_ABIERTO_Y_JUSTIFICACION_VERIFICADOS".equals(console.readLine("Confirmacion publica CASO_ABIERTO_Y_JUSTIFICACION_VERIFICADOS: ")))throw new IllegalArgumentException();
                    console.printf("RETENTION_HOLD_APPROVED=%s%n",service.approveHold(id(args[1]),password(console)));
                }
                case "execute-plan" -> {
                    exact(args,3);
                    if(!"EJECUTAR_PLAN_EXACTO".equals(console.readLine("Confirmacion publica EJECUTAR_PLAN_EXACTO: ")))throw new IllegalArgumentException();
                    var result=service.execute(id(args[1]),args[2],password(console));
                    console.printf("RETENTION_PLAN_ID=%s%nRETENTION_RETIRED_COUNT=%d%nRETENTION_EXECUTED_AT=%s%nRETENTION_REPLAY=%s%n",result.planId(),result.retiredCount(),result.executedAt(),result.replay());
                }
                case "register-synthetic-copy" -> {
                    exact(args,5);console.printf("RETENTION_SYNTHETIC_COPY_ID=%s%n",service.registerSyntheticCopy(id(args[1]),RetentionProcedure.Category.valueOf(args[2]),id(args[3]),id(args[4]),password(console)));
                }
                case "attest-synthetic-copy" -> {
                    exact(args,4);console.printf("RETENTION_SYNTHETIC_COPY_ATTESTATION=%s%n",service.attestSyntheticCopy(id(args[1]),args[2],id(args[3]),password(console)));
                }
                case "check-synthetic-copy" -> {
                    exact(args,2);console.printf("RETENTION_COPY_POLICY_READY_ONLY=%s%n",service.syntheticRestoreReady(id(args[1])));
                }
                default -> throw new IllegalArgumentException();
            }
            }finally{try(var q=protection.createStatement()){q.execute("select pg_advisory_unlock(7518440514)");}}
        }catch(Exception failure){System.out.println("RETENTION_PROCEDURE=REFUSED_STATE_PRESERVED_DETAILS_REDACTED");}
        finally{SecurityContextHolder.clearContext();}
    }
    private static void plan(Console console,RetentionProcedure.Plan plan){console.printf("RETENTION_PLAN_ID=%s%nRETENTION_SELECTION_SHA256=%s%nRETENTION_ITEM_COUNT=%d%nRETENTION_POLICY_VERSION=%s%n",plan.id(),plan.selectionSha256(),plan.itemCount(),plan.policyVersion());}
    private static char[] password(Console console){return console.readPassword("Contrasena actual para reautenticacion (entrada invisible; sin caracteres ni asteriscos): ");}
    private static UUID id(String value){return UUID.fromString(value);}
    private static List<UUID> ids(String value){return Arrays.stream(value.split(",",-1)).map(UUID::fromString).toList();}
    private static void exact(String[] values,int size){if(values.length!=size)throw new IllegalArgumentException();}
    private static boolean activeCase(){
        for(String name:List.of(".local","backend/.local")){
            var folder=java.nio.file.Path.of(name);if(!java.nio.file.Files.isDirectory(folder))continue;
            try(var paths=java.nio.file.Files.list(folder)){if(paths.anyMatch(p->p.getFileName().toString().endsWith(".active")))return true;}
            catch(java.io.IOException failure){return true;}
        }return false;
    }
}
