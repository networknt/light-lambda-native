package com.networknt.aws.lambda.handler.chain;

import com.networknt.aws.lambda.handler.LambdaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;

public class Chain {
    private static final Logger LOG = LoggerFactory.getLogger(Chain.class);
    private final LinkedList<LambdaHandler> chainedHandlers = new LinkedList<>();
    private boolean isFinalized;
    public Chain() {
        this.isFinalized = false;
    }

    public void addChainable(LambdaHandler chainable) {

        if (!this.isFinalized)
            this.chainedHandlers.add(chainable);

        else LOG.error("Attempting to add chain link after chain has been finalized!");
    }

    public boolean isFinalized() {
        return isFinalized;
    }

    public void setFinalized(boolean finalized) {
        this.isFinalized = finalized;
    }

    public int getChainSize() {
        return this.chainedHandlers.size();
    }


    /**
     * Add to chain from string parameter
     *
     * @param className - class name in string format
     * @return - this
     */
    @SuppressWarnings("unchecked")
    public Chain add(String className) {
        try {

            if (Class.forName(className).getSuperclass().equals(LambdaHandler.class))
                return this.add((Class<? extends LambdaHandler>) Class.forName(className));

            else throw new RuntimeException(className + " is not a member of LambdaMiddleware...");

        } catch (ClassNotFoundException e) {
            LOG.error("Failed to find class with the name: {}", className);
            throw new RuntimeException(e);
        }
    }

    /**
     * Add to chain from class parameter
     * @param  middleware - middleware class
     * @return - this
     */
    public Chain add(Class<? extends LambdaHandler> middleware) {

        try {
            var newClazz = middleware.getConstructor()
                    .newInstance();
            this.chainedHandlers.add(newClazz);
            int linkNumber = this.chainedHandlers.size();
            LOG.debug("Created new middleware instance: {}[{}]", middleware.getName(), linkNumber);

        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            LOG.error("failed to create class instance: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        return this;
    }

    public LinkedList<LambdaHandler> getChain() {
        return chainedHandlers;
    }

}
