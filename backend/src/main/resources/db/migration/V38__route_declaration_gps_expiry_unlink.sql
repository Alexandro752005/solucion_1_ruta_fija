-- Keep declaration originals immutable; only a deleted GPS FK may be nulled by its referential action.
CREATE FUNCTION route_declaration_originals_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.evidence_point_id IS NOT NULL AND NEW.evidence_point_id IS NULL
  AND (to_jsonb(NEW)-'evidence_point_id') IS NOT DISTINCT FROM (to_jsonb(OLD)-'evidence_point_id')
  AND NOT EXISTS(SELECT 1 FROM service_gps_point WHERE id=OLD.evidence_point_id)
 THEN RETURN NEW; END IF;
 RAISE EXCEPTION 'Route declaration originals cannot be rewritten or deleted' USING ERRCODE='23514';
END; $$;
DROP TRIGGER protect_route_declaration ON assignment_route_declaration;
CREATE TRIGGER protect_route_declaration BEFORE UPDATE OR DELETE ON assignment_route_declaration
 FOR EACH ROW EXECUTE FUNCTION route_declaration_originals_guard();
