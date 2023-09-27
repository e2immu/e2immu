
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
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

    public static final String OPTIONS = "nullable instance type FormattingOptions/*@Identity*/";

    public Test_Output_03_FormatterForward() {
        super(true);
    }

    // @Disabled("Infinite loop between And, Negation, Equals, isNotNull0")
    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if ("8.0.4.1.0.1.0.0.07".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<instanceOf:Symbol>?<dv:scope-scope-58:37:8.0.3.split>:<f:NEVER>";
                        case 1 ->
                                "outputElement instanceof Symbol symbol?<dv:scope-scope-58:37:8.0.3.split>:Split.NEVER";
                        default -> "outputElement instanceof Symbol symbol?scope-scope-58:37:8.0.3.split:Split.NEVER";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 1, d.evaluationResult().value().isDelayed());
                    assertEquals(d.iteration() <= 1, d.evaluationResult().causesOfDelay().isDelayed());

                    if (d.iteration() >= 2) {
                        assertEquals(5, d.evaluationResult().changeData().size());
                        String scopes = d.evaluationResult().changeData().keySet().stream()
                                .filter(v -> v instanceof FieldReference fr && "split".equals(fr.fieldInfo.name))
                                .map(v -> ((FieldReference) v).scope.toString())
                                .sorted().collect(Collectors.joining(", "));
                        assertEquals("scope-scope-54:25:8.0.3, scope-scope-58:37:8.0.3", scopes);
                    }
                }
                if ("8.0.4.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 2 ? "<m:combine>" :
                            "Forward.combine(list.get(pos$8) instanceof Symbol?Forward.combine(lastOneWasSpace$8,list.get(pos$8)/*(Symbol)*/.left().elementarySpace(options)):nullable instance type ElementarySpace,list.get(pos$8)/*(Space)*/.elementarySpace(options))";
                    assertEquals(expected, d.evaluationResult().value().toString());
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
                String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<dv:scope-scope-54:25:8.0.3.split>:<vl:split>"
                        : "list.get(pos$8) instanceof Symbol?scope-scope-54:25:8.0.3.split:nullable instance type Split";
                if ("8.0.3.0.0".equals(d.statementId()) || "8.0.3.0.5".equals(d.statementId())) {
                    String v = d.iteration() == 0 ? "<f:symbol.left().split>" : "(list.get(pos$8)/*(Symbol)*/.left()).split";
                    assertEquals(v, d.currentValue().toString());
                }
                // transitioning from 8.0.3.0.0->5 to 8.0.3, we see that symbol is expanded in the scope
                // this is necessary, because it disappears! so the other scopes must disappear as well!
                if ("8.0.3".equals(d.statementId())) {
                    assertEquals(expected, d.currentValue().toString());
                }
                if ("8.0.4.1.0.1.0.0.06".equals(d.statementId())) {
                    String v = switch (d.iteration()) {
                        case 0 -> "<instanceOf:Symbol>?<dv:scope-scope-54:25:8.0.3.split>:<vl:split>";
                        case 1 -> "<new:ForwardInfo>";
                        default ->
                                "outputElement instanceof Symbol symbol?scope-scope-54:25:8.0.3.split:nullable instance type Split";
                    };
                    assertEquals(v, d.currentValue().toString());
                }
            }
            if (d.variable() instanceof FieldReference fr && "split".equals(fr.fieldInfo.name)) {
                if ("8.0.4".equals(d.statementId())) {
                    assertTrue(Set.of("scope-scope-54:25:8.0.3", "scope-space:8.0.4", "scope-scope-58:37:8.0.3")
                                    .contains(fr.scope.toString()),
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
            if ("scope-scope-58:37:8.0.3".equals(d.variableName())) {
                if ("8.0.4.1.0.0.1".equals(d.statementId())) {
                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    assertTrue(d.variableInfoContainer().hasMerge());
                }
            }
            if (d.variable() instanceof ParameterInfo pi && "options".equals(pi.name)) {
                if ("8.0.2".equals(d.statementId())) {
                    assertEquals(OPTIONS, d.currentValue().toString());
                }
                if ("8.0.3.0.0".equals(d.statementId())) {
                    assertEquals(OPTIONS, d.currentValue().toString());
                }
                if ("8.0.3.0.5".equals(d.statementId())) {
                    String value = d.iteration() < 2 ? "<p:options>" : OPTIONS;
                    assertEquals(value, d.currentValue().toString());
                }
                if ("8.0.3".equals(d.statementId())) {
                    VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                    assertTrue(d.variableInfoContainer().isPrevious());
                    assertEquals(OPTIONS, vi1.getValue().toString());
                    String value = d.iteration() < 2 ? "<p:options>" : OPTIONS;
                    assertEquals(value, d.currentValue().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("8.0.4.1.0.1.0.0.06".equals(d.statementId())) {
                String expected = d.iteration() <= 1 ? "!<m:apply>"
                        : "!writer.apply(new ForwardInfo(pos,chars,stringToWrite,split,null,outputElement instanceof Symbol))";
                assertEquals(expected, d.state().toString());
            }
        };
        testSupportAndUtilClasses(List.of(Forward.class,
                        CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
