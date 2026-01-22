DROP VIEW IF EXISTS v1_brevstatistikk_view;
ALTER TABLE v1_brevstatistikk
ALTER COLUMN antall
TYPE bigint
USING antall::bigint;
