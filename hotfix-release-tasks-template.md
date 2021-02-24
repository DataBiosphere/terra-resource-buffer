# Hotfix Releases for Terra

_Note: This documentation is for new Terra applications, like Data Repo and Workspace Manager, that do not depend on any configuration in firecloud-develop. If your application still depends on some configuration in firecloud-develop, follow the [hotfix checklist here](https://github.com/broadinstitute/firecloud-develop/blob/dev/hotfix-release-tasks-template.md)._

## Contents
* [Step 1: Prepare Release Candidate](#step-1-prepare-release-candidate)
* [Step 2: Deploy to Dev and Staging](#step-2-deploy-to-dev-and-staging)
* [Step 3: Deploy to Prod](#step-3-deploy-to-prod)

## Step 1: Prepare Release Candidate

* [ ] **Inform QA** ([#dsde-qa](https://broadinstitute.slack.com/archives/C53JYBV9A)) that you will be preparing a hotfix release for your application.
* [ ] **Build new container image(s)** for your hotfix following your team's documented procedures.
* [ ] **Verify** that unit tests and integration tests have passed against the hotfix candidate.
* [ ] **Identify the version number** for your hotfix candidate.

## Step 2: Deploy to Dev and Staging

* [ ] **Prepare a pull request** to the terra-helmfile repo.
  * [ ] Open the [versions.yaml](https://github.com/broadinstitute/terra-helmfile/blob/master/versions.yaml) file at the root of terra-helmfile repo, and update the
  `releases.<yourapp>.appVersion` key to the version number identified in step 1.
  * [ ] Update the same key in [versions/staging.yaml](https://github.com/broadinstitute/terra-helmfile/blob/master/versions/staging.yaml).
  * [ ] Title the PR according to the following formula:
    * `[<Jira ticket>] <application name> staging hotfix <date>`
    * Eg. `[PROD-123] datarepo staging hotfix 2021-01-27`
  * [ ] Add the `hotfix` label to the PR
* [ ] **Merge** the pull request.
* [ ] **Sync** the ArgoCD deployment for your application in dev.
* [ ] **Sync** the ArgoCD deployment for your application in staging.
* [ ] **Verify** the hotfix is working in staging.

## Step 3: Deploy to Prod

* [ ] **Prepare a pull request** to the terra-helmfile repo.
  * [ ] Update the `releases.<yourapp>.appVersion` key in [versions/prod.yaml](https://github.com/broadinstitute/terra-helmfile/blob/master/versions/prod.yaml) to deploy the hotfix version of your application.
  * [ ] Title the PR according to the following formula:
    * `[<Jira ticket>] <application name> prod hotfix <date>`
    * Eg. `[PROD-123] datarepo prod hotfix 2021-01-27`
  * [ ] Add the `hotfix` label to the PR
* [ ] **Merge** the pull request.
  * At least one approval is required for production changes.
* [ ] **Sync** the ArgoCD deployment for your application in prod.
* [ ] **Verify** the hotfix is working in prod.