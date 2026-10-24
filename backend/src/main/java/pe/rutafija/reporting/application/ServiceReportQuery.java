package pe.rutafija.reporting.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.rutafija.reporting.api.ServiceReportContract.*;
import pe.rutafija.shared.exception.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Cohorts use the first immutable offer, never the rescheduled service header. */
@Component
public class ServiceReportQuery {
    public static final String RULE="REP-L07-01-v1";
    private final JdbcTemplate jdbc;
    public ServiceReportQuery(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public static void range(LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>=90)
            throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.REPORT_RANGE_REQUIRED,"Indica un periodo de uno a90 días.");
    }
    public View read(UUID organization,UUID ownDriver,LocalDate from,LocalDate to,String zone,Instant cutoff){
        range(from,to);Instant begin=from.atStartOfDay(ZoneId.of(zone)).toInstant(),end=to.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant();
        String own=ownDriver==null?"":" and (exists(select 1 from assignment a where a.service_id=s.id and a.driver_id=? and a.accepted_at is not null) or exists(select 1 from service_execution_segment e where e.service_id=s.id and e.driver_id=?))";
        var args=new ArrayList<Object>(List.of(organization,ts(cutoff),ts(begin),ts(end)));
        if(ownDriver!=null){args.add(ownDriver);args.add(ownDriver);}
        var headers=jdbc.query("""
            select s.id,s.version,s.status,s.scheduled_at,f.scheduled_at cohort_at,current_offer.response_deadline_at
             from scheduled_service s join lateral(select a.scheduled_at from assignment a where a.service_id=s.id
              order by a.created_at,a.id limit 1) f on true
             left join assignment current_offer on current_offer.id=s.current_offer_id
            where s.organization_id=? and s.created_at<=? and f.scheduled_at>=? and f.scheduled_at<?
            """+own+" order by f.scheduled_at,s.id limit 10001",(r,n)->{String status=r.getString("status");Instant deadline=instant(r,"response_deadline_at");if("PENDING_RESPONSE".equals(status)&&deadline!=null&&!deadline.isAfter(cutoff))status="PENDING_REASSIGNMENT";return new Header(r.getObject("id",UUID.class),r.getLong("version"),status,instant(r,"cohort_at"),instant(r,"scheduled_at"));},args.toArray());
        if(headers.size()>10000)throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.REPORT_RESULT_LIMIT_EXCEEDED,"Reduce el periodo:el reporte supera10000 servicios.");
        var source=source(headers,organization,ownDriver,cutoff);var items=new ArrayList<Item>();
        for(var h:headers)items.add(item(h,ownDriver,cutoff,source));
        long completed=items.stream().filter(Item::validCompletion).count(),cancelled=count(items,"CANCELLED"),interrupted=count(items,"INTERRUPTED"),transferred=count(items,"TRANSFERRED");
        var total=new Totals(items.size(),completed,cancelled,interrupted,items.size()-completed-cancelled-interrupted-transferred,transferred,
            items.isEmpty()?null:BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(items.size()),4,RoundingMode.HALF_UP));
        String legacyOwn=ownDriver==null?"":" and driver_id=?";
        Object[] legacyArgs=ownDriver==null?new Object[]{organization,ts(begin),ts(end)}:new Object[]{organization,ts(begin),ts(end),ownDriver};
        long legacy=jdbc.queryForObject("select count(*) from assignment where organization_id=? and service_id is null and scheduled_at>=? and scheduled_at<?"+legacyOwn,Long.class,legacyArgs);
        long missing=ownDriver==null?jdbc.queryForObject("select count(*) from scheduled_service s where organization_id=? and not exists(select 1 from assignment a where a.service_id=s.id)",Long.class,organization):0;
        return new View(RULE,ownDriver==null?"ORGANIZATION":"OWN_PARTICIPATION",from,to,zone,cutoff,total,
            new Coverage(legacy,missing,"LEGACY_NOT_CERTIFIED_NO_BACKFILL"),List.copyOf(items));
    }
    private Source source(List<Header> headers,UUID org,UUID own,Instant cutoff){
        if(headers.isEmpty())return new Source(Map.of(),Map.of());
        String ids="{"+headers.stream().map(h->h.id.toString()).collect(java.util.stream.Collectors.joining(","))+"}";
        var args=new ArrayList<Object>(List.of(ts(cutoff),ids,org,ts(cutoff)));if(own!=null)args.add(own);
        var segments=new LinkedHashMap<UUID,List<Segment>>();
        jdbc.query("""
            select e.*,coalesce(c.corrected_started_at,e.started_at) effective_start,
             coalesce(c.corrected_ended_at,e.ended_at) effective_end,c.correction_id,
             case when d.declared_at is null then 'NOT_DECLARED' else coalesce(c.effective_terminal_result,d.terminal_result,'NOT_DECLARED') end terminal_result,d.declared_at
             from service_execution_segment e left join lateral(
              select x.corrected_started_at,x.corrected_ended_at,p.id correction_id,p.effective_terminal_result
              from service_time_correction_segment x join service_time_correction p on p.id=x.correction_id
              where x.segment_id=e.id and p.corrected_at<=? order by p.revision desc limit 1)c on true
             left join assignment_route_declaration d on d.assignment_id=e.assignment_id
            where e.service_id=any(?::uuid[]) and e.organization_id=? and e.start_recorded_at<=?
            """+(own==null?"":" and e.driver_id=?")+" order by e.service_id,e.ordinal",(org.springframework.jdbc.core.RowCallbackHandler)r->segments.computeIfAbsent(r.getObject("service_id",UUID.class),k->new ArrayList<>()).add(new Segment(r.getObject("driver_id",UUID.class),r.getObject("vehicle_id",UUID.class),instant(r,"started_at"),instant(r,"ended_at"),instant(r,"effective_start"),instant(r,"effective_end"),instant(r,"end_recorded_at"),r.getString("end_kind"),r.getString("terminal_result"),r.getObject("correction_id",UUID.class),instant(r,"declared_at"))),args.toArray());
        var resources=new LinkedHashMap<UUID,List<Resource>>();
        Object[] resourceArgs=own==null?new Object[]{ids,org}:new Object[]{ids,org,own};
        jdbc.query("""
            select a.service_id,a.driver_id,a.vehicle_id,a.status,a.accepted_at,a.scheduled_at,g.group_id from assignment a
             left join assignment_group_snapshot g on g.assignment_id=a.id and g.source='CREATION_SNAPSHOT'
             where a.service_id=any(?::uuid[]) and a.organization_id=?
            """+(own==null?"":" and a.driver_id=?")+" order by a.service_id,a.created_at desc,a.id desc,g.group_id",(org.springframework.jdbc.core.RowCallbackHandler)r->resources.computeIfAbsent(r.getObject("service_id",UUID.class),k->new ArrayList<>()).add(new Resource(r.getObject("driver_id",UUID.class),r.getObject("vehicle_id",UUID.class),r.getObject("group_id",UUID.class),r.getString("status"),r.getTimestamp("accepted_at")!=null,instant(r,"scheduled_at"))),resourceArgs);
        return new Source(segments,resources);
    }
    private Item item(Header h,UUID own,Instant cutoff,Source source){
        var segments=source.segments.getOrDefault(h.id,List.of());var resources=source.resources.getOrDefault(h.id,List.of());
        Segment first=segments.isEmpty()?null:segments.getFirst(),last=segments.isEmpty()?null:segments.getLast();
        boolean closed=last!=null&&last.recordedEnd!=null&&!last.recordedEnd.isAfter(cutoff)&&segments.stream().allMatch(e->e.recordedEnd!=null&&!e.recordedEnd.isAfter(cutoff));
        boolean declared=last!=null&&last.declaredAt!=null&&!last.declaredAt.isAfter(cutoff);
        boolean completed=closed&&declared&&"COMPLETED".equals(last.kind)&&(own!=null||"COMPLETED".equals(h.status));
        String status=h.status;
        if(own!=null){
            if(last!=null)status=closed?last.kind:"IN_SERVICE";
            else status=resources.stream().filter(Resource::accepted).map(Resource::status).findFirst().orElse("PENDING_REVIEW");
        }
        // A terminal header without a valid started/closed segment never enters the numerator.
        if("COMPLETED".equals(status)&&!completed)status="COMPLETION_NOT_CERTIFIED";
        var driverIds=resources.stream().map(Resource::driver).distinct().sorted().toList();var vehicleIds=resources.stream().map(Resource::vehicle).distinct().sorted().toList();
        var groups=resources.stream().map(Resource::group).filter(Objects::nonNull).distinct().sorted().toList();
        Instant currentPlan=own==null?h.current:resources.stream().filter(Resource::accepted).map(Resource::scheduledAt).findFirst().orElse(null);
        return new Item(h.id,h.version,h.cohort,currentPlan,status,completed,first==null?null:first.start,closed?last.end:null,
            first==null?null:first.effectiveStart,closed?last.effectiveEnd:null,closed&&"COMPLETED".equals(last.kind)?last.recordedEnd:null,declared,
            last==null?"NOT_DECLARED":last.result,segments.stream().map(Segment::correction).filter(Objects::nonNull).distinct().toList(),driverIds,vehicleIds,groups);
    }
    private static long count(List<Item> values,String state){return values.stream().filter(v->state.equals(v.status())).count();}
    private static Timestamp ts(Instant i){return Timestamp.from(i);}
    private static Instant instant(ResultSet r,String key)throws SQLException{Timestamp t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    private record Header(UUID id,long version,String status,Instant cohort,Instant current) { }
    private record Segment(UUID driver,UUID vehicle,Instant start,Instant end,Instant effectiveStart,Instant effectiveEnd,Instant recordedEnd,String kind,String result,UUID correction,Instant declaredAt) { }
    private record Resource(UUID driver,UUID vehicle,UUID group,String status,boolean accepted,Instant scheduledAt) { }
    private record Source(Map<UUID,List<Segment>> segments,Map<UUID,List<Resource>> resources) { }
}
