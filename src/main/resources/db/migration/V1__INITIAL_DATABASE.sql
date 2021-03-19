CREATE TABLE IF NOT EXISTS V1_POLL_LIST
(
    SOKNADS_ID          UUID        NOT NULL,
    FNR_BRUKER          CHAR(11)    NOT NULL,
    TKNR                CHAR(4)     NOT NULL,
    SAKSBLOKK           CHAR(1)     NOT NULL,
    SAKSNR              CHAR(2)     NOT NULL,
    NUMBER_OF_POLLINGS  INTEGER     NOT NULL,
    CREATED             TIMESTAMP   NOT NULL default (now()),
    LAST_POLL           TIMESTAMP   NULL default NULL,
    PRIMARY KEY (SOKNADS_ID)
);
