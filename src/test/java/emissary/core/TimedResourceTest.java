package emissary.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Timer;
import emissary.place.IServiceProviderPlace;
import emissary.place.sample.DevNullPlace;
import emissary.test.core.UnitTest;
import emissary.test.core.extensions.TestAttempts;
import org.junit.jupiter.api.BeforeEach;

class TimedResourceTest extends UnitTest {
    private IServiceProviderPlace tp;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        tp = new DevNullPlace();
    }

    @TestAttempts
    void testCheckState() {
        TestMobileAgent tma = new TestMobileAgent();
        try (TimedResource tr = new TimedResource(tma, tp, -2, new Timer())) {
            // should never time out
            assertFalse(tr.checkState(System.currentTimeMillis()));
            // still running
            assertTrue(tma.latch.getCount() > 0);
            // cause thread to die
            tma.latch.countDown();
        }
    }

    @TestAttempts
    void testInterrupted() throws Exception {
        TestMobileAgent tma = new TestMobileAgent();
        // timeout almost immediately
        try (TimedResource tr = new TimedResource(tma, tp, 1, new Timer())) {
            Thread.sleep(100);
            // still running, but should be interrupted by this
            assertFalse(tr.checkState(System.currentTimeMillis()));
            tma.latch.await(5, TimeUnit.SECONDS);
            assertTrue(tma.interrupted);
        }
    }

    @TestAttempts
    void testDontInterruptAgent() {
        TestMobileAgent tma = new TestMobileAgent();
        // little time
        try (TimedResource first = new TimedResource(tma, tp, 1, new Timer())) {
            first.close();
            // long time
            try (TimedResource second = new TimedResource(tma, tp, 1000000, new Timer())) {
                // simulate finished processing within place
                // should indicate complete
                assertTrue(first.checkState(System.currentTimeMillis()));
                // try to interrupt directly
                first.interruptAgent();
                // should not have been interrupted
                assertTrue(tma.latch.getCount() > 0L);
                tma.latch.countDown();
            }
        }
    }

    static class TestMobileAgent extends HDMobileAgent {
        private static final long serialVersionUID = 1L;

        CountDownLatch latch = new CountDownLatch(1);

        volatile boolean interrupted;

        public TestMobileAgent() {
            super();
        }

        @Override
        public void run() {
            try {
                while (latch == null) {
                    // make the object is created
                    Thread.sleep(100);
                }
                latch.await();
            } catch (InterruptedException ex) {
                interrupted = true;
            } finally {
                latch.countDown();
            }
        }
    }
}
