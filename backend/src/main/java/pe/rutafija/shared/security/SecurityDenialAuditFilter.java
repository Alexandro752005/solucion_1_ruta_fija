package pe.rutafija.shared.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import pe.rutafija.audit.application.SecurityDenialRecorder;
import java.io.IOException;
import java.util.Set;

/** No request body, query, bearer, cookie or resource value is copied to audit. */
@Component
public class SecurityDenialAuditFilter extends OncePerRequestFilter {
    public static final String ENTERED=SecurityDenialAuditFilter.class.getName()+".entered";
    private final SecurityDenialRecorder recorder;
    public SecurityDenialAuditFilter(SecurityDenialRecorder recorder){this.recorder=recorder;}
    public static boolean relevant(String path){return path.startsWith("/api/v1/")&&!path.startsWith("/api/v1/auth/")&&!path.startsWith("/api/v1/mobile/auth/")&&!path.startsWith("/api/v1/onboarding/applicant/");}
    public static String module(String path){
        if(path.startsWith("/api/v1/platform/")||path.startsWith("/api/v1/platform-admins")||path.startsWith("/api/v1/organizations"))return "PLATFORM";
        if(path.contains("/audit-events"))return "AUDIT";
        if(path.startsWith("/api/v1/onboarding/")||path.startsWith("/api/v1/invitations"))return "ONBOARDING";
        return "OPERATIONS";
    }
    public void persist(HttpServletRequest request,int status){
        if(!relevant(request.getRequestURI()))return;
        try{recorder.record(SecurityContextHolder.getContext().getAuthentication(),status,request.getMethod(),module(request.getRequestURI()));request.setAttribute("L07_SECURITY_AUDIT_RESULT","PERSISTED");}
        catch(RuntimeException failure){request.setAttribute("L07_SECURITY_AUDIT_RESULT","FAILED");LoggerFactory.getLogger(getClass()).error("Security denial audit persistence failed; access remains denied.");}
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        request.setAttribute(ENTERED,Boolean.TRUE);
        try{chain.doFilter(request,response);}finally{
            int status=response.getStatus();boolean knownObject=SecurityContextHolder.getContext().getAuthentication()!=null&&request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)!=null;
            if(Set.of(401,403).contains(status)||status==404&&knownObject)persist(request,status);
        }
    }
}
