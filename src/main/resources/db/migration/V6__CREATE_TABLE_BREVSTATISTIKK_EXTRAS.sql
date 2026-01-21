CREATE TABLE IF NOT EXISTS v1_brevstatistikk_brevkode (
    brevkode      text NOT NULL,
    beskrivelse   text NOT NULL,
    PRIMARY KEY (brevkode)
);

CREATE TABLE IF NOT EXISTS v1_brevstatistikk_valg (
    valg      text NOT NULL,
    beskrivelse   text NOT NULL,
    PRIMARY KEY (valg)
);

CREATE TABLE IF NOT EXISTS v1_brevstatistikk_undervalg (
    valg          text NOT NULL,
    undervalg     text NOT NULL,
    beskrivelse   text NOT NULL,
    PRIMARY KEY (valg, undervalg)
);

CREATE TABLE IF NOT EXISTS v1_brevstatistikk_type (
    type          text NOT NULL,
    beskrivelse   text NOT NULL,
    PRIMARY KEY (type)
);

CREATE TABLE IF NOT EXISTS v1_brevstatistikk_resultat (
    resultat      text NOT NULL,
    beskrivelse   text NOT NULL,
    PRIMARY KEY (resultat)
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
