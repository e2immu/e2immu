
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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.CurrentExceeds;
import org.e2immu.analyser.output.formatter.Forward;
import org.e2immu.analyser.output.formatter.ForwardInfo;
import org.e2immu.analyser.output.formatter.GuideOnStack;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Output_03_FormatterForward extends CommonTestRunner {

    public Test_Output_03_FormatterForward() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testSupportAndUtilClasses(List.of(Forward.class,
                        CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                7, 20, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
