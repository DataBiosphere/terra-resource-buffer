#!/bin/bash
export GOOGLE_CLOUD_PROJECT=dsp-artifact-registry
export GAR_LOCATION=us-central1
export GAR_REPOSITORY_ID=libs-snapshot-standard
./gradlew test
./gradlew publish