package com.networknt.aws.lambda.handler.info;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.info.ServerInfoConfig;
import com.networknt.info.ServerInfoUtil;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ServerInfoHandler implements LambdaHandler {
    static final String STATUS_SERVER_INFO_DISABLED = "ERR10013";
    static final Logger logger = LoggerFactory.getLogger(ServerInfoHandler.class);
    static ServerInfoConfig config;

    public ServerInfoHandler() {
        logger.info("ServerInfoHandler is constructed");
        config = ServerInfoConfig.load();
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if (logger.isTraceEnabled()) logger.trace("ServerInfoHandler.handleRequest starts.");
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        if (config.isEnableServerInfo()) {
            Map<String, Object> infoMap = ServerInfoUtil.getServerInfo(config);
            // TODO access the downstream to get the server info from the downstream
            var res = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(JsonMapper.toJson((infoMap)));

            exchange.setResponse(res);

        } else {

            var status = new Status(STATUS_SERVER_INFO_DISABLED);
            var res = new APIGatewayProxyResponseEvent()
                    .withStatusCode(status.getStatusCode())
                    .withHeaders(headers)
                    .withBody(status.toString());
            exchange.setResponse(res);
            return status;
        }
        if (logger.isTraceEnabled()) logger.trace("ServerInfoHandler.handleRequest ends.");
        return this.successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnableServerInfo();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                ServerInfoConfig.CONFIG_NAME,
                ServerInfoHandler.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(ServerInfoConfig.CONFIG_NAME),
                null);
    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }
}
