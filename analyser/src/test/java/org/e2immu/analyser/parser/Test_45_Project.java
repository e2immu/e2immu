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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_45_Project extends CommonTestRunner {

    public Test_45_Project() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {

        testClass("Project_0", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Project_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor  = d -> {
            if("value".equals(d.fieldInfo().name)) {
                int expectPm = Level.FALSE;
                assertEquals(expectPm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_PROPAGATE_MOD));
            }
        };

        testClass("Project_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
