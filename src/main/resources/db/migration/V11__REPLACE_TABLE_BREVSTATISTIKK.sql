DROP VIEW IF EXISTS v1_brevstatistikk_view;
DROP TABLE IF EXISTS v1_brevstatistikk;
ALTER TABLE v1_brevstatistikk2 RENAME TO v1_brevstatistikk;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
