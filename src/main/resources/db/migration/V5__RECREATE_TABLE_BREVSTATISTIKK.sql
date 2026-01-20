DROP TABLE IF EXISTS v1_brevstatistikk;
CREATE TABLE IF NOT EXISTS v1_brevstatistikk (
    enhet       text        NOT NULL,
    dato        date        NOT NULL,
    brevkode    text        NOT NULL,
    valg        text        NOT NULL,
    undervalg   text        NOT NULL,
    type        text        NOT NULL,
    resultat    text        NOT NULL,
    antall      text        NOT NULL,
    oppdatert   timestamp   NOT NULL default (now()),
    PRIMARY KEY (enhet, dato, brevkode, valg, undervalg, type, resultat)
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
