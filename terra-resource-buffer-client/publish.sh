#!/bin/bash

# Default to snapshot repository
REPO_TYPE=${1:-snapshot}

export GOOGLE_CLOUD_PROJECT=dsp-artifact-registry
export GAR_LOCATION=us-central1

if [ "$REPO_TYPE" = "release" ]; then
    export GAR_REPOSITORY_ID=libs-release-standard
    echo "Publishing to release repository: $GAR_REPOSITORY_ID"
else
    export GAR_REPOSITORY_ID=libs-snapshot-standard
    echo "Publishing to snapshot repository: $GAR_REPOSITORY_ID"
fi

./gradlew test
./gradlew publish