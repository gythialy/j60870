/*
 * Copyright 2014-2026 Fraunhofer ISE
 *
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * j60870 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * j60870 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with j60870.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.j60870;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TimeoutManagerTest {

    @Test
    public void test1() throws Exception {
        final long timeout = 200;

        TimeoutManager tm = spy(new TimeoutManager());
        final TimeoutTask task = mock(TimeoutTask.class);

        // Replace PowerMock's setInternalState with reflection
        Field timeoutField = TimeoutTask.class.getDeclaredField("timeout");
        timeoutField.setAccessible(true);
        timeoutField.set(task, timeout);

        doCallRealMethod().when(task).updateDueTime();
        doCallRealMethod().when(task).sleepTimeFromDueTime();
        doCallRealMethod().when(task).executeManually();
        doCallRealMethod().when(task).isDone();

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(tm);
        tm.addTimerTask(task);

        final long t0 = System.currentTimeMillis();
        doAnswer(new Answer<Void>() {

                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        long sleepTime = System.currentTimeMillis() - t0;

                        assertEquals(timeout, sleepTime, 40D);
                        return null;
                    }
                })
                .when(task)
                .execute();

        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                return task.isDone();
            }
        });

        // check if execute has been called
        verify(tm).run();
        verify(task).execute();

        exec.shutdown();
    }
}
