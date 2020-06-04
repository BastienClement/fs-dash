#!/bin/zsh

docker run --rm -it --net host \
	-e PGHOST=$PGHOST \
	-e PGUSER=$PGUSER \
	-e PGPASSWORD=$PGPASSWORD \
	-e PGDATABASE=$PGDATABASE \
	postgres:12-alpine \
	psql
