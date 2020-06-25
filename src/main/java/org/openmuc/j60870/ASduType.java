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
 * Every ASDU contains a type identification field that defines the purpose and contents of the ASDU. Every Type
 * Identifier is of the form A_BB_CC_1 with the following meanings:
 *
 * <ul>
 *
 * <li>A - can be 'M' for information in monitor direction, 'C' for system information in control direction, 'P' for
 * parameter in control direction or 'F' for file transfer.</li>
 *
 * <li>BB - a two letter abbreviation of the function of the message (e.g. "SC" for Single Command)</li>
 *
 * <li>CC - additional information to distinguish different messages with the same function (e.g. "NA" for no timestamp
 * and "TA" for with timestamp)</li>
 *
 * </ul>
 */
public enum ASduType {

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

    PRIVATE_128(128),
    PRIVATE_129(129),
    PRIVATE_130(130),
    PRIVATE_131(131),
    PRIVATE_132(132),
    PRIVATE_133(133),
    PRIVATE_134(134),
    PRIVATE_135(135),
    PRIVATE_136(136),
    PRIVATE_137(137),
    PRIVATE_138(138),
    PRIVATE_139(139),
    PRIVATE_140(140),
    PRIVATE_141(141),
    PRIVATE_142(142),
    PRIVATE_143(143),
    PRIVATE_144(144),
    PRIVATE_145(145),
    PRIVATE_146(146),
    PRIVATE_147(147),
    PRIVATE_148(148),
    PRIVATE_149(149),
    PRIVATE_150(150),
    PRIVATE_151(151),
    PRIVATE_152(152),
    PRIVATE_153(153),
    PRIVATE_154(154),
    PRIVATE_155(155),
    PRIVATE_156(156),
    PRIVATE_157(157),
    PRIVATE_158(158),
    PRIVATE_159(159),
    PRIVATE_160(160),
    PRIVATE_161(161),
    PRIVATE_162(162),
    PRIVATE_163(163),
    PRIVATE_164(164),
    PRIVATE_165(165),
    PRIVATE_166(166),
    PRIVATE_167(167),
    PRIVATE_168(168),
    PRIVATE_169(169),
    PRIVATE_170(170),
    PRIVATE_171(171),
    PRIVATE_172(172),
    PRIVATE_173(173),
    PRIVATE_174(174),
    PRIVATE_175(175),
    PRIVATE_176(176),
    PRIVATE_177(177),
    PRIVATE_178(178),
    PRIVATE_179(179),
    PRIVATE_180(180),
    PRIVATE_181(181),
    PRIVATE_182(182),
    PRIVATE_183(183),
    PRIVATE_184(184),
    PRIVATE_185(185),
    PRIVATE_186(186),
    PRIVATE_187(187),
    PRIVATE_188(188),
    PRIVATE_189(189),
    PRIVATE_190(190),
    PRIVATE_191(191),
    PRIVATE_192(192),
    PRIVATE_193(193),
    PRIVATE_194(194),
    PRIVATE_195(195),
    PRIVATE_196(196),
    PRIVATE_197(197),
    PRIVATE_198(198),
    PRIVATE_199(199),
    PRIVATE_200(200),
    PRIVATE_201(201),
    PRIVATE_202(202),
    PRIVATE_203(203),
    PRIVATE_204(204),
    PRIVATE_205(205),
    PRIVATE_206(206),
    PRIVATE_207(207),
    PRIVATE_208(208),
    PRIVATE_209(209),
    PRIVATE_210(210),
    PRIVATE_211(211),
    PRIVATE_212(212),
    PRIVATE_213(213),
    PRIVATE_214(214),
    PRIVATE_215(215),
    PRIVATE_216(216),
    PRIVATE_217(217),
    PRIVATE_218(218),
    PRIVATE_219(219),
    PRIVATE_220(220),
    PRIVATE_221(221),
    PRIVATE_222(222),
    PRIVATE_223(223),
    PRIVATE_224(224),
    PRIVATE_225(225),
    PRIVATE_226(226),
    PRIVATE_227(227),
    PRIVATE_228(228),
    PRIVATE_229(229),
    PRIVATE_230(230),
    PRIVATE_231(231),
    PRIVATE_232(232),
    PRIVATE_233(233),
    PRIVATE_234(234),
    PRIVATE_235(235),
    PRIVATE_236(236),
    PRIVATE_237(237),
    PRIVATE_238(238),
    PRIVATE_239(239),
    PRIVATE_240(240),
    PRIVATE_241(241),
    PRIVATE_242(242),
    PRIVATE_243(243),
    PRIVATE_244(244),
    PRIVATE_245(245),
    PRIVATE_246(246),
    PRIVATE_247(247),
    PRIVATE_248(248),
    PRIVATE_249(249),
    PRIVATE_250(250),
    PRIVATE_251(251),
    PRIVATE_252(252),
    PRIVATE_253(253),
    PRIVATE_254(254),
    PRIVATE_255(255);

    private static final Map<Integer, ASduType> idMap = new HashMap<>();

    static {
        for (ASduType enumInstance : ASduType.values()) {
            if (idMap.put(enumInstance.getId(), enumInstance) != null) {
                throw new IllegalArgumentException("duplicate ID: " + enumInstance.getId());
            }
        }
    }

    private final int id;
    private final String description;

    private ASduType(int id) {
        this(id, "private range");
    }

    private ASduType(int id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Returns the ASduType that corresponds to the given ID. Returns <code>null</code> if no ASduType with the given ID
     * exists.
     *
     * @param id the ID
     * @return the ASduType that corresponds to the given ID
     */
    public static ASduType typeFor(int id) {
        return idMap.get(id);
    }

    /**
     * Returns the description of this ASduType.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the ID of this ASduType.
     *
     * @return the ID
     */
    public int getId() {
        return id;
    }
}
