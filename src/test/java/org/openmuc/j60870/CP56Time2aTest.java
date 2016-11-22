/*
 * Copyright 2014-16 Fraunhofer ISE
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

import org.junit.Assert;
import org.junit.Test;

import java.util.TimeZone;

public class CP56Time2aTest {

    @Test
    public void testTimestampToCalendar() {
        long timestamp = 1383060654596l;
        TimeZone timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        IeTime56 time = new IeTime56(timestamp);
        byte[] buffer = new byte[7];
        time.encode(buffer, 0);
        Assert.assertArrayEquals(new byte[]{0x44, (byte) 0xd5, 0x1e, 0x17, 0x5d, 0x0a, 0x0d}, buffer);

        Assert.assertEquals(timestamp, time.getTimestamp());
        TimeZone.setDefault(timeZone);
    }
}
