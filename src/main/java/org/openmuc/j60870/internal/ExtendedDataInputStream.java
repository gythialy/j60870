/* Any copyright is dedicated to the Public Domain.
 * http://creativecommons.org/publicdomain/zero/1.0/ */
package org.openmuc.j60870.internal;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ExtendedDataInputStream extends DataInputStream {

    private static final int INTEGER_BYTES = 4;
    private static final int SHORT_BYTES = 2;

    public ExtendedDataInputStream(InputStream in) {
        super(in);
    }

    public int readLittleEndianInt() throws IOException {
        return (int) readNLittleEndianBytes(INTEGER_BYTES);
    }

    public long readLittleEndianUnsignedInt() throws IOException {
        return readLittleEndianInt() & 0xffffffffl;
    }

    public short readLittleEndianShort() throws IOException {
        return (short) readNLittleEndianBytes(SHORT_BYTES);
    }

    public int readLittleEndianUnsignedShort() throws IOException {
        return readLittleEndianShort() & 0xffff;
    }

    private long readNLittleEndianBytes(int n) throws IOException {
        long res = 0;
        for (int i = 0; i < n; ++i) {
            res |= readUnsignedByte() << 8 * i;
        }
        return res;
    }
}
