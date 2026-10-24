package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** No Spring/database/SMTP. These do not clean or change the protected human fixture. */
class ManualL01AutomatedTermsComplementTest {
    @Test void rejectionMustBeTermsSpecificNotAuthOrAnotherConflict() {
        assertThat(ManualL01AutomatedTermsComplement.termsSpecificRejection(409,"RESOURCE_CONFLICT",ManualL01AutomatedTermsComplement.REASON)).isTrue();
        for(int status:List.of(401,403,200))
            assertThat(ManualL01AutomatedTermsComplement.termsSpecificRejection(status,"RESOURCE_CONFLICT",ManualL01AutomatedTermsComplement.REASON)).isFalse();
        assertThat(ManualL01AutomatedTermsComplement.termsSpecificRejection(409,"RESOURCE_CONFLICT","Correo no verificado")).isFalse();
        assertThat(ManualL01AutomatedTermsComplement.termsSpecificRejection(409,"FORBIDDEN",ManualL01AutomatedTermsComplement.REASON)).isFalse();
    }
    @Test void compositeProofCannotBeLabeledHumanOrOmitEitherSide() {
        var proof=valid();assertThat(ManualL01AutomatedTermsComplement.validProof(proof)).isTrue();
        for(String key:List.of("negativeGate","positiveGate","ordinaryLoginAndGroupsAllowed","manualAcceptancePreserved","negativeThenAcceptanceThenCompletionServerOrderVerified")) {
            var copy=new HashMap<>(proof);copy.remove(key);assertThat(ManualL01AutomatedTermsComplement.validProof(copy)).isFalse();
        }
        proof.put("executionOrigin","HUMAN");assertThat(ManualL01AutomatedTermsComplement.validProof(proof)).isFalse();
    }
    @Test void differentCaseOrDifferentOperationCannotCloseThisCase() {
        var proof=valid();proof.put("runId",UUID.randomUUID().toString());assertThat(ManualL01AutomatedTermsComplement.validProof(proof)).isFalse();
        proof=valid();proof.put("positiveGate",Map.of("httpStatus",200,"sameMethodAndPath",false));
        assertThat(ManualL01AutomatedTermsComplement.validProof(proof)).isFalse();
    }
    @Test void originalManualPredicateRemainsUnmetWithoutItsOwnGate() {
        var state=new HashMap<String,Object>();
        state.put("SC03LocalResult",ManualL01AutomatedTermsComplement.RESULT);
        state.put("executionOrigin","AUTOMATED_SYNTHETIC_ONLY");
        assertThat(ManualL01HumanTrialServer.adminEvidenceComplete(state,List.of())).isFalse();
    }
    Map<String,Object> valid(){return new HashMap<>(Map.of("runId",ManualL01AutomatedTermsComplement.RUN.toString(),
            "SC03LocalResult",ManualL01AutomatedTermsComplement.RESULT,"executionOrigin","AUTOMATED_SYNTHETIC_ONLY",
            "ordinaryLoginAndGroupsAllowed",true,"manualAcceptancePreserved",true,"negativeThenAcceptanceThenCompletionServerOrderVerified",true,
            "negativeGate",Map.of("httpStatus",409,"code","RESOURCE_CONFLICT","reason",ManualL01AutomatedTermsComplement.REASON),
            "positiveGate",Map.of("httpStatus",200,"sameMethodAndPath",true)));}
}
