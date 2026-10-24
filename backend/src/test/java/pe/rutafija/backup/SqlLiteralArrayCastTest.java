package pe.rutafija.backup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SqlLiteralArrayCastTest {
 @Test void restoresOnlyTheKnownConstraintSpellingWithoutChangingItsPredicate(){
  assertThat(SqlLiteralArrayCast.sourceSpelling("CHECK (role::text = ANY (ARRAY['ADMIN'::character varying::text, 'CONDUCTOR'::character varying::text]))",false))
   .isEqualTo("CHECK (role::text = ANY (ARRAY['ADMIN'::character varying, 'CONDUCTOR'::character varying]::text[]))");
 }
 @Test void restoresTheIndexArrayGroupingAndKeepsEveryOtherToken(){
  assertThat(SqlLiteralArrayCast.sourceSpelling("WHERE (status = ANY (ARRAY[('A'::character varying)::text, ('B'::character varying)::text]))",true))
   .isEqualTo("WHERE (status = ANY ((ARRAY['A'::character varying, 'B'::character varying])::text[]))");
 }
 @Test void doesNotEraseBoundedOrUnknownCastsOrNonliteralExpressions(){
  for(String sql:new String[]{"ARRAY['LONG'::character varying(2)::text]","ARRAY[role::character varying::text]","ARRAY['A'::integer::text]","ARRAY['A'::character varying]","ARRAY['A'::character varying::text, NULL]"})
   assertThat(SqlLiteralArrayCast.sourceSpelling(sql,false)).isEqualTo(sql);
 }
 @Test void handlesQuotedCommaAndEscapedQuoteAndPreservesChangesInRoleValues(){
  assertThat(SqlLiteralArrayCast.sourceSpelling("ARRAY['a,b''c'::character varying::text]",false)).isEqualTo("ARRAY['a,b''c'::character varying]::text[]");
  assertThat(SqlLiteralArrayCast.sourceSpelling("ARRAY['ADMIN'::character varying::text]",false))
   .isNotEqualTo(SqlLiteralArrayCast.sourceSpelling("ARRAY['SUPER_ADMIN'::character varying::text]",false));
 }
}
