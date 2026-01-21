DROP VIEW IF EXISTS v1_brevstatistikk_view;

CREATE VIEW v1_brevstatistikk_view AS
SELECT
    bs.enhet,
    bs.dato,
    INITCAP(COALESCE(bk.beskrivelse, bs.brevkode)) AS brevkode,
    INITCAP(COALESCE(bv.beskrivelse, bs.valg)) AS valg,
    INITCAP(COALESCE(buv.beskrivelse, bs.undervalg)) AS undervalg,
    INITCAP(COALESCE(bt.beskrivelse, bs.type)) AS type,
    INITCAP(COALESCE(br.beskrivelse, bs.resultat)) AS resultat,
    bs.antall,
    bs.oppdatert
FROM v1_brevstatistikk bs
LEFT JOIN v1_brevstatistikk_brevkode bk ON bk.brevkode = TRIM(bs.brevkode)
LEFT JOIN v1_brevstatistikk_type bt ON bt.type = TRIM(bs.type)
LEFT JOIN v1_brevstatistikk_resultat br ON br.resultat = TRIM(bs.resultat)
LEFT JOIN v1_brevstatistikk_valg bv ON bv.valg = TRIM(bs.valg)
LEFT JOIN v1_brevstatistikk_undervalg buv ON buv.valg = TRIM(bs.valg) AND buv.undervalg = TRIM(bs.undervalg)
;
