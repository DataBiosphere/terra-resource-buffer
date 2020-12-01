# buffer-clienttests
This Gradle project contains Test Runner tests written with the Buffer Service client library.

The Test Runner library [GitHub repository](https://github.com/DataBiosphere/terra-test-runner) has documentation for
how to write and execute tests.

#### Run a test
From the buffer-clienttests directory:
```
./gradlew  runTest --args="configs/BasicAuthenticated.json /tmp/TR"
```

The default server that this test will run against is specified in the resources/configs/BasicAuthenticated.json file.
To override the default server, set an environment variable
```
TEST_RUNNER_SERVER_SPECIFICATION_FILE="mmedlock-dev.json" ./gradlew  runTest --args="configs/BasicAuthenticated.json /tmp/TR"
```

#### SA keys from Vault
Run the tools/render-config.sh script before running tests. The first argument is required; it corresponds to the 
namespace in the terra-integration cluster that you want to test against.

From the buffer-clienttests/tools directory:
```
./render-config.sh mmedlock
```

Each service account JSON files in the resources/serviceaccounts directory of this project specifies a default file
path for the client secret file. This default path should match where the render-config.sh script puts the secret.
