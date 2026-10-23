package pe.rutafija.support;

/** Read-only proof that an active connected marker refuses rollback fixture suites before any fixture setup. */
public final class L06ConnectedGuardProbe {
    public static void main(String[] args){
        try {new L02PostgresIntegrationTest(){}.verifyRollbackBoundary();}
        catch(IllegalStateException expected){if(expected.getMessage().startsWith("L06 connected case protected:")){System.out.println("L06_ROLLBACK_GUARD=PASS_REFUSED_BEFORE_FIXTURE");return;}}
        throw new IllegalStateException("L06_MARKER_GUARD_NOT_PROVED");
    }
}
