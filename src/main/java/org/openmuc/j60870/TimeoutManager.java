/*
 * Copyright 2014-2023 Fraunhofer ISE
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

    private static final int INITIAL_QUE_CAPACITY = 4;
    private final PriorityBlockingQueue<TimeoutTask> queue;
    private final Object guardedLock;
    boolean canceled;

    public TimeoutManager() {
        this.queue = new PriorityBlockingQueue<>(INITIAL_QUE_CAPACITY);
        this.guardedLock = new Object();
    }

    public void addTimerTask(TimeoutTask task) {
        task.updateDueTime();
        removeDuplicates(task);
        this.queue.add(task);
        notifyLock();
    }

    private void notifyLock() {
        synchronized (this.guardedLock) {
            this.guardedLock.notifyAll();
        }
    }

    private void removeDuplicates(TimeoutTask task) {
        while (queue.remove(task)) {
            // continue removing until there are no duplicates
        }
    }

    public void cancel() {
        this.canceled = true;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("TimeoutManager");
        TimeoutTask currentTask;
        while (!canceled) {
            try {
                long sleepMillis;
                currentTask = queue.take();

                while ((sleepMillis = currentTask.sleepTimeFromDueTime()) > 0) {
                    queue.put(currentTask);

                    synchronized (this.guardedLock) {
                        this.guardedLock.wait(sleepMillis);
                    }
                    currentTask = queue.take();
                }

                currentTask.executeManually();
            } catch (InterruptedException e) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
