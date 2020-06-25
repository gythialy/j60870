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

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.MessageFormat;

import org.openmuc.j60870.internal.ExtendedDataInputStream;

class APdu {

    private static final int CONTROL_FIELDS_LENGTH = 4;
    /**
     * Since the length of the control field is control field is 4 octets.
     */
    private static final int MIN_APDU_LENGTH = CONTROL_FIELDS_LENGTH;
    /**
     * The maximum length of APDU for both directions is 253. APDU max = 255 minus start and length octet.
     */
    private static final int MAX_APDU_LENGTH = 253;
    /**
     * START flag of an APDU.
     */
    private static final byte START_FLAG = 0x68;
    private final int sendSeqNum;
    private final int receiveSeqNum;
    private final ApciType apciType;
    private final ASdu aSdu;
    public APdu(int sendSeqNum, int receiveSeqNum, ApciType apciType, ASdu aSdu) {
        this.sendSeqNum = sendSeqNum;
        this.receiveSeqNum = receiveSeqNum;
        this.apciType = apciType;
        this.aSdu = aSdu;
    }

    @SuppressWarnings("resource")
    public static APdu decode(Socket socket, ConnectionSettings settings) throws IOException {
        socket.setSoTimeout(0);

        ExtendedDataInputStream is = new ExtendedDataInputStream(socket.getInputStream());

        if (is.readByte() != START_FLAG) {
            throw new IOException("Message does not start with START flag (0x68). Broken connection.");
        }

        socket.setSoTimeout(settings.getMessageFragmentTimeout());

        int length = readApduLength(is);

        byte[] aPduControlFields = readControlFields(is);

        ApciType apciType = ApciType.apciTypeFor(aPduControlFields[0]);
        switch (apciType) {
            case I_FORMAT:
                int sendSeqNum = seqNumFrom(aPduControlFields[0], aPduControlFields[1]);
                int receiveSeqNum = seqNumFrom(aPduControlFields[2], aPduControlFields[3]);

                int aSduLength = length - CONTROL_FIELDS_LENGTH;

                return new APdu(sendSeqNum, receiveSeqNum, apciType, ASdu.decode(is, settings, aSduLength));
            case S_FORMAT:
                return new APdu(0, seqNumFrom(aPduControlFields[2], aPduControlFields[3]), apciType, null);

            default:
                return new APdu(0, 0, apciType, null);
        }

    }

    private static int seqNumFrom(byte b1, byte b2) {
        return ((b1 & 0xfe) >> 1) + ((b2 & 0xff) << 7);
    }

    private static int readApduLength(DataInputStream is) throws IOException {
        int length = is.readUnsignedByte();

        if (length < MIN_APDU_LENGTH || length > MAX_APDU_LENGTH) {
            String msg = MessageFormat
                    .format("APDU has an invalid length must be between 4 and 253.\nReceived length was: {0}.", length);
            throw new IOException(msg);
        }
        return length;
    }

    private static byte[] readControlFields(DataInputStream is) throws IOException {
        byte[] aPduControlFields = new byte[CONTROL_FIELDS_LENGTH];
        is.readFully(aPduControlFields);
        return aPduControlFields;
    }

    private static void setV3To5zero(byte[] buffer) {
        buffer[3] = 0x00;
        buffer[4] = 0x00;
        buffer[5] = 0x00;
    }

    public int encode(byte[] buffer, ConnectionSettings settings) {

        buffer[0] = START_FLAG;

        int length = CONTROL_FIELDS_LENGTH;

        if (apciType == ApciType.I_FORMAT) {
            buffer[2] = (byte) (sendSeqNum << 1);
            buffer[3] = (byte) (sendSeqNum >> 7);
            writeReceiveSeqNumTo(buffer);

            length += aSdu.encode(buffer, 6, settings);
        } else if (apciType == ApciType.STARTDT_ACT) {
            buffer[2] = 0x07;
            setV3To5zero(buffer);
        } else if (apciType == ApciType.STARTDT_CON) {
            buffer[2] = 0x0b;
            setV3To5zero(buffer);
        } else if (apciType == ApciType.STOPDT_ACT) {
            buffer[2] = 0x13;
            setV3To5zero(buffer);
        } else if (apciType == ApciType.STOPDT_CON) {
            buffer[2] = 0x23;
            setV3To5zero(buffer);
        } else if (apciType == ApciType.S_FORMAT) {
            buffer[2] = 0x01;
            buffer[3] = 0x00;
            writeReceiveSeqNumTo(buffer);
        }

        buffer[1] = (byte) length;

        return length + 2;

    }

    private void writeReceiveSeqNumTo(byte[] buffer) {
        buffer[4] = (byte) (receiveSeqNum << 1);
        buffer[5] = (byte) (receiveSeqNum >> 7);
    }

    public ApciType getApciType() {
        return apciType;
    }

    public int getSendSeqNumber() {
        return sendSeqNum;
    }

    public int getReceiveSeqNumber() {
        return receiveSeqNum;
    }

    public ASdu getASdu() {
        return aSdu;
    }

    public enum ApciType {
        /**
         * Numbered information transfer. I format APDUs always contain an ASDU.
         */
        I_FORMAT,
        /**
         * Numbered supervisory functions. S format APDUs consist of the APCI only.
         */
        S_FORMAT,

        // Unnumbered control functions.

        TESTFR_CON,
        TESTFR_ACT,
        STOPDT_CON,
        STOPDT_ACT,
        STARTDT_CON,
        STARTDT_ACT;

        private static ApciType apciTypeFor(byte controlField1) {
            if ((controlField1 & 0x01) == 0) {
                return ApciType.I_FORMAT;
            }

            switch ((controlField1 & 0x03)) {
                case 1:
                    return ApciType.S_FORMAT;
                case 3:
                default:
                    return unnumberedFormatFor(controlField1);
            }

        }

        private static ApciType unnumberedFormatFor(byte controlField1) {
            if ((controlField1 & 0x80) == 0x80) {
                return ApciType.TESTFR_CON;
            } else if (controlField1 == 0x43) {
                return ApciType.TESTFR_ACT;
            } else if (controlField1 == 0x23) {
                return ApciType.STOPDT_CON;
            } else if (controlField1 == 0x13) {
                return ApciType.STOPDT_ACT;
            } else if (controlField1 == 0x0B) {
                return ApciType.STARTDT_CON;
            } else {
                return ApciType.STARTDT_ACT;
            }
        }
    }

}
