/*
 * Copyright 2014-20 Fraunhofer ISE
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

import java.util.concurrent.PriorityBlockingQueue;

class TimeoutManager implements Runnable {

    private final PriorityBlockingQueue<TimeoutTask> queue;

    private final Object guadedLock;

    boolean canceled;

    public TimeoutManager() {
        this.queue = new PriorityBlockingQueue<>(4);
        this.guadedLock = new Object();
    }

    public void addTimerTask(TimeoutTask task) {
        task.updateDueTime();
        removeDuplicates(task);
        this.queue.add(task);
        synchronized (this.guadedLock) {
            this.guadedLock.notifyAll();
        }
    }

    private void removeDuplicates(TimeoutTask task) {
        while (queue.remove(task)) {
            ;
        }
    }

    public void cancel() {
        this.canceled = true;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("TimeoutManager");
        TimeoutTask currTask;
        while (!canceled) {
            try {
                long sleepMillis;
                currTask = queue.take();

                while ((sleepMillis = currTask.sleepTimeFromDueTime()) > 0) {
                    queue.put(currTask);

                    synchronized (this.guadedLock) {
                        this.guadedLock.wait(sleepMillis);
                    }
                    currTask = queue.take();
                }

                currTask.manExec();
            } catch (InterruptedException e) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
