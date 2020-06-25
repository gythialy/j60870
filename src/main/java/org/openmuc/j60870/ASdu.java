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

import java.io.IOException;
import java.text.MessageFormat;

import javax.xml.bind.DatatypeConverter;

import org.openmuc.j60870.ie.InformationObject;
import org.openmuc.j60870.internal.ExtendedDataInputStream;

/**
 * The application service data unit (ASDU). The ASDU is the payload of the application protocol data unit (APDU). Its
 * structure is defined in IEC 60870-5-101. The ASDU consists of the Data Unit Identifier and a number of Information
 * Objects. The Data Unit Identifier contains:
 *
 * <ul>
 * <li>{@link org.openmuc.j60870.ASduType} (1 byte)</li>
 * <li>Variable Structure Qualifier (1 byte) - specifies how many Information Objects and Information Element sets are
 * part of the ASDU.</li>
 * <li>Cause of Transmission (COT, 1 or 2 bytes) - The first byte codes the actual
 * {@link org.openmuc.j60870.CauseOfTransmission}, a bit indicating whether the message was sent for test purposes only
 * and a bit indicating whether a confirmation message is positive or negative. The optional second byte of the Cause of
 * Transmission field is the Originator Address. It is the address of the originating controlling station so that
 * responses can be routed back to it.</li>
 * <li>Common Address of ASDU (1 or 2 bytes) - the address of the target station or the broadcast address. If the field
 * length of the common address is 1 byte then the addresses 1 to 254 are used to address a particular station (station
 * address) and 255 is used for broadcast addressing. If the field length of the common address is 2 bytes then the
 * addresses 1 to 65534 are used to address a particular station and 65535 is used for broadcast addressing. Broadcast
 * addressing is only allowed for certain TypeIDs.</li>
 * <li>A list of Information Objects containing the actual actual data in the form of Information Elements.</li>
 * </ul>
 */
public class ASdu {

    private final ASduType aSduType;
    private final boolean isSequenceOfElements;
    private final CauseOfTransmission causeOfTransmission;
    private final boolean test;
    private final boolean negativeConfirm;
    private final int originatorAddress;
    private final int commonAddress;
    private final InformationObject[] informationObjects;
    private final byte[] privateInformation;
    private final int sequenceLength;

    /**
     * Use this constructor to create standardized ASDUs.
     *
     * @param typeId               type identification field that defines the purpose and contents of the ASDU
     * @param isSequenceOfElements if {@code false} then the ASDU contains a sequence of information objects consisting of a fixed number
     *                             of information elements. If {@code true} the ASDU contains a single information object with a sequence
     *                             of elements.
     * @param causeOfTransmission  the cause of transmission
     * @param test                 true if the ASDU is sent for test purposes
     * @param negativeConfirm      true if the ASDU is a negative confirmation
     * @param originatorAddress    the address of the originating controlling station so that responses can be routed back to it
     * @param commonAddress        the address of the target station or the broadcast address.
     * @param informationObjects   the information objects containing the actual data
     */
    public ASdu(ASduType typeId, boolean isSequenceOfElements, CauseOfTransmission causeOfTransmission, boolean test,
                boolean negativeConfirm, int originatorAddress, int commonAddress,
                InformationObject... informationObjects) {

        this.aSduType = typeId;
        this.isSequenceOfElements = isSequenceOfElements;
        this.causeOfTransmission = causeOfTransmission;
        this.test = test;
        this.negativeConfirm = negativeConfirm;
        this.originatorAddress = originatorAddress;
        this.commonAddress = commonAddress;
        this.informationObjects = informationObjects;
        privateInformation = null;
        if (isSequenceOfElements) {
            sequenceLength = informationObjects[0].getInformationElements().length;
        } else {
            sequenceLength = informationObjects.length;
        }
    }

    /**
     * Use this constructor to create private ASDU with TypeIDs in the range 128-255.
     *
     * @param typeId               type identification field that defines the purpose and contents of the ASDU
     * @param isSequenceOfElements if false then the ASDU contains a sequence of information objects consisting of a fixed number of
     *                             information elements. If true the ASDU contains a single information object with a sequence of
     *                             elements.
     * @param sequenceLength       the number of information objects or the number elements depending depending on which is transmitted
     *                             as a sequence
     * @param causeOfTransmission  the cause of transmission
     * @param test                 true if the ASDU is sent for test purposes
     * @param negativeConfirm      true if the ASDU is a negative confirmation
     * @param originatorAddress    the address of the originating controlling station so that responses can be routed back to it
     * @param commonAddress        the address of the target station or the broadcast address.
     * @param privateInformation   the bytes to be transmitted as payload
     */
    public ASdu(ASduType typeId, boolean isSequenceOfElements, int sequenceLength,
                CauseOfTransmission causeOfTransmission, boolean test, boolean negativeConfirm, int originatorAddress,
                int commonAddress, byte[] privateInformation) {

        this.aSduType = typeId;
        this.isSequenceOfElements = isSequenceOfElements;
        this.causeOfTransmission = causeOfTransmission;
        this.test = test;
        this.negativeConfirm = negativeConfirm;
        this.originatorAddress = originatorAddress;
        this.commonAddress = commonAddress;
        informationObjects = null;
        this.privateInformation = privateInformation;
        this.sequenceLength = sequenceLength;
    }

    static ASdu decode(ExtendedDataInputStream is, ConnectionSettings settings, int aSduLength) throws IOException {

        int typeIdCode = is.readUnsignedByte();

        ASduType typeId = ASduType.typeFor(typeIdCode);

        if (typeId == null) {
            throw new IOException(MessageFormat.format("Unknown Type Identification: {0}", typeIdCode));
        }

        int currentByte = is.readUnsignedByte();

        boolean isSequenceOfElements = byteHasMask(currentByte, 0x80);

        int numberOfSequenceElements;
        int numberOfInformationObjects;

        int sequenceLength = currentByte & 0x7f;
        if (isSequenceOfElements) {
            numberOfSequenceElements = sequenceLength;
            numberOfInformationObjects = 1;
        } else {
            numberOfInformationObjects = sequenceLength;
            numberOfSequenceElements = 1;
        }

        currentByte = is.readUnsignedByte();
        CauseOfTransmission causeOfTransmission = CauseOfTransmission.causeFor(currentByte & 0x3f);
        boolean test = byteHasMask(currentByte, 0x80);
        boolean negativeConfirm = byteHasMask(currentByte, 0x40);

        int originatorAddress;
        if (settings.getCotFieldLength() == 2) {
            originatorAddress = is.readUnsignedByte();
            aSduLength--;
        } else {
            originatorAddress = -1;
        }

        int commonAddress;
        if (settings.getCommonAddressFieldLength() == 1) {
            commonAddress = is.readUnsignedByte();
        } else {
            commonAddress = is.readUnsignedByte() | (is.readUnsignedByte() << 8);

            aSduLength--;
        }

        InformationObject[] informationObjects;
        byte[] privateInformation;
        if (typeIdCode < 128) {

            informationObjects = new InformationObject[numberOfInformationObjects];

            int ioaFieldLength = settings.getIoaFieldLength();
            for (int i = 0; i < numberOfInformationObjects; i++) {
                informationObjects[i] = InformationObject.decode(is, typeId, numberOfSequenceElements, ioaFieldLength);
            }

            return new ASdu(typeId, isSequenceOfElements, causeOfTransmission, test, negativeConfirm, originatorAddress,
                    commonAddress, informationObjects);
        } else {
            privateInformation = new byte[aSduLength - 4];
            is.readFully(privateInformation);

            return new ASdu(typeId, isSequenceOfElements, sequenceLength, causeOfTransmission, test, negativeConfirm,
                    originatorAddress, commonAddress, privateInformation);
        }

    }

    private static boolean byteHasMask(int b, int mask) {
        return (b & mask) == mask;
    }

    public ASduType getTypeIdentification() {
        return aSduType;
    }

    public boolean isSequenceOfElements() {
        return isSequenceOfElements;
    }

    public int getSequenceLength() {
        return sequenceLength;
    }

    public CauseOfTransmission getCauseOfTransmission() {
        return causeOfTransmission;
    }

    public boolean isTestFrame() {
        return test;
    }

    public boolean isNegativeConfirm() {
        return negativeConfirm;
    }

    public Integer getOriginatorAddress() {
        return originatorAddress;
    }

    public int getCommonAddress() {
        return commonAddress;
    }

    public InformationObject[] getInformationObjects() {
        return informationObjects;
    }

    public byte[] getPrivateInformation() {
        return privateInformation;
    }

    int encode(byte[] buffer, int i, ConnectionSettings settings) {

        int origi = i;

        buffer[i++] = (byte) aSduType.getId();
        if (isSequenceOfElements) {
            buffer[i++] = (byte) (sequenceLength | 0x80);
        } else {
            buffer[i++] = (byte) sequenceLength;
        }

        if (test) {
            if (negativeConfirm) {
                buffer[i++] = (byte) (causeOfTransmission.getId() | 0xC0);
            } else {
                buffer[i++] = (byte) (causeOfTransmission.getId() | 0x80);
            }
        } else {
            if (negativeConfirm) {
                buffer[i++] = (byte) (causeOfTransmission.getId() | 0x40);
            } else {
                buffer[i++] = (byte) causeOfTransmission.getId();
            }
        }

        if (settings.getCotFieldLength() == 2) {
            buffer[i++] = (byte) originatorAddress;
        }

        buffer[i++] = (byte) commonAddress;

        if (settings.getCommonAddressFieldLength() == 2) {
            buffer[i++] = (byte) (commonAddress >> 8);
        }

        if (informationObjects != null) {
            for (InformationObject informationObject : informationObjects) {
                i += informationObject.encode(buffer, i, settings.getIoaFieldLength());
            }
        } else {
            System.arraycopy(privateInformation, 0, buffer, i, privateInformation.length);
            i += privateInformation.length;
        }
        return i - origi;
    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder().append("ASDU Type: ")
                .append(aSduType.getId())
                .append(", ")
                .append(aSduType)
                .append(", ")
                .append(aSduType.getDescription())
                .append("\nCause of transmission: ")
                .append(causeOfTransmission)
                .append(", test: ")
                .append(isTestFrame())
                .append(", negative con: ")
                .append(isNegativeConfirm())
                .append("\nOriginator address: ")
                .append(originatorAddress)
                .append(", Common address: ")
                .append(commonAddress);

        if (informationObjects != null) {
            for (InformationObject informationObject : informationObjects) {
                builder.append("\n").append(informationObject);
            }
        } else {
            builder.append("\nPrivate Information:\n");
            builder.append(DatatypeConverter.printHexBinary(this.privateInformation));
        }

        return builder.toString();

    }

}
