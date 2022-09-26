#!/bin/bash

arangosh --server.password "" --server.endpoint tcp://coordinator1:8529 --javascript.execute-string "db._createDatabase('ldbc_snb_sf0001')"

arangoimport --server.password "" --server.endpoint tcp://coordinator1:8529 --create-collection true --server.database ldbc_snb_sf0001 --file /resources/ldbc_extract/person.csv --type csv  --separator "|" --on-duplicate ignore --collection Person --translate "id=_key"
arangoimport --server.password "" --server.endpoint tcp://coordinator2:8529 --create-collection true --server.database ldbc_snb_sf0001 --file /resources/ldbc_extract/person_knows_person.csv --from-collection-prefix Person --collection knows --to-collection-prefix Person --type csv --separator "|" --translate "src.id=_from" --translate "dst.id=_to"
