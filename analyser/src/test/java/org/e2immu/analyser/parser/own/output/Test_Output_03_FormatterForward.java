
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
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.CurrentExceeds;
import org.e2immu.analyser.output.formatter.Forward;
import org.e2immu.analyser.output.formatter.ForwardInfo;
import org.e2immu.analyser.output.formatter.GuideOnStack;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Output_03_FormatterForward extends CommonTestRunner {

    public Test_Output_03_FormatterForward() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("8.0.4.1.0.1.0.0.07".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:<m:right>.split>:<f:NEVER>"
                        : "outputElement instanceof Symbol symbol?list.get(pos$8)/*(Symbol)*/.right().split:Split.NEVER";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertEquals(d.iteration() == 0, d.evaluationResult().value().isDelayed());
                assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());

                if (d.iteration() > 0) {
                    assertEquals(5, d.evaluationResult().changeData().size());
                    String scopes = d.evaluationResult().changeData().keySet().stream()
                            .filter(v -> v instanceof FieldReference fr && "split".equals(fr.fieldInfo.name))
                            .map(v -> ((FieldReference) v).scope.toString())
                            .sorted().collect(Collectors.joining(", "));
                    assertEquals("list.get(pos$8)/*(Symbol)*/.left(), list.get(pos$8)/*(Symbol)*/.right()", scopes);
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("symbol".equals(d.variableName())) {
                assertNotEquals("8.0.4", d.statementId());
                if ("8.0.3".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<v:outputElement>/*(Symbol)*/" : "list.get(pos$8)/*(Symbol)*/";
                    assertEquals(expected, d.currentValue().toString());
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern p) {
                        assertEquals("8.0.3.0.0", p.scope());
                    } else fail();
                }
            }
            if ("split".equals(d.variableName())) {
                String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:<m:left>.split>:<vl:split>"
                        : "outputElement instanceof Symbol symbol?list.get(pos$8)/*(Symbol)*/.left().split:nullable instance type Split";
                if ("8.0.3.0.0".equals(d.statementId()) || "8.0.3.0.5".equals(d.statementId())) {
                    String v = d.iteration() == 0 ? "<f:symbol.left().split>" : "symbol.left().split";
                    assertEquals(v, d.currentValue().toString());
                }
                // transitioning from 8.0.3.0.0->5 to 8.0.3, we see that symbol is expanded in the scope
                // this is necessary, because it disappears! so the other scopes must disappear as well!
                if ("8.0.3".equals(d.statementId())) {
                    assertEquals(expected, d.currentValue().toString());
                }
                if ("8.0.4.1.0.1.0.0.06".equals(d.statementId())) {
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if (d.variable() instanceof FieldReference fr && "split".equals(fr.fieldInfo.name)) {
                if ("8.0.4".equals(d.statementId())) {
                    assertTrue(Set.of("<m:right>", "<m:left>", "<out of scope:space:8.0.4>",
                                    "list.get(pos$8)/*(Symbol)*/.left()",
                                    "list.get(pos$8)/*(Symbol)*/.right()",
                                    "list.get(pos$8)/*(Space)*/").contains(fr.scope.toString()),
                            "Scope " + fr.scope + "; should definitely not be symbol.left() or symbol.right()");
                }
                if ("8.0.4.1.0.1.0.0.07".equals(d.statementId())) {
                    if ("list.get(pos$8)/*(Symbol)*/.left()".equals(fr.scope.toString())) {
                        String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:symbol.left().split>:nullable instance type Split"
                                : "nullable instance type Split";
                        assertEquals(expected, d.currentValue().toString());
                    } else if ("list.get(pos$8)/*(Symbol)*/.right()".equals(fr.scope.toString())) {
                        String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:symbol.right().split>:nullable instance type Split"
                                : "nullable instance type Split";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("8.0.4.1.0.1.0.0.06".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "!<m:apply>" : "!nullable instance type Boolean";
                assertEquals(expected, d.state().toString());
            }
        };
        testSupportAndUtilClasses(List.of(Forward.class,
                        CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                5, 18, new DebugConfiguration.Builder()
                  //      .addEvaluationResultVisitor(evaluationResultVisitor)
                   //     .addStatementAnalyserVisitor(statementAnalyserVisitor)
                    //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
