#!/bin/zsh

pg() {
docker run --rm -i --net host \
	-e PGHOST=$PGHOST \
	-e PGUSER=$PGUSER \
	-e PGPASSWORD=$PGPASSWORD \
	-e PGDATABASE=$PGDATABASE \
	postgres:12-alpine \
	$@
}

pg-local() {
	PGHOST=127.0.0.1 PGUSER=postgres PGPASSWORD=docker PGDATABASE=fs-dash pg $@
}

pg-local psql -d postgres <<< 'DROP DATABASE "fs-dash";'
pg-local psql -d postgres <<< 'CREATE DATABASE "fs-dash";'
pg pg_dump -COx -d $PGDATABASE | pg-local psql
