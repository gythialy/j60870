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

/**
 * Every ASDU contains a single Cause Of Transmission field so the recipient knows why the message it received was sent.
 * Parts IEC 60870-5-101 and IEC 60870-5-104 define what CauseOfTransmissions are allowed for the different ASDU types.
 * CauseOfTransmissions 44 to 47 are meant for replies to commands with undefined values.
 *
 * @author Stefan Feuerhahn
 */
public enum CauseOfTransmission {

    PERIODIC(1), BACKGROUND_SCAN(2), SPONTANEOUS(3), INITIALIZED(4), REQUEST(5), ACTIVATION(6), ACTIVATION_CON(
            7), DEACTIVATION(
            8), DEACTIVATION_CON(9), ACTIVATION_TERMINATION(10), RETURN_INFO_REMOTE(11), RETURN_INFO_LOCAL(
            12), FILE_TRANSFER(
            13), INTERROGATED_BY_STATION(20), INTERROGATED_BY_GROUP_1(21), INTERROGATED_BY_GROUP_2(
            22), INTERROGATED_BY_GROUP_3(
            23), INTERROGATED_BY_GROUP_4(24), INTERROGATED_BY_GROUP_5(25), INTERROGATED_BY_GROUP_6(
            26), INTERROGATED_BY_GROUP_7(
            27), INTERROGATED_BY_GROUP_8(28), INTERROGATED_BY_GROUP_9(29), INTERROGATED_BY_GROUP_10(
            30), INTERROGATED_BY_GROUP_11(
            31), INTERROGATED_BY_GROUP_12(32), INTERROGATED_BY_GROUP_13(33), INTERROGATED_BY_GROUP_14(
            34), INTERROGATED_BY_GROUP_15(
            35), INTERROGATED_BY_GROUP_16(36), REQUESTED_BY_GENERAL_COUNTER(37), REQUESTED_BY_GROUP_1_COUNTER(
            38), REQUESTED_BY_GROUP_2_COUNTER(
            39), REQUESTED_BY_GROUP_3_COUNTER(40), REQUESTED_BY_GROUP_4_COUNTER(41), UNKNOWN_TYPE_ID(
            44), UNKNOWN_CAUSE_OF_TRANSMISSION(
            45), UNKNOWN_COMMON_ADDRESS_OF_ASDU(46), UNKNOWN_INFORMATION_OBJECT_ADDRESS(47);

    private final int code;

    /**
     * Create a CauseOfTransmission with the given code.
     *
     * @param code the code of the CauseOfTransmission.
     */
    private CauseOfTransmission(int code) {
        this.code = code;
    }

    /**
     * Returns the code associated with this CauseOfTransmission.
     *
     * @return the code associated with this CauseOfTransmission.
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the CauseOfTransmission for the given code. Returns null if the code is unknown or from the private
     * (special use) range.
     *
     * @param code the code of the CauseOfTransmission
     * @return the CauseOfTransmission. Returns null if the code is unknown or from the private (special use) range.
     */
    public static CauseOfTransmission createCauseOfTransmission(int code) {
        switch (code) {
        case 1:
            return CauseOfTransmission.PERIODIC;
        case 2:
            return CauseOfTransmission.BACKGROUND_SCAN;
        case 3:
            return CauseOfTransmission.SPONTANEOUS;
        case 4:
            return CauseOfTransmission.INITIALIZED;
        case 5:
            return CauseOfTransmission.REQUEST;
        case 6:
            return CauseOfTransmission.ACTIVATION;
        case 7:
            return CauseOfTransmission.ACTIVATION_CON;
        case 8:
            return CauseOfTransmission.DEACTIVATION;
        case 9:
            return CauseOfTransmission.DEACTIVATION_CON;
        case 10:
            return CauseOfTransmission.ACTIVATION_TERMINATION;
        case 11:
            return CauseOfTransmission.RETURN_INFO_REMOTE;
        case 12:
            return CauseOfTransmission.RETURN_INFO_LOCAL;
        case 13:
            return CauseOfTransmission.FILE_TRANSFER;
        case 20:
            return CauseOfTransmission.INTERROGATED_BY_STATION;
        case 21:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_1;
        case 22:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_2;
        case 23:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_3;
        case 24:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_4;
        case 25:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_5;
        case 26:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_6;
        case 27:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_7;
        case 28:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_8;
        case 29:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_9;
        case 30:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_10;
        case 31:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_11;
        case 32:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_12;
        case 33:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_13;
        case 34:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_14;
        case 35:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_15;
        case 36:
            return CauseOfTransmission.INTERROGATED_BY_GROUP_16;
        case 37:
            return CauseOfTransmission.REQUESTED_BY_GENERAL_COUNTER;
        case 38:
            return CauseOfTransmission.REQUESTED_BY_GROUP_1_COUNTER;
        case 39:
            return CauseOfTransmission.REQUESTED_BY_GROUP_2_COUNTER;
        case 40:
            return CauseOfTransmission.REQUESTED_BY_GROUP_3_COUNTER;
        case 41:
            return CauseOfTransmission.REQUESTED_BY_GROUP_4_COUNTER;
        case 44:
            return CauseOfTransmission.UNKNOWN_TYPE_ID;
        case 45:
            return CauseOfTransmission.UNKNOWN_CAUSE_OF_TRANSMISSION;
        case 46:
            return CauseOfTransmission.UNKNOWN_COMMON_ADDRESS_OF_ASDU;
        case 47:
            return CauseOfTransmission.UNKNOWN_INFORMATION_OBJECT_ADDRESS;
        default:
            return null;
        }
    }
}
