#!/bin/bash

export RBS_DB_USER=dbuser
export RBS_DB_PASSWORD=dbpwd
export RBS_DATABASE_NAME=testdb
export RBS_STAIRWAY_DB_USER=dbuser_stairway
export RBS_STAIRWAY_DB_PASSWORD=dbpwd_stairway
export RBS_STAIRWAY_DATABASE_NAME=testdb_stairway
./gradlew bootRun
