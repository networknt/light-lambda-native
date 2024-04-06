package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.exception.LambdaExchangeStateException;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.chain.PooledChainLinkExecutor;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.ExceptionUtil;
import com.networknt.aws.lambda.listener.LambdaExchangeFailureListener;
import com.networknt.aws.lambda.listener.LambdaRequestCompleteListener;
import com.networknt.aws.lambda.listener.LambdaResponseCompleteListener;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;


/**
 * Shared object among middleware threads containing information on the request/response event.
 */
public final class LightLambdaExchange {
    private static final Logger LOG = LoggerFactory.getLogger(LightLambdaExchange.class);
    private APIGatewayProxyRequestEvent request;
    private InputStream streamRequest;
    private APIGatewayProxyResponseEvent response;
    private OutputStream streamResponse;
    private final Context context;
    private final Map<Attachable<?>, Object> attachments = Collections.synchronizedMap(new HashMap<>());
    private final PooledChainLinkExecutor executor;
    private final List<LambdaResponseCompleteListener> responseCompleteListeners = Collections.synchronizedList(new ArrayList<>());
    private final List<LambdaRequestCompleteListener> requestCompleteListeners = Collections.synchronizedList(new ArrayList<>());
    private final List<LambdaExchangeFailureListener> exchangeFailedListeners = Collections.synchronizedList(new ArrayList<>());

    // Initial state
    private static final int INITIAL_STATE = 0;

    // Request data has been initialized in the exchange.
    private static final int FLAG_REQUEST_SET = 1 << 1;

    // Request portion of the exchange is complete.
    private static final int FLAG_REQUEST_DONE = 1 << 2;

    // Failure occurred during request execution.
    private static final int FLAG_REQUEST_HAS_FAILURE = 1 << 3;

    // Response data has been initialized in the exchange.
    private static final int FLAG_RESPONSE_SET = 1 << 4;

    // Response portion of the exchange is complete.
    private static final int FLAG_RESPONSE_DONE = 1 << 5;

    // Failure occurred during response execution.
    private static final int FLAG_RESPONSE_HAS_FAILURE = 1 << 6;

    // The chain has been fully executed
    private static final int FLAG_CHAIN_EXECUTED = 1 << 7;

    // the exchange is complete
    private static final int FLAG_EXCHANGE_COMPLETE = 1 << 8;
    private int state = INITIAL_STATE;
    private int statusCode = 200;
    private final Chain chain;

    public LightLambdaExchange(Context context, Chain chain) {
        this.context = context;
        this.chain = chain;
        this.executor = new PooledChainLinkExecutor();
    }

    public void executeChain() {

        if (stateHasAnyFlags(FLAG_CHAIN_EXECUTED | FLAG_EXCHANGE_COMPLETE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_CHAIN_EXECUTED | FLAG_EXCHANGE_COMPLETE);

        if (stateHasAnyFlagsClear(FLAG_REQUEST_SET))
            throw LambdaExchangeStateException
                    .missingStateException(this.state, FLAG_REQUEST_SET);

        this.executor.executeChain(this, this.chain);
        this.state |= FLAG_CHAIN_EXECUTED;
    }

    /**
     * Sets the response object of the exchange.
     *
     * @param response -
     */
    public void setInitialResponse(final APIGatewayProxyResponseEvent response) {

        if (stateHasAnyFlags(FLAG_RESPONSE_SET))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_RESPONSE_SET);

        this.response = response;
        this.statusCode = response.getStatusCode();
        this.state |= FLAG_RESPONSE_SET;
    }

    /**
     * Sets the request object of the exchange.
     *
     * @param request -
     */
    public void setInitialRequest(APIGatewayProxyRequestEvent request) {

        if (this.state != INITIAL_STATE)
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, INITIAL_STATE);


        this.request = request;
        this.state |= FLAG_REQUEST_SET;
    }

    /**
     * Returns the response object or an exception object if there was a failure.
     *
     * @return - return formatted response event.
     */
    public APIGatewayProxyResponseEvent getResponse() {

        if (stateHasAnyFlags(FLAG_CHAIN_EXECUTED | FLAG_EXCHANGE_COMPLETE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_CHAIN_EXECUTED | FLAG_EXCHANGE_COMPLETE);

        if (stateHasAnyFlagsClear(FLAG_RESPONSE_SET))
            throw LambdaExchangeStateException
                    .missingStateException(this.state, FLAG_RESPONSE_SET);

        if (stateHasAnyFlags(FLAG_RESPONSE_DONE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_RESPONSE_DONE);

        return response;
    }

    public Context getContext() {
        return context;
    }

    public APIGatewayProxyRequestEvent getRequest() {

        if (stateHasAnyFlags(FLAG_CHAIN_EXECUTED | FLAG_EXCHANGE_COMPLETE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_CHAIN_EXECUTED | FLAG_EXCHANGE_COMPLETE);

        if (stateHasAnyFlagsClear(FLAG_REQUEST_SET))
            throw LambdaExchangeStateException
                    .missingStateException(this.state, FLAG_REQUEST_SET);

        if (stateHasAnyFlags(FLAG_REQUEST_DONE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_REQUEST_DONE);


        return request;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Terminates the request portion of the exchange and invokes the exchangeRequestCompleteListeners.
     * You cannot finalize a request that has already been finalized.
     *
     * @return - returns the complete and final request event.
     */
    public APIGatewayProxyRequestEvent getFinalizedRequest(boolean fromListener) {
        if(!fromListener) {
            // the call any listener should not invoke listener again to prevent deal loop.
            for (int i = requestCompleteListeners.size() - 1; i >= 0; --i) {
                LambdaRequestCompleteListener listener = requestCompleteListeners.get(i);
                listener.requestCompleteEvent(this);
                requestCompleteListeners.remove(i);
            }
        }

        if (stateHasAnyFlags(FLAG_REQUEST_DONE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_REQUEST_DONE);

        if (stateHasAnyFlagsClear(FLAG_REQUEST_SET))
            throw LambdaExchangeStateException
                    .missingStateException(this.state, FLAG_REQUEST_SET);

        if(!fromListener) {
            this.state |= FLAG_REQUEST_DONE;
        }

        return request;
    }

    /**
     * Terminates the response portion of the exchange and invokes the exchangeResponseCompleteListeners.
     * You cannot finalize a response that has already been finalized.
     *
     * @return - returns the complete and final response event.
     */
    public APIGatewayProxyResponseEvent getFinalizedResponse(boolean fromListener) {
        if(!fromListener) {
            // the call any listener should not invoke listener again to prevent deal loop.
            for (int i = responseCompleteListeners.size() - 1; i >= 0; --i) {
                LambdaResponseCompleteListener listener = responseCompleteListeners.get(i);
                listener.responseCompleteEvent(this);
                responseCompleteListeners.remove(i);
            }
        }
        /*
         * Check for failures first because a failed request could mean
         * that we never set the response in the first place.
         */
        if (stateHasAnyFlags(FLAG_REQUEST_HAS_FAILURE | FLAG_RESPONSE_HAS_FAILURE)) {
            LOG.error("Exchange has an error, returning middleware status.");
            this.state |= FLAG_RESPONSE_DONE;
            return ExceptionUtil.convert(this.executor.getChainResults());
        }

        if (stateHasAnyFlagsClear(FLAG_CHAIN_EXECUTED))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_CHAIN_EXECUTED);

        if (stateHasAnyFlags(FLAG_RESPONSE_DONE | FLAG_EXCHANGE_COMPLETE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_RESPONSE_DONE | FLAG_EXCHANGE_COMPLETE);

        if (stateHasAnyFlagsClear(FLAG_RESPONSE_SET))
            throw LambdaExchangeStateException
                    .missingStateException(this.state, FLAG_RESPONSE_SET);

        if(!fromListener) {
            this.state |= FLAG_RESPONSE_DONE;
            this.state |= FLAG_EXCHANGE_COMPLETE;
        }
        return response;
    }

    /**
     * Update the exchange. This happens automatically after each middleware execution,
     * but it can be invoked manually by user logic.
     * <p>
     * Once an exchange is marked as failed, no longer handle updates.
     *
     * @param status - status to update the exchange with.
     */
    public void updateExchangeStatus(final Status status) {

        /* No need to update anything if the exchange is marked as complete/failed. */
        if (stateHasAnyFlags(FLAG_REQUEST_HAS_FAILURE | FLAG_RESPONSE_HAS_FAILURE | FLAG_EXCHANGE_COMPLETE)) {
            return;
        }

        if (status.getSeverity().startsWith("ERR")) {

            int oldState = this.state;

            /* if we are making the update from a handler or a request complete listener. */
            if ((this.isRequestInProgress() && !this.isResponseInProgress())
                    || (this.isRequestComplete() && !this.isResponseInProgress())) {
                LOG.error("Exchange has an error in the request phase.");
                this.statusCode = status.getStatusCode();
                this.state |= FLAG_REQUEST_HAS_FAILURE;

            } else if (this.isResponseInProgress()) {
                LOG.error("Exchange has an error in the response phase.");
                this.statusCode = status.getStatusCode();
                this.state |= FLAG_RESPONSE_HAS_FAILURE;
            }

            /* if a failure occurred, run failure listeners */
            if (oldState != this.state) {
                for (var failureListener : this.exchangeFailedListeners)
                    failureListener.exchangeFailedEvent(this);
            }
        }
    }

    /**
     * Check to see if the exchange has any error state at all.
     *
     * @return - returns true if the exchange has a failure state.
     */
    public boolean hasFailedState() {
        return stateHasAnyFlags(FLAG_REQUEST_HAS_FAILURE | FLAG_RESPONSE_HAS_FAILURE);
    }

    /**
     * Checks to see if the exchange is in the 'request in progress' state.
     * The exchange is in the request state when the request chain is ready and has not finished executing.
     *
     * @return - returns true if the exchange is handing the request.
     */
    public boolean isRequestInProgress() {
        return this.stateHasAllFlags(FLAG_REQUEST_SET)
                && this.stateHasAllFlagsClear(FLAG_REQUEST_DONE);
    }

    public boolean isRequestComplete() {
        return this.stateHasAllFlags(FLAG_REQUEST_DONE);
    }

    /**
     * Checks to see if the exchange is in the response in progress state.
     * The exchange is in the response state when the request chain is complete, and the response chain is ready and has not finished executing.
     *
     * @return - return true if the exchange is handling the response.
     */
    public boolean isResponseInProgress() {
        return this.stateHasAllFlags(FLAG_REQUEST_DONE | FLAG_RESPONSE_SET)
                && this.stateHasAllFlagsClear(FLAG_RESPONSE_DONE);
    }

    /**
     * Checks to see if the exchange has the response portion complete.
     *
     * @return - true if the response is complete.
     */
    public boolean isResponseComplete() {
        return this.stateHasAllFlags(FLAG_RESPONSE_DONE);
    }

    /**
     * Checks to see if the exchange is complete.
     *
     * @return - true if the exchange is complete.
     */
    public boolean isExchangeComplete() {
        return this.stateHasAllFlags(FLAG_EXCHANGE_COMPLETE);
    }

    /**
     * @return int state
     */
    public int getState() {
        return state;
    }

    /**
     * Checks to see if any of the provided flags is true.
     *
     * @param flags - flags to check.
     * @return - returns true if any flags are true.
     */
    private boolean stateHasAnyFlags(final int flags) {
        return (this.state & flags) != 0;
    }

    /**
     * Checks to see if any of the provided flags are not set.
     *
     * @param flags - flags to check.
     * @return - returns true if any of the provided flags are false.
     */
    private boolean stateHasAnyFlagsClear(final int flags) {
        return (this.state & flags) != flags;
    }

    /**
     * Checks to see if all provided flags are set.
     *
     * @param flags - flags to check.
     * @return - returns true if all provided flags are true.
     */
    private boolean stateHasAllFlags(final int flags) {
        return (this.state & flags) == flags;
    }

    /**
     * Checks to see if all provided flags are not set.
     *
     * @param flags - flags to check.
     * @return - returns true if all flags are false.
     */
    private boolean stateHasAllFlagsClear(final int flags) {
        return (this.state & flags) == 0;
    }

    public LightLambdaExchange addExchangeFailedListener(final LambdaExchangeFailureListener listener) {

        if (this.stateHasAnyFlags(FLAG_RESPONSE_DONE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_RESPONSE_DONE);

        this.exchangeFailedListeners.add(listener);
        return this;
    }

    public LightLambdaExchange addResponseCompleteListener(final LambdaResponseCompleteListener listener) {

        if (this.stateHasAnyFlags(FLAG_RESPONSE_DONE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_RESPONSE_DONE);

        this.responseCompleteListeners.add(listener);
        return this;
    }

    public LightLambdaExchange addRequestCompleteListener(final LambdaRequestCompleteListener listener) {

        if (this.stateHasAnyFlags(FLAG_REQUEST_DONE | FLAG_RESPONSE_DONE))
            throw LambdaExchangeStateException
                    .invalidStateException(this.state, FLAG_REQUEST_DONE | FLAG_RESPONSE_DONE);


        this.requestCompleteListeners.add(listener);
        return this;
    }

    /**
     * Adds an attachment to the exchange.
     *
     * @param key - Attachable key.
     * @param o   - object value.
     * @param <T> - Middleware key type.
     */
    public <T extends MiddlewareHandler> void addAttachment(final Attachable<T> key, final Object o) {
        this.attachments.put(key, o);
    }

    /**
     * Get attachment object for given key.
     *
     * @param attachable - middleware key
     * @return - returns the object for the provided key. Can return null if it does not exist.
     */
    public Object getAttachment(final Attachable<?> attachable) {
        return this.attachments.get(attachable);
    }

    public Map<Attachable<?>, Object> getAttachments() {
        return attachments;
    }

    /**
     * Attachment key class to attach data to the exchange.
     *
     * @param <T> -
     */
    public static class Attachable<T extends MiddlewareHandler> {
        private final Class<T> key;

        private Attachable(Class<T> key) {
            this.key = key;
        }

        public Class<T> getKey() {
            return key;
        }

        /**
         * Creates a new attachable key.
         *
         * @param middleware - class to create a key for.
         * @param <T>        - given class has to implement the MiddlewareHandler interface.
         * @return - returns new attachable instance.
         */
        public static <T extends MiddlewareHandler> Attachable<T> createAttachable(final Class<T> middleware) {
            return new Attachable<>(middleware);
        }
    }

    @Override
    public String toString() {
        return "LightLambdaExchange{" +
                "request=" + request +
                ", response=" + response +
                ", context=" + context +
                ", attachments=" + attachments +
                ", executor=" + executor +
                ", state=" + state +
                ", statusCode=" + statusCode +
                '}';
    }
}
