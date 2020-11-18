#!/bin/bash

export RBS_DB_USERNAME=dbuser
export RBS_DB_PASSWORD=dbpwd
export RBS_DB_RECREATE_DB_ON_START=true
export RBS_DATABASE_NAME=testdb
export RBS_STAIRWAY_DB_USERNAME=dbuser_stairway
export RBS_STAIRWAY_DB_PASSWORD=dbpwd_stairway
export RBS_STAIRWAY_DB_FORCE_CLEAN_START=true
export RBS_STAIRWAY_DATABASE_NAME=testdb_stairway
export GOOGLE_APPLICATION_CREDENTIALS="$(dirname $0)"/../src/test/resources/rendered/sa-account.json
export RBS_CRL_TESTING_MODE=true
export RBS_CRL_JANITOR_CLIENT_CREDENTIAL_FILE_PATH="$(dirname $0)"/../src/test/resources/rendered/janitor-client-sa-account.json
export RBS_CRL_JANITOR_TRACK_RESOURCE_PROJECT_ID=terra-kernel-k8s
export RBS_CRL_JANITOR_TRACK_RESOURCE_TOPIC_ID=crljanitor-tools-pubsub-topic
./gradlew bootRun
