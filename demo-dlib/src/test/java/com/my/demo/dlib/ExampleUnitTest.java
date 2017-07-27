package com.my.demo.dlib;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.TestScheduler;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void rxjava2_disposable() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        TestObserver<Long> o = Observable
            .interval(1, TimeUnit.SECONDS, scheduler)
            .test();
        o.assertNoValues();
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        o.assertValues(0L);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        o.assertValues(0L, 1L);

        // Dispose the connection.
        o.dispose();

        scheduler.advanceTimeBy(100, TimeUnit.SECONDS);
        o.assertValues(0L, 1L);
    }
}
