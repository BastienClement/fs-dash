#!/bin/zsh

docker run --rm -it --net host \
	-e PGHOST=127.0.0.1 \
	-e PGUSER=postgres \
	-e PGPASSWORD=docker \
	-e PGDATABASE=fs-dash \
	postgres:12-alpine \
	psql
