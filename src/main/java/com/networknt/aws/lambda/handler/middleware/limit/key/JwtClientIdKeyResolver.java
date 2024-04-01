package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware;
import com.networknt.utility.Constants;

import java.util.Map;

/**
 * When the rate limit handler is located after the JwtVerifierHandler in the request/response chain, we can
 * get the client_id claim from the JWT token from the auditInfo object from the exchange attachment. In this
 * way, we can set up rate limit per client_id to give priority client more access to our services.
 *
 * @author Steve Hu
 */
public class JwtClientIdKeyResolver implements KeyResolver {

    @Override
    public String resolve(LightLambdaExchange exchange) {
        String key = null;
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getRequestAttachment(AuditMiddleware.AUDIT_ATTACHMENT_KEY);
        if(auditInfo != null) {
            key = (String)auditInfo.get(Constants.CLIENT_ID_STRING);
        }
        return key;
    }
}
