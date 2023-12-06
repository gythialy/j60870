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
package org.openmuc.j60870.ie;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.openmuc.j60870.internal.HexUtils.hexToBytes;

public class CP56Time2aTest {

    TimeZone cet = TimeZone.getTimeZone("CET");
    TimeZone systemDefault;

    @Before
    public void setup() {
        systemDefault = TimeZone.getDefault();
        TimeZone.setDefault(cet);
    }

    @After
    public void init() {
        TimeZone.setDefault(systemDefault);
    }

    @After
    public void deinit() {
        TimeZone cet = TimeZone.getTimeZone("CET");
        TimeZone.setDefault(cet);
    }

    @Test
    public void testTimestampToCalendarInvalid() {
        // Time56: 29-10-13 16:30:54:596, invalid
        testIeTime56(1383060654596L, true, "CET", false, hexToBytes("44D59E105D0A0D"));
    }

    @Test
    public void testDSTTimestampToCalendarInvalid() {
        // Time56: 06-07-18 15:17:23:000 DST, invalid
        testIeTime56(1530883043000L, true, "CET", true, hexToBytes("D859918FA60712"));
    }

    @Test
    public void testDstTimestampToCalendar() {
        // Time56: 28-10-18 02:59:59:999 DST
        testIeTime56(1540688399999L, false, "CET", true, hexToBytes("5FEA3B82FC0A12"));
    }

    @Test
    public void testTimestampToCalendar() {
        // Time56: 28-10-18 02:00:00:000
        testIeTime56(1540688400000L, false, "CET", false, hexToBytes("00000002FC0A12"));
    }

    @Test
    public void summertime_20181028_0100() {
        // 28.10.2018 01:00:30 CET DST (UTC+2)
        IeTime56 ts = new IeTime56(new byte[]{0x30, 0x75, 0x00, (byte) 0x81, (byte) 0xFC, 0x0a, 0x12});
        assertEquals(1540681230000L, ts.getTimestamp());
    }

    @Test
    public void summertime_20181028_0200() {
        // 28.10.2018 02:00:30 CET DST (UTC+2)
        IeTime56 ts = new IeTime56(new byte[]{(byte) 0x30, 0x75, 0x00, (byte) 0x82, (byte) 0xfc, 0x0a, 0x12});
        assertEquals(1540684830000L, ts.getTimestamp());
    }

    @Test
    public void standardtime_20181028_0200() {
        // 28.10.2018 02:00:30 CET (UTC+1)
        IeTime56 ts = new IeTime56(new byte[]{0x30, 0x75, 0x00, (byte) 0x02, (byte) 0xfc, 0x0a, 0x12});
        assertEquals(1540688430000L, ts.getTimestamp());
    }

    @Test
    public void standardtime_20190331_0100() {
        // 31.03.2019 01:00:30 CET (UTC+1)
        IeTime56 ts = new IeTime56(new byte[]{0x30, 0x75, 0x00, (byte) 0x01, (byte) 0xff, 0x03, 0x13});
        assertEquals(1553990430000L, ts.getTimestamp());
    }

    @Test
    public void summtertime_20190331_0300() {
        // 31.03.2019 03:00:30 CET DST (UTC+2)
        IeTime56 ts = new IeTime56(new byte[]{0x30, 0x75, 0x00, (byte) 0x83, (byte) 0xff, 0x03, 0x13});
        assertEquals(1553994030000L, ts.getTimestamp());
    }

    private void testIeTime56(long timestamp, boolean invalid, String timezone, boolean expectedDST,
                              byte[] expectedEncodedBytes) {
        TimeZone timeZone = TimeZone.getTimeZone(timezone);

        IeTime56 time = new IeTime56(timestamp, timeZone, invalid);

        byte[] buffer = new byte[7];
        int length = time.encode(buffer, 0);

        assertEquals(7, length);

        assertEquals(invalid, time.isInvalid());

        assertEquals(expectedDST, time.isSummerTime());
        assertArrayEquals(expectedEncodedBytes, buffer);

        assertEquals(timestamp, time.getTimestamp());
    }
}
