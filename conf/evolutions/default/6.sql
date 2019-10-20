--- !Ups

CREATE TABLE calendar_events
(
    id      SNOWFLAKE PRIMARY KEY,
    date    TIMESTAMP NOT NULL,
    label   TEXT      NOT NULL,
    is_note BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX calendar_events_date_idx ON calendar_events (date);

CREATE TABLE calendar_answers
(
    event     SNOWFLAKE,
    "user"    SNOWFLAKE,
    date      TIMESTAMP NOT NULL,
    available BOOLEAN   NOT NULL,
    note      TEXT,

    PRIMARY KEY (event, "user"),
    FOREIGN KEY (event) REFERENCES calendar_events (id),
    FOREIGN KEY ("user") REFERENCES users (id)
);

--- !Downs

DROP TABLE calendar_answers;
DROP TABLE calendar_events;
