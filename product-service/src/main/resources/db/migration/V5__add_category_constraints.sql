-- ensure name is unique (already is, but add explicit named constraint)
-- no structural changes needed, categories table already correct
-- just add a comment migration confirming schema is ready
SELECT 1; -- no-op, schema already correct
