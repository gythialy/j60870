/*
 * Copyright 2014 Fraunhofer ISE
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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

/**
 * Represents a seven octet binary time (CP56Time2a) information element.
 *
 * @author Stefan Feuerhahn
 */
public class IeTime56 extends InformationElement {

    private final byte[] value = new byte[7];

    public IeTime56(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        int ms = calendar.get(Calendar.MILLISECOND) + 1000 * calendar.get(Calendar.SECOND);

        value[0] = (byte) ms;
        value[1] = (byte) (ms >> 8);
        value[2] = (byte) calendar.get(Calendar.MINUTE);
        value[3] = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        value[4] = (byte) (calendar.get(Calendar.DAY_OF_MONTH)
                           + ((((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1) << 5));
        value[5] = (byte) (calendar.get(Calendar.MONTH) + 1);
        value[6] = (byte) (calendar.get(Calendar.YEAR) % 100);
    }

    IeTime56(DataInputStream is) throws IOException {
        is.readFully(value);
    }

    @Override int encode(byte[] buffer, int i) {
        System.arraycopy(value, 0, buffer, i, 7);
        return 7;
    }

    public long getTimestamp(int earliestYear) {

        int century = earliestYear / 100 * 100;
        if (value[6] < (earliestYear % 100)) {
            century += 100;
        }

        Calendar calendar = Calendar.getInstance();

        calendar.set(value[6] + century, value[5] - 1, value[4] & 0x1f, value[3], value[2],
                     (((value[0] & 0xff) + ((value[1] & 0xff) << 8))) / 1000);
        calendar.set(Calendar.MILLISECOND, (((value[0] & 0xff) + ((value[1] & 0xff) << 8))) % 1000);

        return calendar.getTimeInMillis();
    }

    @Override
    public String toString() {
        return "Time56: " + new Date(getTimestamp(0));
    }
}
