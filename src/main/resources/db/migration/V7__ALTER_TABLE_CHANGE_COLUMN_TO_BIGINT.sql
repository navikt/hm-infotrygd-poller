ALTER TABLE v1_brevstatistikk
ALTER COLUMN antall
TYPE bigint
USING antall::bigint;
