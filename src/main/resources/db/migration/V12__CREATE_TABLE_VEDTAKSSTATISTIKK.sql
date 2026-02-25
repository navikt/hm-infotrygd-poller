CREATE TABLE IF NOT EXISTS v1_vedtaksstatistikk (
    enhet       text        NOT NULL,
    dato        date        NOT NULL,
    valg        text        NOT NULL,
    undervalg   text        NOT NULL,
    type        text        NOT NULL,
    resultat    text        NOT NULL,
    antall      text        NOT NULL,
    oppdatert   timestamp   NOT NULL default (now()),
    PRIMARY KEY (enhet, dato, valg, undervalg, type, resultat)
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
