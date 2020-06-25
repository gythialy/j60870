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

import java.util.HashMap;
import java.util.Map;

/**
 * Every ASDU contains a single Cause Of Transmission field so the recipient knows why the message it received was sent.
 * Parts IEC 60870-5-101 and IEC 60870-5-104 define what CauseOfTransmissions are allowed for the different ASDU types.
 * CauseOfTransmissions 44 to 47 are meant for replies to commands with undefined values.
 */
public enum CauseOfTransmission {
    PERIODIC(1),
    BACKGROUND_SCAN(2),
    SPONTANEOUS(3),
    INITIALIZED(4),
    REQUEST(5),
    ACTIVATION(6),
    ACTIVATION_CON(7),
    DEACTIVATION(8),
    DEACTIVATION_CON(9),
    ACTIVATION_TERMINATION(10),
    RETURN_INFO_REMOTE(11),
    RETURN_INFO_LOCAL(12),
    FILE_TRANSFER(13),
    INTERROGATED_BY_STATION(20),
    INTERROGATED_BY_GROUP_1(21),
    INTERROGATED_BY_GROUP_2(22),
    INTERROGATED_BY_GROUP_3(23),
    INTERROGATED_BY_GROUP_4(24),
    INTERROGATED_BY_GROUP_5(25),
    INTERROGATED_BY_GROUP_6(26),
    INTERROGATED_BY_GROUP_7(27),
    INTERROGATED_BY_GROUP_8(28),
    INTERROGATED_BY_GROUP_9(29),
    INTERROGATED_BY_GROUP_10(30),
    INTERROGATED_BY_GROUP_11(31),
    INTERROGATED_BY_GROUP_12(32),
    INTERROGATED_BY_GROUP_13(33),
    INTERROGATED_BY_GROUP_14(34),
    INTERROGATED_BY_GROUP_15(35),
    INTERROGATED_BY_GROUP_16(36),
    REQUESTED_BY_GENERAL_COUNTER(37),
    REQUESTED_BY_GROUP_1_COUNTER(38),
    REQUESTED_BY_GROUP_2_COUNTER(39),
    REQUESTED_BY_GROUP_3_COUNTER(40),
    REQUESTED_BY_GROUP_4_COUNTER(41),
    UNKNOWN_TYPE_ID(44),
    UNKNOWN_CAUSE_OF_TRANSMISSION(45),
    UNKNOWN_COMMON_ADDRESS_OF_ASDU(46),
    UNKNOWN_INFORMATION_OBJECT_ADDRESS(47);

    private static final Map<Integer, CauseOfTransmission> idMap = new HashMap<>();

    static {
        for (CauseOfTransmission enumInstance : CauseOfTransmission.values()) {
            if (idMap.put(enumInstance.getId(), enumInstance) != null) {
                throw new IllegalArgumentException("duplicate ID: " + enumInstance.getId());
            }
        }
    }

    private final int id;

    private CauseOfTransmission(int id) {
        this.id = id;
    }

    /**
     * Returns the CauseOfTransmission that corresponds to the given ID. Returns <code>null</code> if no
     * CauseOfTransmission with the given ID exists.
     *
     * @param id the ID.
     * @return the CauseOfTransmission that corresponds to the given ID.
     */
    public static CauseOfTransmission causeFor(int id) {
        return idMap.get(id);
    }

    /**
     * Returns the ID of this CauseOfTransmission.
     *
     * @return the ID.
     */
    public int getId() {
        return id;
    }

}
