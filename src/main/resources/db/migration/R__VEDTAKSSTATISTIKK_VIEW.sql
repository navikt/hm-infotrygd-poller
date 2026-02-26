DROP VIEW IF EXISTS v1_vedtaksstatistikk_view;
CREATE VIEW v1_vedtaksstatistikk_view AS
SELECT
    vs.enhet,
    vs.dato,
    COALESCE(INITCAP(bv.beskrivelse), vs.valg) AS valg,
    COALESCE(INITCAP(buv.beskrivelse), vs.undervalg) AS undervalg,
    COALESCE(INITCAP(bt.beskrivelse), vs.type) AS type,
    COALESCE(INITCAP(br.beskrivelse), vs.resultat) AS resultat,
    vs.antall,
    vs.oppdatert
FROM v1_vedtaksstatistikk vs
LEFT JOIN v1_brevstatistikk_type bt ON bt.type = TRIM(vs.type)
LEFT JOIN v1_brevstatistikk_resultat br ON br.resultat = TRIM(vs.resultat)
LEFT JOIN v1_brevstatistikk_valg bv ON bv.valg = TRIM(vs.valg)
LEFT JOIN v1_brevstatistikk_undervalg buv ON buv.valg = TRIM(vs.valg) AND buv.undervalg = TRIM(vs.undervalg)
;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
