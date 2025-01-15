package com.networknt.aws.lambda.middleware.transformer;

import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.rule.IAction;
import com.networknt.rule.RuleActionValue;
import com.networknt.rule.RuleConstants;
import com.networknt.rule.exception.RuleEngineException;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * This action class is returning a status error directly to prevent the request from being processed.
 *
 */
public class DummyRequestValidationAction implements IAction {
    private static final Logger logger = LoggerFactory.getLogger(DummyRequestValidationAction.class);

    @Override
    public void performAction(String ruleId, String actionId, Map<String, Object> objMap, Map<String, Object> resultMap, Collection<RuleActionValue> actionValues) throws RuleEngineException {
        resultMap.put(RuleConstants.RESULT, true);
        Status status = new Status("ERR10001", "Request validation failed");
        resultMap.put("responseBody", status.toString());
        resultMap.put("statusCode", status.getStatusCode());
    }
}
