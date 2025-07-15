# terra-resource-buffer-client
Terra Resource Buffering Service Client Library
## Publish a new version
Make sure you are logged in to your @broad account.  You may not have permission to publish; if not, contact Devops.
Update the version in `gradle.properties` file, then run
```
./publish.sh
```
By default, this publishes to the snapshot repo; if you want to publish to the release repo, then use the `release` flag:
```
./publish.sh release
```