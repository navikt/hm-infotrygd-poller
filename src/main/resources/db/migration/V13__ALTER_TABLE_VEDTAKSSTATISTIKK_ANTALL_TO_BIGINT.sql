DROP VIEW IF EXISTS v1_vedtaksstatistikk_view;

ALTER TABLE v1_vedtaksstatistikk
    ALTER COLUMN antall TYPE bigint
    USING antall::bigint;
