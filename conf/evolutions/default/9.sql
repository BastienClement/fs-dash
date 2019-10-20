-- !Ups

CREATE TABLE bounties
(
    id    SNOWFLAKE PRIMARY KEY,
    title TEXT NOT NULL,
    body  TEXT NOT NULL
);

CREATE TABLE bounties_objectives
(
    id              SNOWFLAKE PRIMARY KEY,
    bounty          SNOWFLAKE NOT NULL REFERENCES bounties (id),
    label           TEXT      NOT NULL,
    item            INTEGER,
    count           INTEGER,
    progress        INTEGER   NOT NULL DEFAULT 0,
    show_percentage BOOLEAN   NOT NULL DEFAULT FALSE
);

-- !Downs

DROP TABLE bounties_objectives;
DROP TABLE bounties;
