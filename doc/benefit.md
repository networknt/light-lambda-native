* Full Serverless: No need to manage containers on Kubernetes or standalone gateways.
* Avoid Lock-In: Java Middleware Handlers can integrate with proprietary services and can be ported between cloud vendors.
* Open Source: Cross-cutting concerns are supported by the Light-4j open-source community.
* Code Generation: Scaffold a project based on the OpenAPI 3 specification with SAM template.
* Configuration Driven: Lambda Native Proxy is deployed based on the configuration from the deployment pipeline for values.yml
* Segregation of Concerns: Light Native Lambda will implement API CCC and the Lambda function API will implement business logic. Therefore, only Authentication and Authorization requests will invoke Business Lambda functions.
* Friendly URL: Map AWS ALB auto-gen static DNS to friendly alias URL for VPC and Lambda Function URLs for the public cloud.
