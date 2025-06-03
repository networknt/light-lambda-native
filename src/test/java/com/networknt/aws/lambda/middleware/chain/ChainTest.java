package com.networknt.aws.lambda.middleware.chain;

import com.networknt.aws.lambda.TestAsynchronousMiddleware;
import com.networknt.aws.lambda.TestSynchronousMiddleware;
import com.networknt.aws.lambda.handler.chain.Chain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class ChainTest {

    @Test
    void testSynchronous() {
        var testSynchronousMiddleware = new TestSynchronousMiddleware();
        var chain = new Chain();
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.setFinalized(true);

        Assertions.assertTrue(chain.isFinalized());
        Assertions.assertEquals(8, chain.getChainSize());

    }

    @Test
    void testAsynchronous() {
        var testAsynchronousMiddleware = new TestAsynchronousMiddleware();
        var chain = new Chain();
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.setFinalized(true);
        Assertions.assertTrue(chain.isFinalized());
        Assertions.assertEquals(8, chain.getChainSize());
    }

    @Test
    void testMixed() {
        var testSynchronousMiddleware = new TestSynchronousMiddleware();
        var testAsynchronousMiddleware = new TestAsynchronousMiddleware();
        var chain = new Chain();
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testAsynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.setFinalized(true);
        Assertions.assertTrue(chain.isFinalized());
        Assertions.assertEquals(5, chain.getChainSize());
    }

}
