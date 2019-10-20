--- !Ups

CREATE TABLE charter_sections
(
    id     SNOWFLAKE PRIMARY KEY,
    number INTEGER NOT NULL UNIQUE DEFERRABLE INITIALLY DEFERRED,
    title  TEXT    NOT NULL,
    body   TEXT    NOT NULL
);

--- !Downs

DROP TABLE charter_sections;
