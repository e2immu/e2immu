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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Test_58_GuideSimplified extends CommonTestRunner {

    public Test_58_GuideSimplified() {
        super(false);
    }

    // original minus some dependencies
    @Test
    public void test_0() throws IOException {
        testClass("GuideSimplified_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // too simple
    @Test
    public void test_1() throws IOException {
        testClass("GuideSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("GuideSimplified_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // simplest situation
    @Test
    public void test_3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                assertEquals("Position", d.methodInfo().typeInfo.simpleName);
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else { // independent of delays/modification of the fields
                    assertEquals("{START,MID,END}", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("GuideSimplified_3".equals(d.methodInfo().name)) {
                ParameterAnalysis position = d.parameterAnalyses().get(1);
                int expectContextModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectContextModified, position.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMom = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, position.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                int expectModifiedVar = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModifiedVar, position.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("START".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectMom = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
            if ("position".equals(d.fieldInfo().name)) {
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("GuideSimplified_3", 1, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
