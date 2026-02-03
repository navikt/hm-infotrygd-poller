DROP VIEW IF EXISTS v1_brevstatistikk_view;
CREATE VIEW v1_brevstatistikk_view AS
SELECT
    bs.enhet,
    bs.dato,
    COALESCE(INITCAP(bk.beskrivelse), bs.brevkode) AS brevkode,
    COALESCE(INITCAP(bv.beskrivelse), bs.valg) AS valg,
    COALESCE(INITCAP(buv.beskrivelse), bs.undervalg) AS undervalg,
    COALESCE(INITCAP(bt.beskrivelse), bs.type) AS type,
    COALESCE(INITCAP(br.beskrivelse), bs.resultat) AS resultat,
    bs.antall,
    bs.oppdatert
FROM v1_brevstatistikk bs
LEFT JOIN v1_brevstatistikk_brevkode bk ON bk.brevkode = TRIM(bs.brevkode)
LEFT JOIN v1_brevstatistikk_type bt ON bt.type = TRIM(bs.type)
LEFT JOIN v1_brevstatistikk_resultat br ON br.resultat = TRIM(bs.resultat)
LEFT JOIN v1_brevstatistikk_valg bv ON bv.valg = TRIM(bs.valg)
LEFT JOIN v1_brevstatistikk_undervalg buv ON buv.valg = TRIM(bs.valg) AND buv.undervalg = TRIM(bs.undervalg)
WHERE TRUE AND
  -- Fjern brevkode som antyder mangel på brev (muntlig vedtak)
  TRIM(bs.brevkode) <> '0VSS'
;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
