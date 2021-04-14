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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_48_Store extends CommonTestRunner {

    public Test_48_Store() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass(List.of("Project_0", "Store_0"), 1, 7, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo mapEntry = typeMap.get(Map.Entry.class);
            MethodInfo getValue = mapEntry.findUniqueMethod("getValue", 0);
            assertEquals(Level.DELAY, getValue.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            assertTrue(getValue.isAbstract());
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("entry".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectLinks = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectLinks, d.variableInfo().getLinkedVariables().toString());
                        int expectPm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                        assertEquals(expectPm, d.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD));
                    }
                }
            }
        };

        testClass("Store_1", 1, 7, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
