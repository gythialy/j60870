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
package org.openmuc.j60870.ie;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openmuc.j60870.ie.IeBinaryCounterReading.Flag;
import org.openmuc.j60870.internal.ExtendedDataInputStream;

public class IeBinaryCounterReadingTest {

    @Test
    public void t1_initialization() throws Exception {
        int counterReading = -300;
        int sequenceNumber = 4;
        IeBinaryCounterReading binaryCounterReading =
                new IeBinaryCounterReading(counterReading, sequenceNumber, Flag.CARRY, Flag.INVALID);

        assertEquals(counterReading, binaryCounterReading.getCounterReading());
        assertEquals(sequenceNumber, binaryCounterReading.getSequenceNumber());

        assertTrue(binaryCounterReading.getFlags().contains(Flag.CARRY));
        assertTrue(binaryCounterReading.getFlags().contains(Flag.INVALID));
        assertFalse(binaryCounterReading.getFlags().contains(Flag.COUNTER_ADJUSTED));
    }

    @Test
    public void t2_encoding() throws Exception {
        int counterReading = -300;
        int sequenceNumber = 4;
        IeBinaryCounterReading binaryCounterReading =
                new IeBinaryCounterReading(counterReading, sequenceNumber, Flag.CARRY, Flag.INVALID);

        byte[] buffer = new byte[5];
        int length = binaryCounterReading.encode(buffer, 0);

        assertEquals(5, length);
        assertArrayEquals(new byte[] {-44, -2, -1, -1, -92}, buffer);
    }

    @Test
    public void t3_encoding_symmetry() throws Exception {
        int counterReading = -300;
        int sequenceNumber = 4;
        IeBinaryCounterReading binaryCounterReading =
                new IeBinaryCounterReading(counterReading, sequenceNumber, Flag.CARRY, Flag.INVALID);

        byte[] buffer = new byte[5];
        int length = binaryCounterReading.encode(buffer, 0);
        assertEquals(5, length);

        IeBinaryCounterReading binaryCounterReadingD = decode(buffer);

        assertEquals(counterReading, binaryCounterReadingD.getCounterReading());
        assertEquals(sequenceNumber, binaryCounterReadingD.getSequenceNumber());

        assertTrue(binaryCounterReadingD.getFlags().contains(Flag.CARRY));
        assertTrue(binaryCounterReadingD.getFlags().contains(Flag.INVALID));
        assertFalse(binaryCounterReadingD.getFlags().contains(Flag.COUNTER_ADJUSTED));
    }

    static Stream<Arguments> t4_data() {
        return Stream.of(
                Arguments.of(0x80, Flag.INVALID),
                Arguments.of(0x40, Flag.COUNTER_ADJUSTED),
                Arguments.of(0x20, Flag.CARRY));
    }

    @ParameterizedTest
    @MethodSource("t4_data")
    public void t4(int flagTag, Flag f) throws Exception {
        byte[] buffer = {-44, -2, -1, -1, (byte) flagTag};
        IeBinaryCounterReading d = decode(buffer);

        EnumSet<Flag> es = EnumSet.allOf(Flag.class);
        assertTrue(es.remove(f), "Flag is somehow not there..?");
        assertTrue(d.getFlags().contains(f), "Flag was not represented..");

        assertFalse(es.removeAll(d.getFlags()), "Also contained flag, which should not have been there.");
    }

    private static IeBinaryCounterReading decode(byte[] buffer) throws IOException {
        IeBinaryCounterReading binaryCounterReadingD;
        try (ExtendedDataInputStream is = new ExtendedDataInputStream(new ByteArrayInputStream(buffer)); ) {
            binaryCounterReadingD = IeBinaryCounterReading.decode(is);
        }
        return binaryCounterReadingD;
    }
}
