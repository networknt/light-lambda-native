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
22:38:36.103 [main] DEBUG software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain -- Unable to load credentials from SystemPropertyCredentialsProvider(): Unable to load credentials from system settings. Access key must be specified either via environment variable (AWS_ACCESS_KEY_ID) or system property (aws.accessKeyId).
software.amazon.awssdk.core.exception.SdkClientException: Unable to load credentials from system settings. Access key must be specified either via environment variable (AWS_ACCESS_KEY_ID) or system property (aws.accessKeyId).
	at software.amazon.awssdk.core.exception.SdkClientException$BuilderImpl.build(SdkClientException.java:111)
	at software.amazon.awssdk.auth.credentials.internal.SystemSettingsCredentialsProvider.resolveCredentials(SystemSettingsCredentialsProvider.java:58)
	at software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain.resolveCredentials(AwsCredentialsProviderChain.java:96)
	at software.amazon.awssdk.auth.credentials.internal.LazyAwsCredentialsProvider.resolveCredentials(LazyAwsCredentialsProvider.java:45)
	at software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.resolveCredentials(DefaultCredentialsProvider.java:126)
	at software.amazon.awssdk.core.internal.util.MetricUtils.measureDuration(MetricUtils.java:50)
	at software.amazon.awssdk.awscore.internal.authcontext.AwsCredentialsAuthorizationStrategy.resolveCredentials(AwsCredentialsAuthorizationStrategy.java:100)
	at software.amazon.awssdk.awscore.internal.authcontext.AwsCredentialsAuthorizationStrategy.addCredentialsToExecutionAttributes(AwsCredentialsAuthorizationStrategy.java:77)
	at software.amazon.awssdk.awscore.internal.AwsExecutionContextBuilder.invokeInterceptorsAndCreateExecutionContext(AwsExecutionContextBuilder.java:123)
	at software.amazon.awssdk.awscore.client.handler.AwsSyncClientHandler.invokeInterceptorsAndCreateExecutionContext(AwsSyncClientHandler.java:69)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.lambda$execute$1(BaseSyncClientHandler.java:78)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.measureApiCallSuccess(BaseSyncClientHandler.java:179)
	at software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler.execute(BaseSyncClientHandler.java:76)
	at software.amazon.awssdk.core.client.handler.SdkSyncClientHandler.execute(SdkSyncClientHandler.java:45)
	at software.amazon.awssdk.awscore.client.handler.AwsSyncClientHandler.execute(AwsSyncClientHandler.java:56)
	at software.amazon.awssdk.services.lambda.DefaultLambdaClient.invoke(DefaultLambdaClient.java:2694)
	at com.networknt.aws.lambda.proxy.LambdaProxy.invokeFunction(LambdaProxy.java:132)
	at com.networknt.aws.lambda.proxy.LambdaProxy.handleRequest(LambdaProxy.java:94)
	at com.networknt.aws.lambda.Main.main(Main.java:157)
```
