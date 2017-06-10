/*
 * Copyright 2014-17 Fraunhofer ISE
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

import org.openmuc.j60870.internal.ConnectionSettings;

/**
 * Every Information Object contains:
 * <ul>
 * <li>The Information Object Address (IOA) that is 1, 2 or 3 bytes long.</li>
 * <li>A set of Information Elements or a sequence of information element sets. The type of information elements in the
 * set and their order depend on the ASDU's TypeId and is the same for all information objects within one ASDU. If the
 * sequence bit is set in the ASDU then the ASDU contains a single Information Object containing a sequence of
 * information element sets. If the sequence bit is not set the ASDU contains a sequence of information objects each
 * containing only single information elements sets.</li>
 * </ul>
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class InformationObject {

    private int informationObjectAddress;
    private final InformationElement[][] informationElements;

    public InformationObject(int informationObjectAddress, InformationElement[][] informationElements) {
        this.informationObjectAddress = informationObjectAddress;
        this.informationElements = informationElements;
    }

    InformationObject(DataInputStream is, TypeId typeId, int numberOfSequenceElements, ConnectionSettings settings)
            throws IOException {
        if (settings.ioaFieldLength == 1) {
            informationObjectAddress = (is.readByte() & 0xff);
        }
        else if (settings.ioaFieldLength == 2) {
            informationObjectAddress = (is.readByte() & 0xff) + ((is.readByte() & 0xff) << 8);
        }
        else if (settings.ioaFieldLength == 3) {
            informationObjectAddress = (is.readByte() & 0xff) + ((is.readByte() & 0xff) << 8)
                    + ((is.readByte() & 0xff) << 16);
        }

        switch (typeId) {
        // 1
        case M_SP_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][1];
            for (int i = 0; i < numberOfSequenceElements; i++) {
                informationElements[i][0] = new IeSinglePointWithQuality(is);
            }
            break;
        // 2
        case M_SP_TA_1:
            informationElements = new InformationElement[][] { { new IeSinglePointWithQuality(is), new IeTime24(is) } };
            break;
        // 3
        case M_DP_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][1];
            for (int i = 0; i < numberOfSequenceElements; i++) {
                informationElements[i][0] = new IeDoublePointWithQuality(is);
            }
            break;
        // 4
        case M_DP_TA_1:
            informationElements = new InformationElement[][] { { new IeDoublePointWithQuality(is), new IeTime24(is) } };
            break;
        // 5
        case M_ST_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][2];
            for (int i = 0; i < numberOfSequenceElements; i++) {
                informationElements[i][0] = new IeValueWithTransientState(is);
                informationElements[i][1] = new IeQuality(is);
            }
            break;
        // 6
        case M_ST_TA_1:
            informationElements = new InformationElement[][] {
                    { new IeValueWithTransientState(is), new IeQuality(is), new IeTime24(is) } };
            break;
        // 7
        case M_BO_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][2];
            for (int i = 0; i < numberOfSequenceElements; i++) {
                informationElements[i][0] = new IeBinaryStateInformation(is);
                informationElements[i][1] = new IeQuality(is);
            }
            break;
        // 8
        case M_BO_TA_1:
            informationElements = new InformationElement[][] {
                    { new IeBinaryStateInformation(is), new IeQuality(is), new IeTime24(is) } };
            break;
        // 9
        case M_ME_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][2];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeNormalizedValue(is);
                informationElementCombination[1] = new IeQuality(is);
            }
            break;
        // 10
        case M_ME_TA_1:
            informationElements = new InformationElement[][] {
                    { new IeNormalizedValue(is), new IeQuality(is), new IeTime24(is) } };
            break;
        // 11
        case M_ME_NB_1:
            informationElements = new InformationElement[numberOfSequenceElements][2];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeScaledValue(is);
                informationElementCombination[1] = new IeQuality(is);
            }
            break;
        // 12
        case M_ME_TB_1:
            informationElements = new InformationElement[][] {
                    { new IeScaledValue(is), new IeQuality(is), new IeTime24(is) } };
            break;
        // 13
        case M_ME_NC_1:
            informationElements = new InformationElement[numberOfSequenceElements][2];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeShortFloat(is);
                informationElementCombination[1] = new IeQuality(is);
            }
            break;
        // 14
        case M_ME_TC_1:
            informationElements = new InformationElement[][] {
                    { new IeShortFloat(is), new IeQuality(is), new IeTime24(is) } };
            break;
        // 15
        case M_IT_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][1];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeBinaryCounterReading(is);
            }
            break;
        // 16
        case M_IT_TA_1:
            informationElements = new InformationElement[][] { { new IeBinaryCounterReading(is), new IeTime24(is) } };
            break;
        // 17
        case M_EP_TA_1:
            informationElements = new InformationElement[][] {
                    { new IeSingleProtectionEvent(is), new IeTime16(is), new IeTime24(is) } };
            break;
        // 18
        case M_EP_TB_1:
            informationElements = new InformationElement[][] { { new IeProtectionStartEvent(is),
                    new IeProtectionQuality(is), new IeTime16(is), new IeTime24(is) } };
            break;
        // 19
        case M_EP_TC_1:
            informationElements = new InformationElement[][] { { new IeProtectionOutputCircuitInformation(is),
                    new IeProtectionQuality(is), new IeTime16(is), new IeTime24(is) } };
            break;
        // 20
        case M_PS_NA_1:
            informationElements = new InformationElement[numberOfSequenceElements][2];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeStatusAndStatusChanges(is);
                informationElementCombination[1] = new IeQuality(is);
            }
            break;
        // 21
        case M_ME_ND_1:
            informationElements = new InformationElement[numberOfSequenceElements][1];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeNormalizedValue(is);
            }
            break;
        // 30
        case M_SP_TB_1:
            informationElements = new InformationElement[][] { { new IeSinglePointWithQuality(is), new IeTime56(is) } };
            break;
        // 31
        case M_DP_TB_1:
            informationElements = new InformationElement[][] { { new IeDoublePointWithQuality(is), new IeTime56(is) } };
            break;
        // 32
        case M_ST_TB_1:
            informationElements = new InformationElement[][] {
                    { new IeValueWithTransientState(is), new IeQuality(is), new IeTime56(is) } };
            break;
        // 33
        case M_BO_TB_1:
            informationElements = new InformationElement[][] {
                    { new IeBinaryStateInformation(is), new IeQuality(is), new IeTime56(is) } };
            break;
        // 34
        case M_ME_TD_1:
            informationElements = new InformationElement[][] {
                    { new IeNormalizedValue(is), new IeQuality(is), new IeTime56(is) } };
            break;
        // 35
        case M_ME_TE_1:
            informationElements = new InformationElement[][] {
                    { new IeScaledValue(is), new IeQuality(is), new IeTime56(is) } };
            break;
        // 36
        case M_ME_TF_1:
            informationElements = new InformationElement[][] {
                    { new IeShortFloat(is), new IeQuality(is), new IeTime56(is) } };
            break;
        // 37
        case M_IT_TB_1:
            informationElements = new InformationElement[][] { { new IeBinaryCounterReading(is), new IeTime56(is) } };
            break;
        // 38
        case M_EP_TD_1:
            informationElements = new InformationElement[][] {
                    { new IeSingleProtectionEvent(is), new IeTime16(is), new IeTime56(is) } };
            break;
        // 39
        case M_EP_TE_1:
            informationElements = new InformationElement[][] { { new IeProtectionStartEvent(is),
                    new IeProtectionQuality(is), new IeTime16(is), new IeTime56(is) } };
            break;
        // 40
        case M_EP_TF_1:
            informationElements = new InformationElement[][] { { new IeProtectionOutputCircuitInformation(is),
                    new IeProtectionQuality(is), new IeTime16(is), new IeTime56(is) } };
            break;
        // 45
        case C_SC_NA_1:
            informationElements = new InformationElement[][] { { new IeSingleCommand(is) } };
            break;
        // 46
        case C_DC_NA_1:
            informationElements = new InformationElement[][] { { new IeDoubleCommand(is) } };
            break;
        // 47
        case C_RC_NA_1:
            informationElements = new InformationElement[][] { { new IeRegulatingStepCommand(is) } };
            break;
        // 48
        case C_SE_NA_1:
            informationElements = new InformationElement[][] {
                    { new IeNormalizedValue(is), new IeQualifierOfSetPointCommand(is) } };
            break;
        // 49
        case C_SE_NB_1:
            informationElements = new InformationElement[][] {
                    { new IeScaledValue(is), new IeQualifierOfSetPointCommand(is) } };
            break;
        // 50
        case C_SE_NC_1:
            informationElements = new InformationElement[][] {
                    { new IeShortFloat(is), new IeQualifierOfSetPointCommand(is) } };
            break;
        // 51
        case C_BO_NA_1:
            informationElements = new InformationElement[][] { { new IeBinaryStateInformation(is) } };
            break;
        // 58
        case C_SC_TA_1:
            informationElements = new InformationElement[][] { { new IeSingleCommand(is), new IeTime56(is) } };
            break;
        // 59
        case C_DC_TA_1:
            informationElements = new InformationElement[][] { { new IeDoubleCommand(is), new IeTime56(is) } };
            break;
        // 60
        case C_RC_TA_1:
            informationElements = new InformationElement[][] { { new IeBinaryStateInformation(is), new IeTime56(is) } };
            break;
        // 61
        case C_SE_TA_1:
            informationElements = new InformationElement[][] {
                    { new IeNormalizedValue(is), new IeQualifierOfSetPointCommand(is), new IeTime56(is) } };
            break;
        // 62
        case C_SE_TB_1:
            informationElements = new InformationElement[][] {
                    { new IeScaledValue(is), new IeQualifierOfSetPointCommand(is), new IeTime56(is) } };
            break;
        // 63
        case C_SE_TC_1:
            informationElements = new InformationElement[][] {
                    { new IeShortFloat(is), new IeQualifierOfSetPointCommand(is), new IeTime56(is) } };
            break;
        // 64
        case C_BO_TA_1:
            informationElements = new InformationElement[][] { { new IeBinaryStateInformation(is), new IeTime56(is) } };
            break;
        // 70
        case M_EI_NA_1:
            informationElements = new InformationElement[][] { { new IeCauseOfInitialization(is) } };
            break;
        // 100
        case C_IC_NA_1:
            informationElements = new InformationElement[][] { { new IeQualifierOfInterrogation(is) } };
            break;
        // 101
        case C_CI_NA_1:
            informationElements = new InformationElement[][] { { new IeQualifierOfCounterInterrogation(is) } };
            break;
        // 102
        case C_RD_NA_1:
            informationElements = new InformationElement[0][0];
            break;
        // 103
        case C_CS_NA_1:
            informationElements = new InformationElement[][] { { new IeTime56(is) } };
            break;
        // 104
        case C_TS_NA_1:
            informationElements = new InformationElement[][] { { new IeFixedTestBitPattern(is) } };
            break;
        // 105
        case C_RP_NA_1:
            informationElements = new InformationElement[][] { { new IeQualifierOfResetProcessCommand(is) } };
            break;
        // 106
        case C_CD_NA_1:
            informationElements = new InformationElement[][] { { new IeTime16(is) } };
            break;
        // 107
        case C_TS_TA_1:
            informationElements = new InformationElement[][] { { new IeTestSequenceCounter(is), new IeTime56(is) } };
            break;
        // 110
        case P_ME_NA_1:
            informationElements = new InformationElement[][] {
                    { new IeNormalizedValue(is), new IeQualifierOfParameterOfMeasuredValues(is) } };
            break;
        // 111
        case P_ME_NB_1:
            informationElements = new InformationElement[][] {
                    { new IeScaledValue(is), new IeQualifierOfParameterOfMeasuredValues(is) } };
            break;
        // 112
        case P_ME_NC_1:
            informationElements = new InformationElement[][] {
                    { new IeShortFloat(is), new IeQualifierOfParameterOfMeasuredValues(is) } };
            break;
        // 113
        case P_AC_NA_1:
            informationElements = new InformationElement[][] { { new IeQualifierOfParameterActivation(is) } };
            break;
        // 120
        case F_FR_NA_1:
            informationElements = new InformationElement[][] {
                    { new IeNameOfFile(is), new IeLengthOfFileOrSection(is), new IeFileReadyQualifier(is) } };
            break;
        // 121
        case F_SR_NA_1:
            informationElements = new InformationElement[][] { { new IeNameOfFile(is), new IeNameOfSection(is),
                    new IeLengthOfFileOrSection(is), new IeSectionReadyQualifier(is) } };
            break;
        // 122
        case F_SC_NA_1:
            informationElements = new InformationElement[][] {
                    { new IeNameOfFile(is), new IeNameOfSection(is), new IeSelectAndCallQualifier(is) } };
            break;
        // 123
        case F_LS_NA_1:
            informationElements = new InformationElement[][] { { new IeNameOfFile(is), new IeNameOfSection(is),
                    new IeLastSectionOrSegmentQualifier(is), new IeChecksum(is) } };
            break;
        // 124
        case F_AF_NA_1:
            informationElements = new InformationElement[][] {
                    { new IeNameOfFile(is), new IeNameOfSection(is), new IeAckFileOrSectionQualifier(is) } };
            break;
        // 125
        case F_SG_NA_1:
            informationElements = new InformationElement[][] {
                    { new IeNameOfFile(is), new IeNameOfSection(is), new IeFileSegment(is) } };
            break;
        // 126
        case F_DR_TA_1:
            informationElements = new InformationElement[numberOfSequenceElements][4];
            for (InformationElement[] informationElementCombination : informationElements) {
                informationElementCombination[0] = new IeNameOfFile(is);
                informationElementCombination[1] = new IeLengthOfFileOrSection(is);
                informationElementCombination[2] = new IeStatusOfFile(is);
                informationElementCombination[3] = new IeTime56(is);
            }
            break;
        // 127
        case F_SC_NB_1:
            informationElements = new InformationElement[][] {
                    { new IeNameOfFile(is), new IeTime56(is), new IeTime56(is) } };
            break;
        default:
            throw new IOException(
                    "Unable to parse Information Object because of unknown Type Identification: " + typeId);
        }

    }

    int encode(byte[] buffer, int i, ConnectionSettings settings) {
        int origi = i;

        buffer[i++] = (byte) informationObjectAddress;
        if (settings.ioaFieldLength > 1) {
            buffer[i++] = (byte) (informationObjectAddress >> 8);
            if (settings.ioaFieldLength > 2) {
                buffer[i++] = (byte) (informationObjectAddress >> 16);
            }
        }

        for (InformationElement[] informationElementCombination : informationElements) {
            for (InformationElement informationElement : informationElementCombination) {
                i += informationElement.encode(buffer, i);
            }
        }

        return i - origi;
    }

    public int getInformationObjectAddress() {
        return informationObjectAddress;
    }

    /**
     * Returns the information elements as a two dimensional array. The first dimension of the array is the index of the
     * sequence of information element sets. The second dimension is the index of the information element set. For
     * example an information object containing a single set of three information elements will have the dimension
     * [1][3]. Note that you will have to cast the returned <code>InformationElement</code>s to a concrete
     * implementation in order to access the data inside them.
     * 
     * @return the information elements as a two dimensional array.
     */
    public InformationElement[][] getInformationElements() {
        return informationElements;
    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder("IOA: " + informationObjectAddress);

        if (informationElements.length > 1) {
            int i = 1;
            for (InformationElement[] informationElementSet : informationElements) {
                builder.append("\nInformation Element Set " + i + ":");
                for (InformationElement informationElement : informationElementSet) {
                    builder.append("\n");
                    builder.append(informationElement.toString());
                }
                i++;
            }
        }
        else {
            for (InformationElement[] informationElementSet : informationElements) {
                for (InformationElement informationElement : informationElementSet) {
                    builder.append("\n");
                    builder.append(informationElement.toString());
                }
            }
        }

        return builder.toString();

    }

}
