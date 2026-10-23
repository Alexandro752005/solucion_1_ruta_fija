package pe.rutafija.backup;

import java.sql.*;
import java.util.*;

/** Comparable logical hashes only. No row body, secret, query result text or connection string is emitted. */
public final class DatabaseIntegrity {
    private DatabaseIntegrity() { }
    public static Map<String,Object> capture(Connection connection)throws SQLException{
        var result=new TreeMap<String,Object>();
        try(var statement=connection.createStatement()){
            statement.execute("set time zone 'UTC'");
            List<String> tables=new ArrayList<>();
            try(var rows=statement.executeQuery("select tablename from pg_tables where schemaname='public' order by tablename")){
                while(rows.next())tables.add(rows.getString(1));
            }
            for(String table:tables){
                if(!table.matches("[a-z_][a-z0-9_]*"))throw new SQLException("UNSUPPORTED_TABLE_IDENTIFIER");
                try(var rows=statement.executeQuery("select count(*),encode(sha256(convert_to(coalesce(string_agg(h,',' order by h),''),'UTF8')),'hex') from (select encode(sha256(convert_to(to_jsonb(t)::text,'UTF8')),'hex') h from public.\""+table+"\" t) x")){
                    rows.next();result.put(table,Map.of("rows",rows.getLong(1),"rowSetSha256",rows.getString(2)));
                }
            }
            for(var item:Map.of(
                "constraints","select n.nspname||'.'||c.relname||'|'||con.conname||'|'||pg_get_constraintdef(con.oid,true) definition from pg_constraint con join pg_class c on c.oid=con.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public'",
                "triggers","select c.relname||'|'||t.tgname||'|'||pg_get_triggerdef(t.oid,true)||'|'||t.tgenabled::text definition from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and not t.tgisinternal",
                "indexes","select indexname||'|'||indexdef definition from pg_indexes where schemaname='public'",
                "columns","select c.relname||'|'||a.attname||'|'||format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||a.attidentity::text||'|'||a.attgenerated::text||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') definition from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where n.nspname='public' and a.attnum>0 and not a.attisdropped",
                "functions","select p.proname||'|'||pg_get_function_identity_arguments(p.oid)||'|'||pg_get_functiondef(p.oid) definition from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.prokind in('f','p')",
                "sequenceState","select schemaname||'.'||sequencename||'|'||start_value||'|'||min_value||'|'||max_value||'|'||increment_by||'|'||cycle||'|'||cache_size||'|'||coalesce(last_value::text,'NULL') definition from pg_sequences where schemaname='public'",
                "acl","select identity||'|'||coalesce(grantee.rolname,'PUBLIC')||'|'||privilege_type||'|'||is_grantable definition from (select 'table:'||c.relname identity,acl.grantee,acl.privilege_type,acl.is_grantable from pg_class c join pg_namespace n on n.oid=c.relnamespace cross join lateral aclexplode(c.relacl) acl where n.nspname='public' union all select 'function:'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',acl.grantee,acl.privilege_type,acl.is_grantable from pg_proc p join pg_namespace n on n.oid=p.pronamespace cross join lateral aclexplode(p.proacl) acl where n.nspname='public') permissions left join pg_roles grantee on grantee.oid=permissions.grantee"
            ).entrySet()){
                try(var rows=statement.executeQuery("select count(*),encode(sha256(convert_to(coalesce(string_agg(definition,E'\\n' order by definition),''),'UTF8')),'hex') from ("+item.getValue()+") definitions")){
                    rows.next();result.put("schema:"+item.getKey(),Map.of("count",rows.getLong(1),"sha256",rows.getString(2)));
                }
            }
        }
        return result;
    }
    public static List<String> referencedAclRoles(Connection connection)throws SQLException{
        var roles=new TreeSet<String>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery("select distinct r.rolname from (select a.grantee from pg_class c join pg_namespace n on n.oid=c.relnamespace cross join lateral aclexplode(c.relacl) a where n.nspname='public' union select a.grantee from pg_proc p join pg_namespace n on n.oid=p.pronamespace cross join lateral aclexplode(p.proacl) a where n.nspname='public') permissions join pg_roles r on r.oid=permissions.grantee")){
            while(rows.next())roles.add(rows.getString(1));
        }
        if(!Set.of("rf_app","rf_test","rf_migrator").containsAll(roles))throw new SQLException("UNREVIEWED_SOURCE_ACL_ROLE");
        return List.copyOf(roles);
    }
}
