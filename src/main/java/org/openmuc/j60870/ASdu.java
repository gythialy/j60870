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
package org.openmuc.j60870;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;
import org.openmuc.j60870.internal.ExtendedDataInputStream;
import org.openmuc.j60870.internal.HexUtils;

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
     * @param typeId
     *            type identification field that defines the purpose and contents of the ASDU
     * @param isSequenceOfElements
     *            if {@code false} then the ASDU contains a sequence of information objects consisting of a fixed number
     *            of information elements. If {@code true} the ASDU contains a single information object with a sequence
     *            of elements.
     * @param causeOfTransmission
     *            the cause of transmission
     * @param test
     *            true if the ASDU is sent for test purposes
     * @param negativeConfirm
     *            true if the ASDU is a negative confirmation
     * @param originatorAddress
     *            the address of the originating controlling station so that responses can be routed back to it
     * @param commonAddress
     *            the address of the target station or the broadcast address.
     * @param informationObjects
     *            the information objects containing the actual data
     */
    public ASdu(
            ASduType typeId,
            boolean isSequenceOfElements,
            CauseOfTransmission causeOfTransmission,
            boolean test,
            boolean negativeConfirm,
            int originatorAddress,
            int commonAddress,
            InformationObject... informationObjects) {

        if (typeId == null) {
            throw new IllegalArgumentException("typeId must not be null");
        }
        if (causeOfTransmission == null) {
            throw new IllegalArgumentException("causeOfTransmission must not be null");
        }
        if (informationObjects == null) {
            throw new IllegalArgumentException("informationObjects must not be null");
        }

        if (isSequenceOfElements && informationObjects.length > 1) {
            throw new IllegalArgumentException(
                    "ASDU with SQ=1 must contain at most one information object in the in-memory model.");
        }

        this.aSduType = typeId;
        this.isSequenceOfElements = isSequenceOfElements;
        this.causeOfTransmission = causeOfTransmission;
        this.test = test;
        this.negativeConfirm = negativeConfirm;
        this.originatorAddress = originatorAddress;
        this.commonAddress = commonAddress;
        this.informationObjects = informationObjects;
        privateInformation = null;

        validateInformationObjects(informationObjects);

        if (isSequenceOfElements) {
            if (informationObjects.length == 0) {
                this.sequenceLength = 0;
            } else {
                InformationElement[][] elements = informationObjects[0].getInformationElements();
                if (elements == null) {
                    throw new IllegalArgumentException("Information elements of sequence ASDU must not be null.");
                }
                this.sequenceLength = elements.length;
            }
        } else {
            validateNonSequenceInformationObjects(informationObjects);
            this.sequenceLength = informationObjects.length;
        }
    }

    /**
     * Use this constructor to create private ASDU with TypeIDs in the range 128-255.
     *
     * @param typeId
     *            type identification field that defines the purpose and contents of the ASDU
     * @param isSequenceOfElements
     *            if false then the ASDU contains a sequence of information objects consisting of a fixed number of
     *            information elements. If true the ASDU contains a single information object with a sequence of
     *            elements.
     * @param sequenceLength
     *            the number of information objects or the number elements depending on which is transmitted as a
     *            sequence
     * @param causeOfTransmission
     *            the cause of transmission
     * @param test
     *            true if the ASDU is sent for test purposes
     * @param negativeConfirm
     *            true if the ASDU is a negative confirmation
     * @param originatorAddress
     *            the address of the originating controlling station so that responses can be routed back to it
     * @param commonAddress
     *            the address of the target station or the broadcast address.
     * @param privateInformation
     *            the bytes to be transmitted as payload
     */
    public ASdu(
            ASduType typeId,
            boolean isSequenceOfElements,
            int sequenceLength,
            CauseOfTransmission causeOfTransmission,
            boolean test,
            boolean negativeConfirm,
            int originatorAddress,
            int commonAddress,
            byte[] privateInformation) {

        if (typeId == null) {
            throw new IllegalArgumentException("typeId must not be null");
        }
        if (causeOfTransmission == null) {
            throw new IllegalArgumentException("causeOfTransmission must not be null");
        }
        if (privateInformation == null) {
            throw new IllegalArgumentException("privateInformation must not be null");
        }
        if (typeId.getId() < 128) {
            throw new IllegalArgumentException(
                    "The private ASDU constructor may only be used for TypeIDs in the private range 128..255.");
        }

        validateSequenceLength(sequenceLength);
        validateOriginatorAddress(originatorAddress);
        validateCommonAddress(commonAddress);

        this.aSduType = typeId;
        this.isSequenceOfElements = isSequenceOfElements;
        this.causeOfTransmission = causeOfTransmission;
        this.test = test;
        this.negativeConfirm = negativeConfirm;
        this.originatorAddress = originatorAddress;
        this.commonAddress = commonAddress;
        informationObjects = null;
        this.privateInformation = Arrays.copyOf(privateInformation, privateInformation.length);
        this.sequenceLength = sequenceLength;
    }

    static ASdu decode(ExtendedDataInputStream is, ConnectionSettings settings, int aSduLength) throws IOException {

        int typeIdCode = is.readUnsignedByte();

        ASduType typeId = ASduType.typeFor(typeIdCode);

        if (typeId == null
                || (settings.getAllowedTypes() != null
                        && !settings.getAllowedTypes().contains(typeId))) {
            throw new UnknownAsduTypeException(
                    MessageFormat.format("Unknown or not allowed Type Identification: {0}", typeIdCode));
        }

        int vsq = is.readUnsignedByte();
        boolean isSequenceOfElements = byteHasMask(vsq, 0x80);
        int sequenceLength = vsq & 0x7f;

        if (sequenceLength > 127) {
            throw new IOException(
                    "Variable structure qualifier sequence length out of range 0..127: " + sequenceLength);
        }

        if (sequenceLength == 0 && typeId.isCommandType()) {
            throw new IOException("ASDU type " + typeId + " must contain exactly one information object.");
        }

        int dataUnitIdentifierLength = 2 + settings.getCotFieldLength() + settings.getCommonAddressFieldLength();
        int availablePayload = aSduLength - dataUnitIdentifierLength;

        if (availablePayload < 0) {
            throw new IOException("Declared ASDU length shorter than data unit identifier.");
        }

        int numberOfSequenceElements;
        int numberOfInformationObjects;

        if (isSequenceOfElements) {
            numberOfSequenceElements = sequenceLength;
            numberOfInformationObjects = sequenceLength == 0 ? 0 : 1;
        } else {
            numberOfInformationObjects = sequenceLength;
            numberOfSequenceElements = sequenceLength == 0 ? 0 : 1;
        }

        int currentByte = is.readUnsignedByte();
        CauseOfTransmission causeOfTransmission = CauseOfTransmission.causeFor(currentByte & 0x3f);
        if (causeOfTransmission == null) {
            throw new IOException("Unknown Cause Of Transmission: " + (currentByte & 0x3f));
        }
        boolean test = byteHasMask(currentByte, 0x80);
        boolean negativeConfirm = byteHasMask(currentByte, 0x40);

        int originatorAddress;
        if (settings.getCotFieldLength() == 2) {
            originatorAddress = is.readUnsignedByte();
        } else {
            originatorAddress = -1;
        }

        int commonAddress;
        if (settings.getCommonAddressFieldLength() == 1) {
            commonAddress = is.readUnsignedByte();
        } else {
            commonAddress = is.readUnsignedByte() | (is.readUnsignedByte() << 8);
        }

        if (typeIdCode < 128) {

            if (typeId.isCommandType()) {
                if (isSequenceOfElements) {
                    throw new IOException("ASDU type " + typeId + " must not use SQ=1.");
                }

                if (sequenceLength > 1) {
                    throw new IOException("ASDU type " + typeId + " must contain at most one information object.");
                }

                if (sequenceLength == 1) {
                    numberOfSequenceElements = 1;
                    numberOfInformationObjects = 1;
                }
            }

            int ioaFieldLength = settings.getIoaFieldLength();

            // Conservative plausibility checks to reject obviously impossible VSQ declarations.
            if (sequenceLength > 0) {
                if (isSequenceOfElements) {
                    if (availablePayload < ioaFieldLength) {
                        throw new IOException("ASDU payload too short for information object address field.");
                    }

                    int minPayloadAfterIoa = availablePayload - ioaFieldLength;

                    // Every sequence element consumes at least one octet.
                    if (numberOfSequenceElements > minPayloadAfterIoa) {
                        throw new IOException("Declared number of sequence elements (" + numberOfSequenceElements
                                + ") exceeds available ASDU payload (" + availablePayload + " bytes).");
                    }
                } else {
                    int minBytesPerInformationObject = Math.max(ioaFieldLength, 1);
                    int maxInformationObjects = availablePayload / minBytesPerInformationObject;

                    if (numberOfInformationObjects > maxInformationObjects) {
                        throw new IOException("Declared number of information objects (" + numberOfInformationObjects
                                + ") exceeds available ASDU payload (" + availablePayload + " bytes).");
                    }
                }
            }

            InformationObject[] informationObjects = new InformationObject[numberOfInformationObjects];

            for (int i = 0; i < numberOfInformationObjects; i++) {
                informationObjects[i] = InformationObject.decode(
                        is, typeId, numberOfSequenceElements, ioaFieldLength, settings.getReservedASduTypeDecoder());
            }

            return new ASdu(
                    typeId,
                    isSequenceOfElements,
                    causeOfTransmission,
                    test,
                    negativeConfirm,
                    originatorAddress,
                    commonAddress,
                    informationObjects);
        } else {
            byte[] privateInformation = new byte[availablePayload];
            is.readFully(privateInformation);

            return new ASdu(
                    typeId,
                    isSequenceOfElements,
                    sequenceLength,
                    causeOfTransmission,
                    test,
                    negativeConfirm,
                    originatorAddress,
                    commonAddress,
                    privateInformation);
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

        StringBuilder builder = new StringBuilder()
                .append("ASDU Type: ")
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
                builder.append('\n').append(informationObject);
            }
        } else {
            builder.append("\nPrivate Information:\n");
            builder.append(HexUtils.bytesToHex(this.privateInformation));
        }

        return builder.toString();
    }

    /**
     * Helper factory to build a response ASDU that mirrors the header of a given request, but with a different cause of
     * transmission and a (possibly different) set of information objects.
     *
     * Typical use-cases are: - activation confirmation (COT = ACTCON) - activation termination (COT = ACTTERM) -
     * negative confirmation (COT with negativeConfirm true)
     */
    public static ASdu buildCotResponse(
            ASdu request, CauseOfTransmission newCot, InformationObject... informationObjects) {

        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (newCot == null) {
            throw new IllegalArgumentException("newCot must not be null");
        }
        if (informationObjects == null) {
            throw new IllegalArgumentException("informationObjects must not be null");
        }

        boolean test = false; // responses are normally not marked as test
        boolean negative = false; // set to true in the caller if needed

        return new ASdu(
                request.getTypeIdentification(),
                request.isSequenceOfElements(),
                newCot,
                test,
                negative,
                request.getOriginatorAddress() == null ? -1 : request.getOriginatorAddress(),
                request.getCommonAddress(),
                informationObjects);
    }

    private static void validateSequenceLength(int sequenceLength) {
        if (sequenceLength < 0 || sequenceLength > 127) {
            throw new IllegalArgumentException("sequenceLength must be in the range 0..127");
        }
    }

    private static void validateOriginatorAddress(int originatorAddress) {
        if (originatorAddress < -1 || originatorAddress > 255) {
            throw new IllegalArgumentException("originatorAddress must be in the range -1..255");
        }
    }

    private static void validateCommonAddress(int commonAddress) {
        if (commonAddress < 0 || commonAddress > 65535) {
            throw new IllegalArgumentException("commonAddress must be in the range 0..65535");
        }
    }

    private static void validateInformationObjects(InformationObject[] informationObjects) {
        for (int i = 0; i < informationObjects.length; i++) {
            InformationObject informationObject = informationObjects[i];
            if (informationObject == null) {
                throw new IllegalArgumentException("informationObjects[" + i + "] must not be null");
            }

            InformationElement[][] informationElements = informationObject.getInformationElements();
            if (informationElements == null) {
                throw new IllegalArgumentException(
                        "informationObjects[" + i + "].informationElements must not be null");
            }

            for (int row = 0; row < informationElements.length; row++) {
                InformationElement[] informationElementSet = informationElements[row];
                if (informationElementSet == null) {
                    throw new IllegalArgumentException(
                            "informationObjects[" + i + "].informationElements[" + row + "] must not be null");
                }
                for (int col = 0; col < informationElementSet.length; col++) {
                    if (informationElementSet[col] == null) {
                        throw new IllegalArgumentException("informationObjects[" + i + "].informationElements[" + row
                                + "][" + col + "] must not be null");
                    }
                }
            }
        }
    }

    private static void validateNonSequenceInformationObjects(InformationObject[] informationObjects) {
        for (int i = 0; i < informationObjects.length; i++) {
            InformationElement[][] informationElements = informationObjects[i].getInformationElements();
            if (informationElements.length != 1) {
                throw new IllegalArgumentException(
                        "ASDU with SQ=0 must contain exactly one information element set per information object. "
                                + "Invalid number of sets at informationObjects[" + i + "]: "
                                + informationElements.length);
            }
        }
    }
}
