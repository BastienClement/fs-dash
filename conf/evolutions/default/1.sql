-- !Ups

CREATE DOMAIN SNOWFLAKE AS BIGINT CHECK ( VALUE >= 0 );

CREATE TABLE users
(
    id            SNOWFLAKE PRIMARY KEY,
    username      TEXT NOT NULL,
    discriminator TEXT NOT NULL,
    avatar        TEXT,
    roles         TEXT NOT NULL
);

CREATE TABLE sessions
(
    id            SNOWFLAKE PRIMARY KEY,
    owner         SNOWFLAKE NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_token  TEXT      NOT NULL,
    scope         TEXT      NOT NULL,
    token_type    TEXT      NOT NULL,
    expires_at    TIMESTAMP NOT NULL,
    last_checked  TIMESTAMP NOT NULL,
    refresh_token TEXT      NOT NULL
);

CREATE INDEX ON sessions (owner);

-- !Downs

DROP TABLE sessions;
DROP TABLE users;

DROP DOMAIN SNOWFLAKE;
