# Terra Resource Buffering Server
Cloud Resource Buffering Server for Terra. 

## Pool Configuration
### File Structure
Pool configuration manages the pool size, resources in the pool. All configuration files are under [src/main/resources/config](src/main/resources/config) folder.
The folder structure is: 
```
-{env}
    - pool_schema.yml
    - resource-config
        - resource_config.yml
- resource_schema.yaml
```
* `{env}` is the configuration folder Buffer service will use. Set `BUFFER_POOL_CONFIG_PATH=config/{env}` as environment variable to change folder to use.
In Broad deployment, the value can be found at [Broad helmfile repo](https://github.com/broadinstitute/terra-helmfile/blob/master/terra/values/buffer/live/dev.yaml#L18) 
* `resource_schema.yaml` is the resource config template
* `pool_schema.yml` lists all pools under that environment. It includes the pool size and resource config to use for that pool.
* `resource-config` folder contains all resource configs all pools are using or used before. 

### Upgrade Pool Configuration
Configuration update require to build a new docker image and redeploy the server.

To update pool size, just update the pool size in the configuration file. 

To update resource configs, it is the same process as creating a new pool using a new resource config. The recommended process is:
1. Add a new resource config and a new pool in configuration file.
2. Wait for next Buffer Service release, and it will create resources using the new config. 
3. Client switch to use the new pool id when ready.
4. Remove the old pool from `pool_schema.yml` and delete old resource config(optional).
5. Next Buffer Service release will delete resoruces in the old pool

## Development
### Connect to dev Buffer Service
[Dev Buffer Service Swagger](https://buffer.dsde-dev.broadinstitute.org/swagger-ui.html)
In Broad deployment, use a valid Google Service Account is required for service authorization. This can be retrieved in Vault:
```
docker run -e VAULT_TOKEN=$(cat ~/.vault-token) -it broadinstitute/dsde-toolbox:dev vault read secret/dsde/terra/kernel/integration/dev/buffer/client-sa
```
### Configs Rendering
Local Testing and Github Action tests require credentials to be able to call GCP, run
``` local-dev/render-config.sh``` first for local testing. It generates:
* A Google Service Account Secret to create/delete cloud resources in test.
* A Google Service Account Secret to publish message to Janitor instance.

### Run Locally
Set executable permissions:
```
chmod +x gradlew
```

To spin up the local postgres, run:
```
local-dev/run_postgres.sh start
```
Start local server
```
local-dev/run_local.sh
```

### Deploy to GKE cluster:
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

To use this, first ensure Skaffold is installed on your local machine 
(available at https://skaffold.dev/). 

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your 
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no 
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
cd local/dev
```
```
./setup_gke_deploy.sh <environment>
```

You can now push to the specified environment by running

```
skaffold run
```

### Connecting psql client using the Cloud SQL Proxy:
Follow [Installing this instruction](https://cloud.google.com/sql/docs/mysql/sql-proxy#macos-64-bit)
to install Cloud SQL Proxy 

Go to cloud console to get the instance name you want to connect to, then start the proxy:
```
./cloud_sql_proxy -instances=<INSTANCE_CONNECTION_NAME>=tcp:5432
```
Start the client session
```
psql "host=127.0.0.1 sslmode=disable dbname=<DB_NAME> user=<USER_NAME>"
```
For Broad engineer, DB_NAME and USER_NAME can be found in vault. 
```
docker run -e VAULT_TOKEN=$(cat ~/.vault-token) -it broadinstitute/dsde-toolbox:dev vault read secret/dsde/terra/kernel/integration/{$NAMESPACE}/buffer/postgres/{db-creds|stairway-db-creds}
```
The db instance name can be also found under `...buffer/postgres/instance` in vault.

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

