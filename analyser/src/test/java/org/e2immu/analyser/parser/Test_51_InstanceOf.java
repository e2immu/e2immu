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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Test_51_InstanceOf extends CommonTestRunner {

    public Test_51_InstanceOf() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("InstanceOf_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("InstanceOf_1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof FieldReference fr && "number".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() == 0 ? "in instanceof Number number?<s:Object>:3.14"
                            : "in instanceof Number number?in/*@NotNull*/:3.14";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                    assertEquals("in/*@NotNull*/", d.currentValue().toString());
                }
            }
        };

        testClass("InstanceOf_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                        assertEquals("in/*@NotNull*/", d.currentValue().toString());
                    }
                }
                if ("0.0.0".equals(d.statementId())) {
                    if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                        assertEquals("in/*@NotNull*/", d.currentValue().toString());
                    }
                    if (d.variable() instanceof LocalVariableReference lvr && "integer".equals(lvr.simpleName())) {
                        assertEquals("in/*@NotNull*/", d.currentValue().toString());
                    }
                }
                assertFalse("1".equals(d.statementId()) && d.variable() instanceof LocalVariableReference,
                        "Found " + d.variable().fullyQualifiedName());
            }
        };

        testClass("InstanceOf_2", 0, 0, new DebugConfiguration.Builder()
                //.addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("InstanceOf_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertFalse("0.0.0".equals(d.statementId()) && d.variable() instanceof LocalVariableReference,
                        "Found " + d.variable().fullyQualifiedName() + " in if() { } part");
                if ("0.1.0".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("", d.currentValue().toString());
                }
                if ("1".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("", d.currentValue().toString());
                }
            }
        };

        testClass("InstanceOf_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertFalse("1.0.0".equals(d.statementId()) && d.variable() instanceof LocalVariableReference,
                        "Found " + d.variable().fullyQualifiedName() + " in if() { } part");
                if ("1.1.0".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("", d.currentValue().toString());
                }
                assertFalse("2".equals(d.statementId()) && d.variable() instanceof LocalVariableReference,
                        "Found " + d.variable().fullyQualifiedName() + " in 2");
            }
        };

        testClass("InstanceOf_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("InstanceOf_6", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("InstanceOf_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
