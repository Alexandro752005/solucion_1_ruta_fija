package pe.rutafija.support;
/** Marker check before any fixture transaction or setup. */
public final class L07ConnectedGuardProbe {
 public static void main(String[] args){
  try{new L02PostgresIntegrationTest(){}.verifyRollbackBoundary();}
  catch(IllegalStateException expected){if(expected.getMessage().startsWith("L07 connected case protected:")){System.out.println("L07_ROLLBACK_GUARD=PASS_REFUSED_BEFORE_FIXTURE");return;}}
  throw new IllegalStateException("L07_MARKER_GUARD_NOT_PROVED");
 }
}
