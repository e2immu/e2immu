/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
