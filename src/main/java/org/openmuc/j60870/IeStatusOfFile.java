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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Represents a status of file (SOF) information element.
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class IeStatusOfFile extends InformationElement {

    private final int status;
    private final boolean lastFileOfDirectory;
    private final boolean nameDefinesDirectory;
    private final boolean transferIsActive;

    public IeStatusOfFile(int status, boolean lastFileOfDirectory, boolean nameDefinesDirectory,
            boolean transferIsActive) {
        this.status = status;
        this.lastFileOfDirectory = lastFileOfDirectory;
        this.nameDefinesDirectory = nameDefinesDirectory;
        this.transferIsActive = transferIsActive;
    }

    IeStatusOfFile(DataInputStream is) throws IOException {
        int b1 = (is.readByte() & 0xff);
        status = b1 & 0x1f;
        lastFileOfDirectory = ((b1 & 0x20) == 0x20);
        nameDefinesDirectory = ((b1 & 0x40) == 0x40);
        transferIsActive = ((b1 & 0x80) == 0x80);
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) status;
        if (lastFileOfDirectory) {
            buffer[i] |= 0x20;
        }
        if (nameDefinesDirectory) {
            buffer[i] |= 0x40;
        }
        if (transferIsActive) {
            buffer[i] |= 0x80;
        }
        return 1;
    }

    public int getStatus() {
        return status;
    }

    public boolean isLastFileOfDirectory() {
        return lastFileOfDirectory;
    }

    public boolean isNameDefinesDirectory() {
        return nameDefinesDirectory;
    }

    public boolean isTransferIsActive() {
        return transferIsActive;
    }

    @Override
    public String toString() {
        return "Status of file: " + status + ", last file of directory: " + lastFileOfDirectory
                + ", name defines directory: " + nameDefinesDirectory + ", transfer is active: " + transferIsActive;
    }
}
