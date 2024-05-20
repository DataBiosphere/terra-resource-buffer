# Terra Resource Buffering Server
Cloud Resource Buffering Server for Terra.

RBS Cheat sheet:
- RBS (server) creates cloud resources based on config provided by clients. Resource details are stored in a database. 
- Common clients of RBS: [Workspace Manager](https://github.com/DataBiosphere/terra-workspace-manager), [Terra Data Repo](https://github.com/DataBiosphere/jade-data-repo), [Rawls](https://github.com/broadinstitute/rawls)
- Local testing: may use a local Postgres DB (native installation) or within Docker. Resource specifics are also notified to Janitor service by writing a pub-sub message if it is to be auto-deleted
- [Buffer Service envs](https://docs.google.com/document/d/1FyYpASNoKW2iY1IIuRmWpRL6-ST06_n5iYg0m_3LP-c/)
- [DSP playbook](https://docs.google.com/document/d/1fo5Q92sJe-8BZNRPuI2GtvrUwWqHKDY4UouD86KtVcM)

## Static (Build Time) Pool Configuration
### File Structure
Pool configuration manages the pool size, and configuration of resources in the pool. All static configuration files are under [src/main/resources/config](src/main/resources/config) folder.  These static configurations are built into the Resource Buffer service at compile time.
The folder structure is:
```
-{env}
    - pool_schema.yml
    - resource-config
        - resource_config.yml
- resource_schema.yaml
```
* `{env}` is the static configuration folder Buffer service will use. Set `BUFFER_POOL_CONFIG_PATH=config/{env}` as environment variable to change folder to use.
In Broad deployment, the value can be found at [Broad helmfile repo](https://github.com/broadinstitute/terra-helmfile/blob/326c7f220c4d3ef5466b12e8a6f8b5fa3f255ec0/values/app/buffer/live/dev.yaml#L27)
* `resource_schema.yaml` is the resource config template
* `pool_schema.yml` lists all pools under that environment. It includes the pool size and resource config to use for that pool.
* `resource-config` folder contains all resource configs all pools are using or used before.

### Upgrade Static Pool Configuration
When using static pools, a configuration update is required to build a new docker image and redeploy the server.

To update pool size, just update the pool size in the configuration file.

To update resource configs, it is the same process as creating a new pool using a new resource config. The recommended process is:
1. Add a new resource config and a new pool in configuration file.
2. Wait for next Buffer Service release, and it will create resources using the new config.
3. Client switch to use the new pool id when ready.
4. Remove the old pool from `pool_schema.yml` and delete old resource config(optional).
5. Next Buffer Service release will delete resoruces in the old pool

## Runtime Pool Configuration
Since version [`0.176.0`](https://github.com/DataBiosphere/terra-resource-buffer/releases/tag/0.176.0), Resource Buffer has supported runtime pool configuration.  Instead of requiring the use of a static pool configuration created at build time (and thus requiring a new version of the Resource Buffer service container to be built and published), runtime pool configuration allows for the specification of pool configurations at service runtime, read from a directory on the local filesystem.

In order to use a runtime pool configuration, the environment variable `BUFFER_POOL_SYSTEM_FILE_PATH` should point at a directory on the local file system in the RBS container which stores the pool configuration.  *This will override the use of any static pool configurations built into the Resource Buffer Jarfile.*

### Runtime Pool Example
* `BUFFER_POOL_SYSTEM_FILE_PATH` is set to `/etc/config/staging`
* Local file `/etc/config/staging/pool_schema.yaml` contains two pools,
  ```
  # RBS Pools Schema for staging environment
  ---
  poolConfigs:
    - poolId: "resource_staging_v1"
      size: 100
      resourceConfigName: "resource_staging_v1"
  poolConfigs:
    - poolId: "resource_staging_v2"
      size: 100
      resourceConfigName: "resource_staging_v2"
  ```
* Local file `/etc/config/staging/resource_config/resource_staging_v1.yaml` contains the configuration for pool `resource_staging_v1`
* Local file `/etc/config/staging/resource_config/resource_staging_v2.yaml` contains the configuration for pool `resource_staging_v2`

### Notes
* Pool configurations are only read at Resource Buffer start time, so changes to files in the configuration directory will require the service to be restarted for these configurations to take effect.
* The same rules around pool modification and deletion apply for dynamically configured pools as do for static pools:
  * For existing pools, pool sizes may change, but resource configurations may not.
  * If a previously created pool does not exist under `BUFFER_POOL_SYSTEM_FILE_PATH`, it will be deleted.

## GitHub Interactions

We currently have these workflows:

Workflow      | Triggers                  | Work
--------------|---------------------------|-------
_master_push_ | on PR merge to master     | tags, version bumps, publishes client to artifactory, pushes image to GCR
_test-runner-nightly-perf_ | nightly at 1am ET         | runs the TestRunner-based integration test suite
_test_ | on PR and merge to master | runs the unit and integration tests
_trivy_ | on PR                     | Broad app-security scan on base Docker image

## Development, testing & deployment

### Connect to dev Buffer Service
[Dev Buffer Service Swagger](https://buffer.dsde-dev.broadinstitute.org/swagger-ui.html)

In Broad deployment, use a valid Google Service Account(created by [Terraform](https://github.com/broadinstitute/terraform-ap-modules/blob/master/buffer/sa.tf#L83)) is required for service authorization. This can be retrieved in Vault.
To get client service account access token:

Step 1:
```
docker run --rm --cap-add IPC_LOCK -e "VAULT_TOKEN=$(cat ~/.vault-token)" -e "VAULT_ADDR=https://clotho.broadinstitute.org:8200" vault:1.1.0 vault read -format json secret/dsde/terra/kernel/dev/dev/buffer/client-sa | jq -r '.data.key' | base64 --decode > buffer-client-sa.json
```
Step2:
```
gcloud auth activate-service-account --key-file=buffer-client-sa.json
```
Step3:
```
gcloud auth print-access-token
```

To access Buffer Service in other environment, lookup for `vault.pathPrefix` in [helmfile repo](https://github.com/broadinstitute/terra-helmfile/tree/master/values/app/buffer) to find the correct vault path.

### Configs Rendering
Local Testing and Github Action tests require credentials to be able to call GCP, run
``` local-dev/render-config.sh``` first for local testing. It generates:
* A Google Service Account Secret to create/delete cloud resources in test.
* A Google Service Account Secret to publish message to Janitor instance.

### Run Locally

Use JDK 17, [instructions](https://github.com/DataBiosphere/terra-workspace-manager/blob/main/DEVELOPMENT.md#jdk)

Set executable permissions:
```
chmod +x gradlew
```

To spin up the local postgres (within docker), run:
```
local-dev/run_postgres.sh start
```
Start local server. Environment variables are added into this file
```
local-dev/run_local.sh
```

This starts a local instance of the Resource Buffer Service. Check the status of service on your browser
```
http://localhost:8080/status
```

Next, go to the Cloud Console to find the cloud project folder, identified by the parentFolderId in resource_schema.yml.
Pool schema to be used is set via environment variable `BUFFER_POOL_CONFIG_PATH`

The name of the new Google Project created by the local RBS instance above in found in console logs, search for the same on Google cloud console

### Deploy to GKE cluster:
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

To use this, first ensure Skaffold is installed on your local machine
(available at https://skaffold.dev/).

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR credentials with `gcloud auth configure-docker`.
Alternately use `gcloud auth login <you>@broadinstitute.org` or `gcloud auth application-default login` for fine grain control of the login credentials to be used (account must have permissions to push to GKE)
```
cd local-dev
```
The script itself permits pushing to any valid environment as the target. Developers however only have access to push to their [personal environment](https://github.com/DataBiosphere/terra/blob/main/docs/dev-guides/personal-environments.md)

```
./setup_gke_deploy.sh <environment>
```
You can now push to the specified environment by running
```
skaffold run
```

### Connecting psql client using the Cloud SQL Proxy:
Follow [Installing this instruction](https://cloud.google.com/sql/docs/mysql/sql-proxy#macos-64-bit)
to install Cloud SQL Proxy. This is used to connect to the remote SQL database used by RBS

Step 1: Go to cloud console to get the instance name you want to connect to, then start the proxy:
```
./cloud_sql_proxy -instances=<INSTANCE_CONNECTION_NAME>=tcp:5432
```
Step 2: Then set database password as `PGPASSWORD`:
```
export PGPASSWORD={BUFFER_DB_PASSWORD}
```
Step 3.1: To connect to Buffer Database, run:
```
psql "host=127.0.0.1 sslmode=disable dbname=buffer user=buffer"
```
Step 3.2: To connect to Buffer Stariway Database, run:
```
psql "host=127.0.0.1 sslmode=disable dbname=buffer-stairway user=buffer-stairway"
```
#### Connect to Broad Deployment Buffer Database
For Broad engineer, BUFFER_DB_PASSWORD can be found in vault. For example, to connect to Dev Buffer Database, run:
```
export PGPASSWORD=$(docker run -e VAULT_TOKEN=$(cat ~/.vault-token) -it broadinstitute/dsde-toolbox:dev vault read -field='password' secret/dsde/terra/kernel/dev/dev/buffer/postgres/db-creds)
```
Then:
```
psql "host=127.0.0.1 sslmode=disable dbname=buffer user=buffer"
```

Note that you must stop the local postgres first to free the 5432 port.
See [this document](https://cloud.google.com/sql/docs/postgres/connect-admin-proxy) for more details.

### Dependencies
We use [Gradle's dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
to ensure that builds use the same transitive dependencies, so they're reproducible. This means that
adding or updating a dependency requires telling Gradle to save the change. If you're getting errors
that mention "dependency lock state" after changing a dep, you need to do this step.

```sh
./gradlew dependencies --write-locks
```

### Jacoco
We use [Jacoco](https://www.eclemma.org/jacoco/) as code coverage library

## SourceClear

[SourceClear](https://srcclr.github.io) is a static analysis tool that scans a project's Java
dependencies for known vulnerabilities. If you are working on addressing dependency vulnerabilities
in response to a SourceClear finding, you may want to run a scan off of a feature branch and/or local code.

### Github Action

You can trigger RBS's SCA scan on demand via its
[Github Action](https://github.com/broadinstitute/dsp-appsec-sourceclear-github-actions/actions/workflows/z-manual-terra-resource-buffer.yml),
and optionally specify a Github ref (branch, tag, or SHA) to check out from the repo to scan.  By default,
the scan is run off of RBS's `master` branch.

High-level results are outputted in the Github Actions run.

### Running Locally

You will need to get the API token from Vault before running the Gradle `srcclr` task.

```sh
export SRCCLR_API_TOKEN=$(vault read -field=api_token secret/secops/ci/srcclr/gradle-agent)
./gradlew srcclr
```

High-level results are outputted to the terminal.

### Veracode

Full results including dependency graphs are uploaded to
[Veracode](https://sca.analysiscenter.veracode.com/workspaces/jppForw/projects/544768/issues)
(if running off of a feature branch, navigate to Project Details > Selected Branch > Change to select your feature branch).
You can request a Veracode account to view full results from #dsp-infosec-champions.
