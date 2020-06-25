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

abstract class TimeoutTask implements Comparable<TimeoutTask> {
    private final int timeout;

    private long dueTime;

    private boolean canceled;
    private boolean done;

    public TimeoutTask(int timeout) {

        this.timeout = timeout;
        this.done = false;
        this.canceled = false;
        this.dueTime = 0;
    }

    void manExec() {

        if (canceled) {
            return;
        }

        try {
            execute();
        } finally {
            this.done = true;
        }
    }

    void updateDueTime() {

        this.dueTime = System.currentTimeMillis() + timeout;
        this.canceled = false;
        this.done = false;
    }

    protected abstract void execute();

    public boolean isPlanned() {

        return !this.canceled && !this.done && dueTime != 0;
    }

    public boolean isDone() {

        return done;
    }

    public void cancel() {

        this.canceled = true;
    }

    public long sleepTimeFromDueTime() {

        return dueTime - System.currentTimeMillis();
    }

    @Override
    public int compareTo(TimeoutTask o) {

        return Long.compare(this.dueTime, o.dueTime);
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof TimeoutTask)) {
            return false;
        }

        TimeoutTask o = (TimeoutTask) obj;
        return this.dueTime == o.dueTime && this.canceled == o.canceled && this.done == o.done
                && this.timeout == o.timeout;
    }

    @Override
    public int hashCode() {

        return this.timeout ^ ((Boolean.valueOf(canceled).hashCode()) << 2) ^ Boolean.valueOf(done).hashCode();
    }

}
