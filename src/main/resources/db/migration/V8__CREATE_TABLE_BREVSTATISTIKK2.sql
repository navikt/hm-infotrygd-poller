CREATE TABLE IF NOT EXISTS v1_brevstatistikk2 (
    enhet       text        NOT NULL,
    dato        date        NOT NULL,
    digital     boolean     NOT NULL,
    brevkode    text        NOT NULL,
    valg        text        NOT NULL,
    undervalg   text        NOT NULL,
    type        text        NOT NULL,
    resultat    text        NOT NULL,
    antall      text        NOT NULL,
    oppdatert   timestamp   NOT NULL default (now()),
    PRIMARY KEY (enhet, dato, digital, brevkode, valg, undervalg, type, resultat)
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
