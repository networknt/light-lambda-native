The AuditMiddleware implements the same cross-cutting concern like the light-4j [AuditHandler](https://doc.networknt.com/concern/audit/) to capture the important information about the application for business partners. It shares the same [audit.yml](https://github.com/networknt/light-4j/blob/master/audit-config/src/main/resources/config/audit.yml) config file.

For each request that is proxied by the lambda-native to the downstream Lambda function, we will create an audit log in the cloud watch. In order to help users to inject the audit log into different subsystems for further processing, we have added "LambdaNativeAuditLog" as the prefix for the log entry. Here is an example.

```
2024-06-10T16:17:44.195-04:00	LambdaNativeAuditLog {"timestamp":1718050663634,"X-Correlation-Id":"3YpCUDlMTFiQ9hJfCWfnYw","serviceId":"com.networknt.market-1.0.1","statusCode":200,"responseTime":561}
```

For more information on how to config the AuditMiddleware, please refer to [AuditHandler](https://doc.networknt.com/concern/audit/)
