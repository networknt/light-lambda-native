## Build and package

Use Maven with JDK 25 (the Java version declared in `pom.xml`):

```sh
mvn clean verify
```

The executable shaded JAR is `target/lambda-native-<version>.jar`, with the
version taken from `pom.xml`. Customers can use this JAR with a compatible
GraalVM Native Image installation to build their own native binary.

To build the Linux native binary and AWS Lambda custom-runtime ZIP, install
Docker and `zip`, then run:

```sh
bash native-image-linux.sh
```

The script runs `mvn clean install`, reads the JAR name and Java version from
Maven, and uses `ghcr.io/graalvm/native-image-community:<maven.compiler.target>`.
Set `GRAALVM_IMAGE` to select a pinned image compatible with that Java version.
Docker builds for its host architecture; use a host matching your Lambda
architecture and verify the resulting binary against your chosen Lambda runtime.
The ZIP is `lambda-native-custom-runtime.zip` and contains executable
`lambda-native` and `bootstrap` files. HTTP and HTTPS protocols are enabled.
Build or packaging failures stop the script with a nonzero exit status.

Run the native-build script regression tests separately with Python 3 and `zip`:

```sh
python3 tests/test_native_image_script.py
```

These tests stub Maven and Docker to check packaging and failure handling.
They are not run by Maven or the current CI workflow.

### Test with Main

We have provided a Main class in the src folder for testing all the middleware handlers with a sample request. You can run the Main class within your IDE with debug mode to see how each middleware handler works.

Before running the Main class, you need to disable the DynamoDbCacheManager as it depends on the AWS environment. To do that you need to common out the service.yml section in the values.yml

```
# ------------< Service Config >-------------
service.singletons:
#  - com.networknt.cache.CacheManager:
#      - com.networknt.aws.lambda.cache.DynamoDbCacheManager
# -------------------------------------------
```

If you want to enable the JWT verification, you need to make sure the token use in the Main class is matching the jwk configuration in the values.yml

```
# client.yml
client.tokenKeyServerUrl: https://networknt.oktapreview.com
client.tokenKeyUri: /oauth2/aus66u5cybTrCsbZs1d6/v1/keys
```

If everything works fine, you should see the following error that indicate all middleware handlers are passed and the AWS Lambda invocation is failed. This is because we are testing without AWS environment.

```
software.amazon.awssdk.services.lambda.model.ResourceNotFoundException: Function not found: arn:aws:lambda:ca-central-1:964637446810:function:PetstoreNativeLambdaProxyFunction (Service: Lambda, Status Code: 404, Request ID: daefb25c-6d31-4efa-b662-69bc2f0fc87d)
	at software.amazon.awssdk.core.internal.http.CombinedResponseHandler.handleErrorResponse(CombinedResponseHandler.java:125)
	at software.amazon.awssdk.core.internal.http.CombinedResponseHandler.handleResponse(CombinedResponseHandler.java:82)
	at software.amazon.awssdk.core.internal.http.CombinedResponseHandler.handle(CombinedResponseHandler.java:60)
	at software.amazon.awssdk.core.internal.http.CombinedResponseHandler.handle(CombinedResponseHandler.java:41)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.HandleResponseStage.execute(HandleResponseStage.java:50)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.HandleResponseStage.execute(HandleResponseStage.java:38)
	at software.amazon.awssdk.core.internal.http.pipeline.RequestPipelineBuilder$ComposingRequestPipelineStage.execute(RequestPipelineBuilder.java:206)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallAttemptTimeoutTrackingStage.execute(ApiCallAttemptTimeoutTrackingStage.java:72)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallAttemptTimeoutTrackingStage.execute(ApiCallAttemptTimeoutTrackingStage.java:42)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.TimeoutExceptionHandlingStage.execute(TimeoutExceptionHandlingStage.java:78)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.TimeoutExceptionHandlingStage.execute(TimeoutExceptionHandlingStage.java:40)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallAttemptMetricCollectionStage.execute(ApiCallAttemptMetricCollectionStage.java:55)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallAttemptMetricCollectionStage.execute(ApiCallAttemptMetricCollectionStage.java:39)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.RetryableStage.execute(RetryableStage.java:81)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.RetryableStage.execute(RetryableStage.java:36)
	at software.amazon.awssdk.core.internal.http.pipeline.RequestPipelineBuilder$ComposingRequestPipelineStage.execute(RequestPipelineBuilder.java:206)
	at software.amazon.awssdk.core.internal.http.StreamManagingStage.execute(StreamManagingStage.java:56)
	at software.amazon.awssdk.core.internal.http.StreamManagingStage.execute(StreamManagingStage.java:36)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallTimeoutTrackingStage.executeWithTimer(ApiCallTimeoutTrackingStage.java:80)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallTimeoutTrackingStage.execute(ApiCallTimeoutTrackingStage.java:60)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallTimeoutTrackingStage.execute(ApiCallTimeoutTrackingStage.java:42)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallMetricCollectionStage.execute(ApiCallMetricCollectionStage.java:50)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ApiCallMetricCollectionStage.execute(ApiCallMetricCollectionStage.java:32)
	at software.amazon.awssdk.core.internal.http.pipeline.RequestPipelineBuilder$ComposingRequestPipelineStage.execute(RequestPipelineBuilder.java:206)
	at software.amazon.awssdk.core.internal.http.pipeline.RequestPipelineBuilder$ComposingRequestPipelineStage.execute(RequestPipelineBuilder.java:206)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ExecutionFailureExceptionReportingStage.execute(ExecutionFailureExceptionReportingStage.java:37)
	at software.amazon.awssdk.core.internal.http.pipeline.stages.ExecutionFailureExceptionReportingStage.execute(ExecutionFailureExceptionReportingStage.java:26)
	at software.amazon.awssdk.core.internal.http.AmazonSyncHttpClient$RequestExecutionBuilderImpl.execute(AmazonSyncHttpClient.java:224)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.invoke(BaseSyncClientHandler.java:103)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.doExecute(BaseSyncClientHandler.java:173)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.lambda$execute$1(BaseSyncClientHandler.java:80)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.measureApiCallSuccess(BaseSyncClientHandler.java:182)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.execute(BaseSyncClientHandler.java:74)
	at software.amazon.awssdk.core.client.handler.SdkSyncClientHandler.execute(SdkSyncClientHandler.java:45)
	at software.amazon.awssdk.awscore.client.handler.AwsSyncClientHandler.execute(AwsSyncClientHandler.java:53)
	at software.amazon.awssdk.services.lambda.DefaultLambdaClient.invoke(DefaultLambdaClient.java:2792)
	at com.networknt.aws.lambda.handler.middleware.proxy.LambdaProxyMiddleware.invokeFunction(LambdaProxyMiddleware.java:104)
	at com.networknt.aws.lambda.handler.middleware.proxy.LambdaProxyMiddleware.execute(LambdaProxyMiddleware.java:66)
	at com.networknt.aws.lambda.handler.middleware.MiddlewareRunnable.run(MiddlewareRunnable.java:23)
	at java.base/java.lang.Thread.run(Thread.java:829)
	at com.networknt.aws.lambda.handler.chain.ChainLinkWorker.run(ChainLinkWorker.java:21)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
	at java.base/java.util.concurrent.FutureTask.run$$$capture(FutureTask.java:264)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:829)
```

## Lambda Native Proxy

### [About](doc/about.md)
### [Benefit](doc/benefit.md)
### [Limitation](doc/limitation.md)
### [Architecture](doc/architecture.md)
### [Design](doc/design.md)

## Middleware Handlers

### [AuditMiddleware](doc/audit.md)
### [LambdaProxyMiddleware](doc/proxy.md)

## Jackson 3 migration

This source line uses Jackson 3.2.3 and json-schema-validator 3.0.8. Build the Jackson 3 light-4j, http-client, REST and AWS Lambda prerequisites first; older snapshot binaries are not compatible. Jackson annotations retain their com.fasterxml.jackson.annotation package.

The runtime mapper uses Jackson2Defaults with its existing permissive unknown-property override. Mapper instances are immutable. Java time and Optional support are built in. Classic SnakeYAML compatibility pins are retained until the stack-wide cleanup.

Local qualification includes the full JVM suite, sanitized event/validation parity against the Jackson 2 baseline, and an offline GraalVM 25 native-image build. A native-image build does not establish deployed AWS behavior or complete reflection coverage for every middleware/configuration. The separate conformance worktree is deferred.

Native qualification now invokes the executable against a synthetic loopback Lambda Runtime API:

```sh
MAVEN_REPO_LOCAL=/path/to/isolated/m2 bash native-image-linux.sh
python3 tests/native_runtime_smoke.py target/custom-runtime/lambda-native
```

The smoke check binds an API Gateway event, runs the local health handler, and verifies success and builder-based error serialization. It uses no AWS endpoint or credentials. `-H:+AddAllCharsets` is required by SnakeYAML Engine's UTF-32 input detection. The check does not qualify every middleware chain or deployed AWS behavior.

### Reproducible Jackson 3 source build

`.github/workflows/jackson3-source-build.yml` builds the prerequisite source stack in a fresh isolated Maven repository and runs its full test suites. It resolves every prerequisite to a full commit SHA once and uploads `source-refs.json`, `source-commits.txt` and Surefire reports. The shared mapper policy prerequisite is light-4j #2821; merge that before its consumers.

The optional `source_refs` workflow-dispatch input replays recorded full SHAs. GitHub enables dispatch only after the workflow exists on the default branch; before merge, reproduce from the recorded source checkouts locally. A green local suite is separate from a successful remote source-build run.
