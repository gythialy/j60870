/*
 * Copyright 2014-2024 Fraunhofer ISE
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
package org.openmuc.j60870.internal.cli;

public class StringCliParameter extends ValueCliParameter {

    String value;

    StringCliParameter(CliParameterBuilder builder, String parameterName, String defaultValue) {
        super(builder, parameterName);
        value = defaultValue;
    }

    StringCliParameter(CliParameterBuilder builder, String parameterName) {
        super(builder, parameterName);
    }

    public String getValue() {
        return value;
    }

    @Override
    int parse(String[] args, int i) throws CliParseException {
        selected = true;

        if (args.length < (i + 2)) {
            throw new CliParseException("Parameter " + name + " has no value.");
        }
        value = args[i + 1];

        return 2;
    }

    @Override
    public void appendDescription(StringBuilder sb) {
        super.appendDescription(sb);
        if (value != null) {
            sb.append(" Default is ").append(value).append(".");
        }
    }
}
