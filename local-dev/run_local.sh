#!/bin/bash

export BUFFER_DB_USERNAME=dbuser
export BUFFER_DB_PASSWORD=dbpwd
export BUFFER_DB_RECREATE_DB_ON_START=true
export BUFFER_DATABASE_NAME=testdb
export BUFFER_STAIRWAY_DB_USERNAME=dbuser_stairway
export BUFFER_STAIRWAY_DB_PASSWORD=dbpwd_stairway
export BUFFER_STAIRWAY_DB_FORCE_CLEAN_START=true
export BUFFER_STAIRWAY_DATABASE_NAME=testdb_stairway
export GOOGLE_APPLICATION_CREDENTIALS="$(dirname $0)"/../src/test/resources/rendered/sa-account.json
export BUFFER_CRL_TESTING_MODE=true
export BUFFER_CRL_JANITOR_CLIENT_CREDENTIAL_FILE_PATH="$(dirname $0)"/../src/test/resources/rendered/janitor-client-sa-account.json
export BUFFER_CRL_JANITOR_TRACK_RESOURCE_PROJECT_ID=terra-kernel-k8s
export BUFFER_CRL_JANITOR_TRACK_RESOURCE_TOPIC_ID=crljanitor-tools-pubsub-topic

./gradlew bootRun
