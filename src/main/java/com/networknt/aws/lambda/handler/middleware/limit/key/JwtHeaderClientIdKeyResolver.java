package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware;
import com.networknt.utility.Constants;
import com.networknt.utility.MapUtil;

import java.util.Map;
import java.util.Optional;

/**
 * This is a customized KeyResolver for one of our customers on the external gateway in the DMZ.
 * There are many external clients that are using the Okta JWT token to access the internal APIs.
 * However, some external clients doesn't support OAuth 2.0, so they will put a client_id and
 * client_secret in the request header to authenticate themselves. So we need to check the JWT
 * token first and then get the client_id from the header second if the JWT doesn't exist.
 *
 * @author Steve Hu
 */
public class JwtHeaderClientIdKeyResolver implements KeyResolver {

    @Override
    public String resolve(LightLambdaExchange exchange) {
        String key = null;
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AuditMiddleware.AUDIT_ATTACHMENT_KEY);
        if(auditInfo != null) {
            key = (String)auditInfo.get(Constants.CLIENT_ID_STRING);
        }
        if(key == null) {
            // try to get the key from the header
            Map<String, String> headerMap = exchange.getRequest().getHeaders();
            Optional<String> optionalValue = MapUtil.getValueIgnoreCase(headerMap, "Client-Id");
            if(optionalValue.isPresent()) key = optionalValue.get();
        }
        return key;
    }
}
