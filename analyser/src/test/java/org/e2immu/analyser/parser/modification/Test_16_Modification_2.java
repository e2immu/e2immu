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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_2 extends CommonTestRunner {

    public Test_16_Modification_2() {
        super(true);
    }

    @Test
    public void test2() throws IOException {
        final String GET_FIRST_VALUE = "set2ter.isEmpty()?\"\":set2ter.stream().findAny().orElseThrow()";
        final String GET_FIRST_VALUE_DELAYED = "<m:isEmpty>?\"\":<m:orElseThrow>";
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getFirst".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? GET_FIRST_VALUE_DELAYED : GET_FIRST_VALUE;
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("org.e2immu.analyser.testexample.Modification_2.Example2ter.getFirst(String)".equals(d.variableName())) {
                String expect = d.iteration() == 0 ? GET_FIRST_VALUE_DELAYED : GET_FIRST_VALUE;
                assertEquals(expect, d.currentValue().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            String name = d.fieldInfo().name;
            if (name.equals("set2ter")) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 0, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if (name.equals("set2bis")) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 0, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("Modification_2", 1, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

    }
}
