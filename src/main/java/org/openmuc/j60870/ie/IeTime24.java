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
package org.openmuc.j60870.ie;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Calendar;

/**
 * Represents a three octet binary time (CP24Time2a) information element.
 */
public class IeTime24 extends InformationElement {

    private final byte[] value = new byte[3];

    public IeTime24(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        int ms = calendar.get(Calendar.MILLISECOND) + 1000 * calendar.get(Calendar.SECOND);

        value[0] = (byte) ms;
        value[1] = (byte) (ms >> 8);
        value[2] = (byte) calendar.get(Calendar.MINUTE);
    }

    public IeTime24(int timeInMs) {

        int ms = timeInMs % 60000;
        value[0] = (byte) ms;
        value[1] = (byte) (ms >> 8);
        value[2] = (byte) (timeInMs / 60000);
    }

    IeTime24(DataInputStream is) throws IOException {
        is.readFully(value);
    }

    @Override
    int encode(byte[] buffer, int i) {
        System.arraycopy(value, 0, buffer, i, 3);
        return 3;
    }

    public int getTimeInMs() {
        return (value[0] & 0xff) + ((value[1] & 0xff) << 8) + value[2] * 60000;
    }

    @Override
    public String toString() {
        return "Time24, time in ms: " + getTimeInMs();
    }
}
