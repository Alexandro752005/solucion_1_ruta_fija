package pe.rutafija.support;
import java.nio.file.*;
public final class L08ConnectedGuardProbe {
 public static void main(String[] args)throws Exception{
  Path marker=Path.of(".local/l08-connected.active");
  if(!Files.isRegularFile(marker)||!L08ConnectedTrial.RUN.equals(Files.readString(marker).strip()))throw new IllegalStateException("EXACT_L08_MARKER_REQUIRED");
  try{new L02PostgresIntegrationTest(){}.verifyRollbackBoundary();}
  catch(IllegalStateException rejected){if(rejected.getMessage().startsWith("L08 connected case protected:")){System.out.println("L08_FIXTURE_GUARD=PASS_REFUSED_BEFORE_DATABASE_FIXTURE");return;}throw rejected;}
  throw new IllegalStateException("ROLLBACK_FIXTURE_GUARD_NOT_EFFECTIVE");
 }
}
