package com.networknt.aws.lambda.handler.info;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.info.ServerInfoConfig;
import com.networknt.info.ServerInfoUtil;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ServerInfoHandler implements LambdaHandler {
    static final String STATUS_SERVER_INFO_DISABLED = "ERR10013";
    static final Logger logger = LoggerFactory.getLogger(ServerInfoHandler.class);

    public ServerInfoHandler() {
        logger.info("ServerInfoHandler is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (logger.isTraceEnabled()) logger.trace("ServerInfoHandler.handleRequest starts.");
        ServerInfoConfig config = ServerInfoConfig.load();
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        if (config.isEnableServerInfo()) {
            Map<String, Object> infoMap = ServerInfoUtil.getServerInfo(config);
            // TODO access the downstream to get the server info from the downstream
            var res = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(JsonMapper.toJson((infoMap)));

            exchange.setInitialResponse(res);

        } else {

            var status = new Status(STATUS_SERVER_INFO_DISABLED);
            var res = new APIGatewayProxyResponseEvent()
                    .withStatusCode(status.getStatusCode())
                    .withHeaders(headers)
                    .withBody(status.toString());
            exchange.setInitialResponse(res);
            return status;
        }
        if (logger.isTraceEnabled()) logger.trace("ServerInfoHandler.handleRequest ends.");
        return this.successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return ServerInfoConfig.load().isEnableServerInfo();
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }
}
