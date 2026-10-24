package pe.rutafija.support;

/** Read-only proof that an active connected marker refuses rollback fixture suites before any fixture setup. */
public final class L05ConnectedGuardProbe {
    public static void main(String[] args){
        try {new L02PostgresIntegrationTest(){}.verifyRollbackBoundary();}
        catch(IllegalStateException expected){if(expected.getMessage().startsWith("L05 connected case protected:")){System.out.println("L05_ROLLBACK_GUARD=PASS_REFUSED_BEFORE_FIXTURE");return;}}
        throw new IllegalStateException("L05_MARKER_GUARD_NOT_PROVED");
    }
}
