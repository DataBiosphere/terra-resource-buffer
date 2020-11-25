# buffer-clienttests
This Gradle project contains Test Runner tests written with the Buffer Service client library.

The Test Runner library [GitHub repository](https://github.com/DataBiosphere/terra-test-runner) has documentation for
how to write and execute tests.

#### SA keys from Vault
Run the local-dev/render-config.sh script before running tests.

Each service account JSON files in the resources/serviceaccounts directory of this project specifies a default file
path for the client secret file. This default path should match where the render-config.sh script puts the secret.
