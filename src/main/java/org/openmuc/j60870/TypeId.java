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

import java.util.HashMap;
import java.util.Map;

/**
 * Every ASDU contains a type identification field that defines the purpose and contents of the ASDU. Every Type
 * Identifier is of the form A_BB_CC_1 with the following meanings:
 * <ul>
 * <li>A - can be 'M' for information in monitor direction, 'C' for system information in control direction, 'P' for
 * parameter in control direction or 'F' for file transfer.</li>
 * <li>BB - a two letter abbreviation of the function of the message (e.g. "SC" for Single Command)</li>
 * <li>CC - additional information to distinguish different messages with the same function (e.g. "NA" for no timestamp
 * and "TA" for with timestamp)</li>
 * </ul>
 * 
 * @author Stefan Feuerhahn
 * 
 */
public enum TypeId {

    /**
     * 1 - Single-point information without time tag
     */
    M_SP_NA_1(1, "Single-point information without time tag"),
    /**
     * 2 - Single-point information with time tag
     */
    M_SP_TA_1(2, "Single-point information with time tag"),
    /**
     * 3 - Double-point information without time tag
     */
    M_DP_NA_1(3, "Double-point information without time tag"),
    /**
     * 4 - Double-point information with time tag
     */
    M_DP_TA_1(4, "Double-point information with time tag"),
    /**
     * 5 - Step position information
     */
    M_ST_NA_1(5, "Step position information"),
    /**
     * 6 - Step position information with time tag
     */
    M_ST_TA_1(6, "Step position information with time tag"),
    /**
     * 7 - Bitstring of 32 bit
     */
    M_BO_NA_1(7, "Bitstring of 32 bit"),
    /**
     * 8 - Bitstring of 32 bit with time tag
     */
    M_BO_TA_1(8, "Bitstring of 32 bit with time tag"),
    /**
     * 9 - Measured value, normalized value
     */
    M_ME_NA_1(9, "Measured value, normalized value"),
    /**
     * 10 - Measured value, normalized value with time tag
     */
    M_ME_TA_1(10, "Measured value, normalized value with time tag"),
    /**
     * 11 - Measured value, scaled value
     */
    M_ME_NB_1(11, "Measured value, scaled value"),
    /**
     * 12 - Measured value, scaled value with time tag
     */
    M_ME_TB_1(12, "Measured value, scaled value with time tag"),
    /**
     * 13 - Measured value, short floating point number
     */
    M_ME_NC_1(13, "Measured value, short floating point number"),
    /**
     * 14 - Measured value, short floating point number with time tag
     */
    M_ME_TC_1(14, "Measured value, short floating point number with time tag"),
    /**
     * 15 - Integrated totals
     */
    M_IT_NA_1(15, "Integrated totals"),
    /**
     * 16 - Integrated totals with time tag
     */
    M_IT_TA_1(16, "Integrated totals with time tag"),
    /**
     * 17 - Event of protection equipment with time tag
     */
    M_EP_TA_1(17, "Event of protection equipment with time tag"),
    /**
     * 18 - Packed start events of protection equipment with time tag
     */
    M_EP_TB_1(18, "Packed start events of protection equipment with time tag"),
    /**
     * 19 - Packed output circuit information of protection equipment with time tag
     */
    M_EP_TC_1(19, "Packed output circuit information of protection equipment with time tag"),
    /**
     * 20 - Packed single-point information with status change detection
     */
    M_PS_NA_1(20, "Packed single-point information with status change detection"),
    /**
     * 21 - Measured value, normalized value without quality descriptor
     */
    M_ME_ND_1(21, "Measured value, normalized value without quality descriptor"),
    /**
     * 30 - Single-point information with time tag CP56Time2a
     */
    M_SP_TB_1(30, "Single-point information with time tag CP56Time2a"),
    /**
     * 31 - Double-point information with time tag CP56Time2a
     */
    M_DP_TB_1(31, "Double-point information with time tag CP56Time2a"),
    /**
     * 32 - Step position information with time tag CP56Time2a
     */
    M_ST_TB_1(32, "Step position information with time tag CP56Time2a"),
    /**
     * 33 - Bitstring of 32 bits with time tag CP56Time2a
     */
    M_BO_TB_1(33, "Bitstring of 32 bits with time tag CP56Time2a"),
    /**
     * 34 - Measured value, normalized value with time tag CP56Time2a
     */
    M_ME_TD_1(34, "Measured value, normalized value with time tag CP56Time2a"),
    /**
     * 35 - Measured value, scaled value with time tag CP56Time2a
     */
    M_ME_TE_1(35, "Measured value, scaled value with time tag CP56Time2a"),
    /**
     * 36 - Measured value, short floating point number with time tag CP56Time2a
     */
    M_ME_TF_1(36, "Measured value, short floating point number with time tag CP56Time2a"),
    /**
     * 37 - Integrated totals with time tag CP56Time2a
     */
    M_IT_TB_1(37, "Integrated totals with time tag CP56Time2a"),
    /**
     * 38 - Event of protection equipment with time tag CP56Time2a
     */
    M_EP_TD_1(38, "Event of protection equipment with time tag CP56Time2a"),
    /**
     * 39 - Packed start events of protection equipment with time tag CP56Time2a
     */
    M_EP_TE_1(39, "Packed start events of protection equipment with time tag CP56Time2a"),
    /**
     * 40 - Packed output circuit information of protection equipment with time tag CP56Time2a
     */
    M_EP_TF_1(40, "Packed output circuit information of protection equipment with time tag CP56Time2a"),
    /**
     * 45 - Single command
     */
    C_SC_NA_1(45, "Single command"),
    /**
     * 46 - Double command
     */
    C_DC_NA_1(46, "Double command"),
    /**
     * 47 - Regulating step command
     */
    C_RC_NA_1(47, "Regulating step command"),
    /**
     * 48 - Set point command, normalized value
     */
    C_SE_NA_1(48, "Set point command, normalized value"),
    /**
     * 49 - Set point command, scaled value
     */
    C_SE_NB_1(49, "Set point command, scaled value"),
    /**
     * 50 - Set point command, short floating point number
     */
    C_SE_NC_1(50, "Set point command, short floating point number"),
    /**
     * 51 - Bitstring of 32 bits
     */
    C_BO_NA_1(51, "Bitstring of 32 bits"),
    /**
     * 58 - Single command with time tag CP56Time2a
     */
    C_SC_TA_1(58, "Single command with time tag CP56Time2a"),
    /**
     * 59 - Double command with time tag CP56Time2a
     */
    C_DC_TA_1(59, "Double command with time tag CP56Time2a"),
    /**
     * 60 - Regulating step command with time tag CP56Time2a
     */
    C_RC_TA_1(60, "Regulating step command with time tag CP56Time2a"),
    /**
     * 61 - Set-point command with time tag CP56Time2a, normalized value
     */
    C_SE_TA_1(61, "Set-point command with time tag CP56Time2a, normalized value"),
    /**
     * 62 - Set-point command with time tag CP56Time2a, scaled value
     */
    C_SE_TB_1(62, "Set-point command with time tag CP56Time2a, scaled value"),
    /**
     * 63 - C_SE_TC_1 Set-point command with time tag CP56Time2a, short floating point number
     */
    C_SE_TC_1(63, "C_SE_TC_1 Set-point command with time tag CP56Time2a, short floating point number"),
    /**
     * 64 - Bitstring of 32 bit with time tag CP56Time2a
     */
    C_BO_TA_1(64, "Bitstring of 32 bit with time tag CP56Time2a"),
    /**
     * 70 - End of initialization
     */
    M_EI_NA_1(70, "End of initialization"),
    /**
     * 100 - Interrogation command
     */
    C_IC_NA_1(100, "Interrogation command"),
    /**
     * 101 - Counter interrogation command
     */
    C_CI_NA_1(101, "Counter interrogation command"),
    /**
     * 102 - Read command
     */
    C_RD_NA_1(102, "Read command"),
    /**
     * 103 - Clock synchronization command
     */
    C_CS_NA_1(103, "Clock synchronization command"),
    /**
     * 104 - Test command
     */
    C_TS_NA_1(104, "Test command"),
    /**
     * 105 - Reset process command
     */
    C_RP_NA_1(105, "Reset process command"),
    /**
     * 106 - Delay acquisition command
     */
    C_CD_NA_1(106, "Delay acquisition command"),
    /**
     * 107 - Test command with time tag CP56Time2a
     */
    C_TS_TA_1(107, "Test command with time tag CP56Time2a"),
    /**
     * 110 - Parameter of measured value, normalized value
     */
    P_ME_NA_1(110, "Parameter of measured value, normalized value"),
    /**
     * 111 - Parameter of measured value, scaled value
     */
    P_ME_NB_1(111, "Parameter of measured value, scaled value"),
    /**
     * 112 - Parameter of measured value, short floating point number
     */
    P_ME_NC_1(112, "Parameter of measured value, short floating point number"),
    /**
     * 113 - Parameter activation
     */
    P_AC_NA_1(113, "Parameter activation"),
    /**
     * 120 - File ready
     */
    F_FR_NA_1(120, "File ready"),
    /**
     * 121 - Section ready
     */
    F_SR_NA_1(121, "Section ready"),
    /**
     * 122 - Call directory, select file, call file, call section
     */
    F_SC_NA_1(122, "Call directory, select file, call file, call section"),
    /**
     * 123 - Last section, last segment
     */
    F_LS_NA_1(123, "Last section, last segment"),
    /**
     * 124 - Ack file, ack section
     */
    F_AF_NA_1(124, "Ack file, ack section"),
    /**
     * 125 - Segment
     */
    F_SG_NA_1(125, "Segment"),
    /**
     * 126 - Directory
     */
    F_DR_TA_1(126, "Directory"),
    /**
     * 127 - QueryLog, request archive file
     */
    F_SC_NB_1(127, "QueryLog, request archive file"),

    PRIVATE_128(128, "private range"),
    PRIVATE_129(129, "private range"),
    PRIVATE_130(130, "private range"),
    PRIVATE_131(131, "private range"),
    PRIVATE_132(132, "private range"),
    PRIVATE_133(133, "private range"),
    PRIVATE_134(134, "private range"),
    PRIVATE_135(135, "private range"),
    PRIVATE_136(136, "private range"),
    PRIVATE_137(137, "private range"),
    PRIVATE_138(138, "private range"),
    PRIVATE_139(139, "private range"),
    PRIVATE_140(140, "private range"),
    PRIVATE_141(141, "private range"),
    PRIVATE_142(142, "private range"),
    PRIVATE_143(143, "private range"),
    PRIVATE_144(144, "private range"),
    PRIVATE_145(145, "private range"),
    PRIVATE_146(146, "private range"),
    PRIVATE_147(147, "private range"),
    PRIVATE_148(148, "private range"),
    PRIVATE_149(149, "private range"),
    PRIVATE_150(150, "private range"),
    PRIVATE_151(151, "private range"),
    PRIVATE_152(152, "private range"),
    PRIVATE_153(153, "private range"),
    PRIVATE_154(154, "private range"),
    PRIVATE_155(155, "private range"),
    PRIVATE_156(156, "private range"),
    PRIVATE_157(157, "private range"),
    PRIVATE_158(158, "private range"),
    PRIVATE_159(159, "private range"),
    PRIVATE_160(160, "private range"),
    PRIVATE_161(161, "private range"),
    PRIVATE_162(162, "private range"),
    PRIVATE_163(163, "private range"),
    PRIVATE_164(164, "private range"),
    PRIVATE_165(165, "private range"),
    PRIVATE_166(166, "private range"),
    PRIVATE_167(167, "private range"),
    PRIVATE_168(168, "private range"),
    PRIVATE_169(169, "private range"),
    PRIVATE_170(170, "private range"),
    PRIVATE_171(171, "private range"),
    PRIVATE_172(172, "private range"),
    PRIVATE_173(173, "private range"),
    PRIVATE_174(174, "private range"),
    PRIVATE_175(175, "private range"),
    PRIVATE_176(176, "private range"),
    PRIVATE_177(177, "private range"),
    PRIVATE_178(178, "private range"),
    PRIVATE_179(179, "private range"),
    PRIVATE_180(180, "private range"),
    PRIVATE_181(181, "private range"),
    PRIVATE_182(182, "private range"),
    PRIVATE_183(183, "private range"),
    PRIVATE_184(184, "private range"),
    PRIVATE_185(185, "private range"),
    PRIVATE_186(186, "private range"),
    PRIVATE_187(187, "private range"),
    PRIVATE_188(188, "private range"),
    PRIVATE_189(189, "private range"),
    PRIVATE_190(190, "private range"),
    PRIVATE_191(191, "private range"),
    PRIVATE_192(192, "private range"),
    PRIVATE_193(193, "private range"),
    PRIVATE_194(194, "private range"),
    PRIVATE_195(195, "private range"),
    PRIVATE_196(196, "private range"),
    PRIVATE_197(197, "private range"),
    PRIVATE_198(198, "private range"),
    PRIVATE_199(199, "private range"),
    PRIVATE_200(200, "private range"),
    PRIVATE_201(201, "private range"),
    PRIVATE_202(202, "private range"),
    PRIVATE_203(203, "private range"),
    PRIVATE_204(204, "private range"),
    PRIVATE_205(205, "private range"),
    PRIVATE_206(206, "private range"),
    PRIVATE_207(207, "private range"),
    PRIVATE_208(208, "private range"),
    PRIVATE_209(209, "private range"),
    PRIVATE_210(210, "private range"),
    PRIVATE_211(211, "private range"),
    PRIVATE_212(212, "private range"),
    PRIVATE_213(213, "private range"),
    PRIVATE_214(214, "private range"),
    PRIVATE_215(215, "private range"),
    PRIVATE_216(216, "private range"),
    PRIVATE_217(217, "private range"),
    PRIVATE_218(218, "private range"),
    PRIVATE_219(219, "private range"),
    PRIVATE_220(220, "private range"),
    PRIVATE_221(221, "private range"),
    PRIVATE_222(222, "private range"),
    PRIVATE_223(223, "private range"),
    PRIVATE_224(224, "private range"),
    PRIVATE_225(225, "private range"),
    PRIVATE_226(226, "private range"),
    PRIVATE_227(227, "private range"),
    PRIVATE_228(228, "private range"),
    PRIVATE_229(229, "private range"),
    PRIVATE_230(230, "private range"),
    PRIVATE_231(231, "private range"),
    PRIVATE_232(232, "private range"),
    PRIVATE_233(233, "private range"),
    PRIVATE_234(234, "private range"),
    PRIVATE_235(235, "private range"),
    PRIVATE_236(236, "private range"),
    PRIVATE_237(237, "private range"),
    PRIVATE_238(238, "private range"),
    PRIVATE_239(239, "private range"),
    PRIVATE_240(240, "private range"),
    PRIVATE_241(241, "private range"),
    PRIVATE_242(242, "private range"),
    PRIVATE_243(243, "private range"),
    PRIVATE_244(244, "private range"),
    PRIVATE_245(245, "private range"),
    PRIVATE_246(246, "private range"),
    PRIVATE_247(247, "private range"),
    PRIVATE_248(248, "private range"),
    PRIVATE_249(249, "private range"),
    PRIVATE_250(250, "private range"),
    PRIVATE_251(251, "private range"),
    PRIVATE_252(252, "private range"),
    PRIVATE_253(253, "private range"),
    PRIVATE_254(254, "private range"),
    PRIVATE_255(255, "private range");

    private final int id;
    private final String description;

    private static final Map<Integer, TypeId> idMap = new HashMap<Integer, TypeId>();

    static {
        for (TypeId enumInstance : TypeId.values()) {
            if (idMap.put(enumInstance.getId(), enumInstance) != null) {
                throw new IllegalArgumentException("duplicate ID: " + enumInstance.getId());
            }
        }
    }

    private TypeId(int id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Returns the description of this TypeId.
     * 
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the ID of this TypeId.
     * 
     * @return the ID
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the TypeId that corresponds to the given ID. Returns <code>null</code> if no TypeId with the given ID
     * exists.
     * 
     * @param id
     *            the ID
     * @return the TypeId that corresponds to the given ID
     */
    public static TypeId getInstance(int id) {
        return idMap.get(id);
    }
}
