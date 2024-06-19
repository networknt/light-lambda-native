Light Lambda Native Proxy Pattern is a new pattern for addressing Cross-Cutting Concerns(CCC) for Lambda functions deployed on AWS.

The new pattern will allow users to take advantage of the Light API Platform without an http-sidecar container on Kubernetes or a standalone light-gateway. It is a pure Lambda function that addresses all Cross-Cutting Concerns and then invokes the downstream business Lambda function that can be built with any language.

Although both http-sidecar and light-gateway can be used to address Cross-Cutting Concerns for Lambda functions, they add additional servers/services alongside native AWS services. For a team that is working with a Serverless environment, managing an HTTP sidecar container or a light gateway EC2 instance would be cumbersome and costly.
