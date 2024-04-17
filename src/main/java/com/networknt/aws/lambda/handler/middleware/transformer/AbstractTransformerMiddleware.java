package com.networknt.aws.lambda.handler.middleware.transformer;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.config.Config;
import com.networknt.rule.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractTransformerMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTransformerMiddleware.class);
    static Map<String, Object> endpointRulesMap  = new HashMap<>();
    public static Map<String, Rule> rules;
    public static RuleEngine ruleEngine;

    public AbstractTransformerMiddleware() {
        if(LOG.isTraceEnabled()) LOG.trace("AbstractTransformerMiddleware is constructed");
        // load endpointRules
        RuleLoaderConfig ruleLoaderConfig = RuleLoaderConfig.load();
        if(ruleLoaderConfig.isEnabled()) {
            if(RuleLoaderConfig.RULE_SOURCE_CONFIG_FOLDER.equals(ruleLoaderConfig.getRuleSource())) {
                // load the rules for the service from the externalized config folder. The filename is rules.yml
                String ruleString = Config.getInstance().getStringFromFile("rules.yml");
                rules = RuleMapper.string2RuleMap(ruleString);
                if(LOG.isInfoEnabled()) LOG.info("Load YAML rules from config folder with size = " + rules.size());
                // load the endpoint rule mapping from the rule-loader.yml
                endpointRulesMap = ruleLoaderConfig.getEndpointRules();
            }
            if(rules != null) {
                // create the rule engine with the rule map.
                ruleEngine = new RuleEngine(rules, null);
                // iterate all action classes to initialize them to ensure that the jar file are deployed and configuration is registered.
                // This is to prevent runtime exception and also ensure that the configuration is part of the server info response.
                loadPluginClass();
            }
        } else {
            LOG.error("RuleLoaderConfig is not enabled. Please check the configuration.");
        }

    }

    public static void loadPluginClass() {
        // iterate the rules map to find the action classes.
        for(Rule rule: rules.values()) {
            for(RuleAction action: rule.getActions()) {
                String actionClass = action.getActionClassName();
                loadActionClass(actionClass);
            }
        }
    }
    public static void loadActionClass(String actionClass) {
        if(LOG.isDebugEnabled()) LOG.debug("load action class " + actionClass);
        try {
            IAction ia = (IAction)Class.forName(actionClass).getDeclaredConstructor().newInstance();
            // this happens during the server startup, so the cache must be empty. No need to check.
            ruleEngine.actionClassCache.put(actionClass, ia);
        } catch (Exception e) {
            LOG.error("Exception:", e);
            throw new RuntimeException("Could not find rule action class " + actionClass, e);
        }
    }

    public static Map<String, String> convertMapValueToString(Map<String, Object> originalMap) {
        Map<String, String> convertedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString(); // Convert Object to String
            convertedMap.put(key, value);
        }
        return convertedMap;
    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

    @Override
    public boolean isContinueOnFailure() {
        return false;
    }

    @Override
    public boolean isAudited() {
        return false;
    }

    @Override
    public void getCachedConfigurations() {

    }

}
