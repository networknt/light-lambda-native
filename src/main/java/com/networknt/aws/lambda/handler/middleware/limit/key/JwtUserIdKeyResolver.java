package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware;
import com.networknt.utility.Constants;

import java.util.Map;

/**
 * When user is selected as the key, we can get the user_id from the JWT claim. In this way, we
 * can limit a number of requests for a user to prevent abuse from a single page application that
 * is using the backend APIs.
 *
 * @author Steve Hu
 *
 */
public class JwtUserIdKeyResolver implements KeyResolver {
    @Override
    public String resolve(LightLambdaExchange exchange) {
        String key = null;
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getRequestAttachment(AuditMiddleware.AUDIT_ATTACHMENT_KEY);
        if(auditInfo != null) {
            key = (String)auditInfo.get(Constants.USER_ID_STRING);
        }
        return key;
    }
}
