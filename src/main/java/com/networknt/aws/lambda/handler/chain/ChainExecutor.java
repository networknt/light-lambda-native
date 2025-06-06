package com.networknt.aws.lambda.handler.chain;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class ChainExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(ChainExecutor.class);
    private final LinkedList<Status> chainResults = new LinkedList<>();
    private static final String MIDDLEWARE_UNHANDLED_EXCEPTION = "ERR14004";

    public void executeChain(final LightLambdaExchange exchange, final Chain chain) {

        if (!chain.isFinalized()) {
            LOG.error("Execution attempt on a chain that is not finalized! Call 'finalizeChain' before 'executeChain'");
            return;
        }

        for(var handler : chain.getChain()) {
            if (!handler.isEnabled()) {
                LOG.debug("Skipping disabled handler: {}", handler.getClass().getName());
                continue;
            }
            Status status = null;
            try {
                status = handler.execute(exchange);
                exchange.updateExchangeStatus(status);
                addChainableResult(status);
            } catch (Exception e) {
                LOG.error("Exception in handler: {}", handler.getClass().getName(), e);
                addChainableResult(new Status(MIDDLEWARE_UNHANDLED_EXCEPTION));
            }
            if (exchange.hasFailedState()) {
                break;
            }
        }
    }

    protected void addChainableResult(Status result) {
        this.chainResults.add(result);
    }

    public List<Status> getChainResults() {
        return chainResults;
    }

}
