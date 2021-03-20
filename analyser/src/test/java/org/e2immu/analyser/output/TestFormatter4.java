/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFormatter4 {

    private static OutputBuilder createExample0() {

        Guide.GuideGenerator gg2044 = Guide.defaultGuideGenerator();

        return new OutputBuilder().add(new TypeName("EnumMap"))
                .add(Symbol.LEFT_ANGLE_BRACKET)
                .add(gg2044.start()) // priority=false, startNL=false, endNL=false
                .add(new Text("K"))
                .add(Space.ONE)
                .add(new Text("extends"))
                .add(Space.ONE)
                .add(new TypeName("Enum"))
                .add(Symbol.LEFT_ANGLE_BRACKET)
                .add(new Text("K"))
                .add(Symbol.RIGHT_ANGLE_BRACKET)
                .add(Symbol.COMMA)
                .add(gg2044.mid()) // priority=false, startNL=false, endNL=false
                .add(new Text("V"))
                .add(gg2044.end()) // priority=false, startNL=false, endNL=false
                .add(Symbol.RIGHT_ANGLE_BRACKET);
    }


    @Test
    public void testExample0() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        Formatter formatter = new Formatter(options);
        OutputBuilder example = createExample0();

        assertEquals("EnumMap<K extends Enum<K>, V>\n", formatter.write(example));
    }
}
