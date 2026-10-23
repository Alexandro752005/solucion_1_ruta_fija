package pe.rutafija.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.rutafija.backup.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
/** Read-only snapshot proof: original hashes retained; no database rewrite or repeated restore. */
public final class L08RestoredConstraintCollationProof {
 public static void main(String[] args){String phase="ARGUMENTS";byte[] key=null,plain=null;try{
  if(!"NEW_LOCALE_MATCHED_DATABASE_55438".equals(System.getenv("RF_L08_RESTORE_AUTHORIZATION"))||!"jdbc:postgresql://127.0.0.1:55438/ruta_fija_test_l08_locale".equals(System.getenv("RF_L08_RESTORE_URL")))throw new IllegalStateException();
  Path root=L08AvailabilityRehearsal.repositoryRoot(),folder=root.resolve("backend/.local/l08-restore-c748e5ab-c20a-43d6-9c17-af5d0a703dab");var json=new ObjectMapper();
  var manifest=json.readTree(Files.readAllBytes(folder.resolve("l08-locale-isolated-restore.json")));
  if(!"c748e5ab-c20a-43d6-9c17-af5d0a703dab".equals(manifest.path("caseId").asText()))throw new IllegalStateException();
  phase="REAUTHENTICATE_ORIGINAL_ARCHIVE";Path archive=folder.resolve("ruta_fija_test.rfdb");
  if(!L08AvailabilityRehearsal.sha256(archive).equals(manifest.path("archiveSha256").asText()))throw new IllegalStateException();
  key=Base64.getDecoder().decode(System.getenv("RF_L08_BACKUP_KEY_BASE64"));try(var input=Files.newInputStream(archive)){plain=new AuthenticatedBackupArchive().authenticatedPostgresBytes(input,key,64*1024*1024);}
  try(var c=DriverManager.getConnection(System.getenv("RF_L08_RESTORE_URL"),"rf_migrator",System.getenv("RF_L08_RESTORE_PASSWORD"))){
   c.setReadOnly(true);phase="EXACT_APPROVED_REGIONAL_TARGET";
   try(var q=c.createStatement();var r=q.executeQuery("select current_database(),current_user,inet_server_port(),datcollate,datctype from pg_database where datname=current_database()")){
    if(!r.next()||!"ruta_fija_test_l08_locale".equals(r.getString(1))||!"rf_migrator".equals(r.getString(2))||r.getInt(3)!=55438||!"Spanish_Peru.1252".equals(r.getString(4))||!"Spanish_Peru.1252".equals(r.getString(5)))throw new IllegalStateException();}
   phase="CAPTURE_UNMODIFIED_RAW_INTEGRITY";var actual=DatabaseIntegrity.capture(c);var raw=json.readTree(json.writeValueAsBytes(actual));var differences=new ArrayList<String>();
   manifest.path("sourceIntegrity").fieldNames().forEachRemaining(name->{if(!manifest.path("sourceIntegrity").path(name).equals(raw.path(name)))differences.add(name);});
   if(!new HashSet<>(differences).equals(Set.of("schema:constraints","schema:indexes","schema:acl")))throw new IllegalStateException();
   var rewrites=new LinkedHashMap<String,Integer>();Integer v46CategorySpellingMask=null;
   try(var q=c.createStatement();var row=q.executeQuery("select distinct pg_collation_for(n.nspname||'.'||t.relname||'|'||p.conname||'|'||pg_get_constraintdef(p.oid,true)) from pg_constraint p join pg_class t on t.oid=p.conrelid join pg_namespace n on n.oid=t.relnamespace where n.nspname='public'")){if(!row.next()||!"\"C\"".equals(row.getString(1))||row.next())throw new IllegalStateException("SOURCE_EXPRESSION_COLLATION_NOT_CONFIRMED");}
   Path diagnostic=root.resolve("docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/diferencias-esquema-staticas.json");
   var contrast=json.readTree(Files.readAllBytes(diagnostic));var constraintSpellings=new HashMap<String,String>();
   if(!"50".equals(contrast.path("sourceVersion").asText())||!"48".equals(contrast.path("restoredVersion").asText()))throw new IllegalStateException();
   for(var item:contrast.path("constraints").path("commonTargetDifferences"))constraintSpellings.put(item.path("schemaObject").asText(),item.path("restored").asText());
   for(String kind:List.of("constraints","indexes")){
    phase="RECONCILE_"+kind.toUpperCase(Locale.ROOT);String sql=kind.equals("indexes")?"select indexname||'|'||indexdef from pg_indexes where schemaname='public'":"select n.nspname||'.'||t.relname||'|'||p.conname||'|'||pg_get_constraintdef(p.oid,true) from pg_constraint p join pg_class t on t.oid=p.conrelid join pg_namespace n on n.oid=t.relnamespace where n.nspname='public'";
    var definitions=new ArrayList<String>();var v46Spellings=new TreeMap<Integer,String>();int changed=0;
    try(var q=c.createStatement();var r=q.executeQuery(sql)){while(r.next()){String original=r.getString(1);boolean selected=kind.equals("indexes");
     if(!selected){String[] pieces=original.split("\\|",3);String id=pieces[0].substring("public.".length())+"."+pieces[1];
      selected=constraintSpellings.containsKey(id);if(selected&&!constraintSpellings.get(id).equals(pieces[2]))throw new IllegalStateException();
      if(Set.of("retention_plan.retention_plan_category_check","retention_controlled_copy.retention_controlled_copy_source_category_check").contains(id))v46Spellings.put(definitions.size(),original);}
     String spelling=selected?SqlLiteralArrayCast.sourceSpelling(original,kind.equals("indexes")):original;definitions.add(spelling);if(!original.equals(spelling))changed++;}}
    var fingerprint=aggregate(c,definitions,kind.equals("constraints"));
    if(kind.equals("constraints")){
     // V49 replaced exactly these two V46 CHECK definitions. Their prior cast
     // spelling is not inferable from current V50. Retain every value/token and
     // test only the four reversible spelling combinations against the ORIGINAL
     // snapshot hash and count; no omitted constraint or relaxed comparison.
     if(v46Spellings.size()!=2)throw new IllegalStateException();
     for(int mask=0;mask<4;mask++){
      var candidate=new ArrayList<>(definitions);int bit=0;for(var entry:v46Spellings.entrySet()){
       if((mask&(1<<bit))!=0)candidate.set(entry.getKey(),entry.getValue());bit++;}
      var candidateFingerprint=aggregate(c,candidate,true);
      if(json.readTree(json.writeValueAsBytes(candidateFingerprint)).equals(manifest.path("sourceIntegrity").path("schema:constraints"))){fingerprint=candidateFingerprint;v46CategorySpellingMask=mask;break;}
     }
    }
    actual.put("schema:"+kind,fingerprint);rewrites.put(kind,changed);
   }
   phase="VERIFY_PRIVATE_AUTHORITY_OWNER_IMPLICIT_PRIVILEGES";
   try(var q=c.createStatement();var r=q.executeQuery("select pg_get_userbyid(relowner),relacl is null from pg_class where oid='public.retention_execution_authority'::regclass")){if(!r.next()||!"rf_migrator".equals(r.getString(1))||!r.getBoolean(2))throw new IllegalStateException();}
   for(String role:List.of("rf_migrator","rf_app","rf_test"))for(String privilege:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE","REFERENCES","TRIGGER")){
    try(var q=c.prepareStatement("select has_table_privilege(?, 'public.retention_execution_authority', ?)")){q.setString(1,role);q.setString(2,privilege);try(var row=q.executeQuery()){if(!row.next()||row.getBoolean(1)!=role.equals("rf_migrator"))throw new IllegalStateException();}}
   }
   phase="COMPARE_ORIGINAL_ACL_HASH_WITH_OWNER_DEFAULT_EXPANSION";
   String acl="select identity||'|'||coalesce(grantee.rolname,'PUBLIC')||'|'||privilege_type||'|'||is_grantable from (select 'table:'||t.relname identity,a.grantee,a.privilege_type,a.is_grantable from pg_class t join pg_namespace n on n.oid=t.relnamespace cross join lateral aclexplode(coalesce(t.relacl,case when t.relname='retention_execution_authority' then acldefault('r',t.relowner) else null end))a where n.nspname='public' union all select 'function:'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',a.grantee,a.privilege_type,a.is_grantable from pg_proc p join pg_namespace n on n.oid=p.pronamespace cross join lateral aclexplode(p.proacl)a where n.nspname='public') permissions left join pg_roles grantee on grantee.oid=permissions.grantee";
   var entries=new ArrayList<String>();try(var q=c.createStatement();var r=q.executeQuery(acl)){while(r.next())entries.add(r.getString(1));}actual.put("schema:acl",aggregate(c,entries));
   phase="ALL_ORIGINAL_SNAPSHOT_HASHES_STILL_REQUIRED";
   if(!json.readTree(json.writeValueAsBytes(actual)).equals(manifest.path("sourceIntegrity"))){
    var fingerprints=new LinkedHashMap<String,Object>();for(String name:List.of("schema:constraints","schema:indexes","schema:acl"))fingerprints.put(name,Map.of("actual",actual.get(name),"expected",manifest.path("sourceIntegrity").path(name)));
    System.out.println("L08_RECONCILIATION_PUBLIC_FINGERPRINTS="+json.writeValueAsString(fingerprints));throw new IllegalStateException();
   }
   var evidence=new LinkedHashMap<String,Object>();evidence.put("result","PASS_READ_ONLY_RECONCILED_SQL_SNAPSHOT_INTEGRITY_NOT_SERVICE_RTO");evidence.put("caseId",manifest.path("caseId").asText());evidence.put("observedAt",Instant.now().toString());
   evidence.put("originalArchiveSha256",manifest.path("archiveSha256").asText());evidence.put("gcmTagReverified",true);evidence.put("rawMismatchCategoriesPreserved",differences);evidence.put("literalArraySpellingCounts",rewrites);
   evidence.put("allOriginalTableAndSchemaCountsAndHashesMatchAfterExplicitReversibleSpelling",true);evidence.put("privateAuthorityPermissionsExplicitlyChecked",true);evidence.put("ownerImplicitPrivilegesExpandedOnlyFor","retention_execution_authority");
   evidence.put("staticContrastSha256",L08AvailabilityRehearsal.sha256(diagnostic));evidence.put("allOtherConstraintSpellingsPreserved",true); evidence.put("constraintHashOrderingCollation","C"); evidence.put("constraintOrderingCollationReadFromOriginalSqlExpression",true);
   evidence.put("v46TwoCategoryCheckSpellingMask",v46CategorySpellingMask);evidence.put("v46SpellingVariantsMaximum",4);
   evidence.put("databaseWrites",0);evidence.put("restoreRepeated",false);evidence.put("source5432Accessed",false);evidence.put("serviceRtoAccredited",false);evidence.put("independentBackupAccredited",false);
   Path output=root.resolve("docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/reconciliacion-snapshot-collation.json");L08AvailabilityRehearsal.writeNewJson(output,evidence);
   System.out.println("L08_RESTORED_SNAPSHOT="+evidence.get("result"));
  }
 }catch(Exception failure){System.out.println("L08_RESTORED_SNAPSHOT=STOPPED_KEEP_RAW_DIFFERENCES_AND_BOTH_DATABASES");System.out.println("L08_RESTORED_SNAPSHOT_PHASE="+phase);System.out.println("L08_RESTORED_SNAPSHOT_TYPE="+failure.getClass().getSimpleName());System.exit(1);}finally{if(key!=null)Arrays.fill(key,(byte)0);if(plain!=null)Arrays.fill(plain,(byte)0);}}
 static Map<String,Object> aggregate(Connection c,List<String> values)throws SQLException{return aggregate(c,values,false);}
 static Map<String,Object> aggregate(Connection c,List<String> values,boolean sourceConstraints)throws SQLException{
  try(var q=c.prepareStatement("select count(*),encode(sha256(convert_to(coalesce(string_agg(x,E'\\n' order by x"+(sourceConstraints?" COLLATE \"C\"":"")+"),''),'UTF8')),'hex') from unnest(?::text[])x")){
   var array=c.createArrayOf("text",values.toArray(String[]::new));try{q.setArray(1,array);try(var r=q.executeQuery()){r.next();return Map.of("count",r.getLong(1),"sha256",r.getString(2));}}finally{array.free();}
  }
 }
}
