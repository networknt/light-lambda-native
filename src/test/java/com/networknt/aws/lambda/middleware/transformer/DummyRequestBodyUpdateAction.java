package com.networknt.aws.lambda.middleware.transformer;

import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.rule.IAction;
import com.networknt.rule.RuleActionValue;
import com.networknt.rule.RuleConstants;
import com.networknt.rule.exception.RuleEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * Dummy action class to test the request transformer middleware. This action updates the request body to add a new field.
 *
 */
public class DummyRequestBodyUpdateAction implements IAction {
    private static final Logger logger = LoggerFactory.getLogger(DummyRequestBodyUpdateAction.class);

    @Override
    public void performAction(String ruleId, String actionId, Map<String, Object> objMap, Map<String, Object> resultMap, Collection<RuleActionValue> actionValues) throws RuleEngineException {
        resultMap.put(RuleConstants.RESULT, true);
        String requestBody = (String)objMap.get("requestBody");
        if(logger.isTraceEnabled()) logger.debug("original request body = " + requestBody);
        // convert the body from string to json map or list.
        try {
            Object body = Config.getInstance().getMapper().readValue(requestBody, Object.class);
            if(body instanceof Map) {
                Map<String, Object> bodyMap = (Map<String, Object>)body;
                bodyMap.put("newField", "newValue");
                requestBody = JsonMapper.toJson(bodyMap);
            } else {
                // if the body is not a map or list, then it is a string and we cannot encode it.
                if(logger.isTraceEnabled()) logger.trace("request body is not a map, skip update.");
            }
        } catch (Exception e) {
            logger.error("Exception:", e);
        }
        if(logger.isTraceEnabled()) logger.trace("updated request body = " + requestBody);
        resultMap.put("requestBody", requestBody);
    }
}
