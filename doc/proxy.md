LambdaProxyMiddleware is responsible for invoking the downstream business Lambda functions after addressing all request cross-cutting concerns. Unlike the light-gateway and http-sidecar proxy handler to invoke downstream API with HTTP request, it invokes the Lambda functions with Java AWS SDK version 2.

Here is the default [lambda-proxy.yml](https://github.com/networknt/light-lambda-native/blob/master/src/main/resources/config/lambda-proxy.yml) and all properties can be overwritten by the values.yml config file.

And here is a section in values.yml for the lambda-market application.

```
# lambda-proxy.yml
lambda-proxy.region: us-east-2
lambda-proxy.apiCallTimeout: 360000
lambda-proxy.apiCallAttemptTimeout: 120000
lambda-proxy.functions:
  /market/{store}/products@get: MarketStoreProductsGetFunction
  /market/{store}/products@post: MarketStoreProductsPostFunction
```

### How to configure the timeout

For some of the business Lambda functions, it takes longer than 20 seconds to complete the request, to allow the Proxy Lambda to invoke them, we need to increase the default timeout setting for both Lambda functions and the AWS SDK apiCallTimeout and apiCallAttemptTimeout.

The AWS SDK client has an internal default retry policy of 3 max retries currently (It used to be 4), and we cannot change it programmatically. Maybe there is an API for it but I couldn't find it from the document and source code.

The following is the section in lambda-proxy.yml for timeout settings.

```
# Api call timeout in milliseconds. This sets the amount of time for the entire execution, including all retry attempts.
apiCallTimeout: ${lambda-proxy.apiCallTimeout:60000}
# Api call attempt timeout in milliseconds. This sets the amount of time for each individual attempt.
apiCallAttemptTimeout: ${lambda-proxy.apiCallAttemptTimeout:20000}
```

Based on the assumption of 3 retries maximum, the apiCallTimeout should be three times of apiCallAttemptTimeout. The default 20 seconds for apiCallAttemptTimeout is based on the default Lambda function timeout on AWS. However, the majority of the examples on the Internet have the same value for both. The following is a use case that we can use to explain how to set the two values for maximum control.

A UI consumer application connects lambda-native proxy and then calls to a Python Lambda function to interact with BedRock API for large language model chat. The BedRock response might take longer than 20 seconds. For example, it might take up to 2 minutes to get the response back.

In the above case, we can set the Python Lambda function timeout to 2 minutes. Also, we set the apiCallAttemptTimeout to 2 minutes on the lambda-native proxy.

From the consumer perspective, if users can tolerate long waiting, we can set up the apiCallTimeout to 6 minutes and also set the lambda-native proxy function timeout to 6 minutes. It will allow the lambda-native proxy to retry three times and chances are you might have the response back.

If you don't need the fine-grained control, then you can set both apiCallAttemptTimeout and apiCallTimeout to 2 minutes, and both Lambda functions to 2 minutes. It will force the Lambda functions to time out at 2 minutes without retrying.

You can also set all four values to 6 minutes to allow the retry without controlling the downstream Python Lambda function differently.

In summary, we expose both values in the lambda-proxy.yml to allow users to configure the timeouts separately for both the proxy Lambda and business Lambda if users understand what is the difference between those two values. Also, users can choose the same value for both Lambdas with consideration of retrying or not. For most users, the default values should be good enough, and they don't need to customize the timeouts if their Lambda functions are fast.
