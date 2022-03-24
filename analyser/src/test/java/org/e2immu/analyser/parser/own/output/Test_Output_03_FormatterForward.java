
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
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.CurrentExceeds;
import org.e2immu.analyser.output.formatter.Forward;
import org.e2immu.analyser.output.formatter.ForwardInfo;
import org.e2immu.analyser.output.formatter.GuideOnStack;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Output_03_FormatterForward extends CommonTestRunner {

    public Test_Output_03_FormatterForward() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("8.0.4.1.0.1.0.0.07".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:symbol.right().split>:<f:NEVER>"
                        : "!nullable instance type Boolean&&outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1&&(!wroteOnce$8||-(outputElement instanceof Symbol&&(nullable instance type String).length()>=1&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1?nullable instance type String:list.get(pos$8).write(options)).length()-chars$8+maxChars>=(instance type boolean&&wroteOnce$8&&!(outputElement instanceof Guide guide)&&!(outputElement instanceof Guide)&&!(outputElement instanceof Space space)&&!(outputElement instanceof Space)&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1&&(instance type boolean&&wroteOnce$8&&outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1?nullable instance type String:list.get(pos$8).write(options)).length()>=1?1:0)||!(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1&&(outputElement instanceof Symbol&&(nullable instance type String).length()>=1&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1?nullable instance type String:list.get(pos$8).write(options)).length()+chars$8-maxChars+(instance type boolean&&wroteOnce$8&&!(outputElement instanceof Guide guide)&&!(outputElement instanceof Guide)&&!(outputElement instanceof Space space)&&!(outputElement instanceof Space)&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1&&(instance type boolean&&wroteOnce$8&&outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE?nullable instance type String:list.get(pos$8).write(options)).length()>=1?nullable instance type String:list.get(pos$8).write(options)).length()>=1?1:0)>0?instance type boolean&&list.get(pos$8)/*(Symbol)*/.left().split!=Split.NEVER:instance type boolean))?list.get(pos$8)/*(Symbol)*/.right().split:Split.NEVER";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertEquals(d.iteration() == 0, d.evaluationResult().value().isDelayed());
                assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());

                // delay in iteration 1 is caused by absence of value properties of the non-existent (not in statementAnalysis.variables())
                // list.get(pos$8)/*(org.e2immu.analyser.output.Symbol)*/.left().split
                if (d.iteration() > 0) {
                    assertEquals(5, d.evaluationResult().changeData().size());
                    // does not contain this list.get(...).left().split variable
                    String scopes = d.evaluationResult().changeData().keySet().stream()
                            .filter(v -> v instanceof FieldReference fr && "split".equals(fr.fieldInfo.name))
                            .map(v -> ((FieldReference) v).scope.toString())
                            .sorted().collect(Collectors.joining(", "));
                    assertEquals("list.get(pos$8)/*(org.e2immu.analyser.output.Symbol)*/.left(), symbol.left(), symbol.right()", scopes);
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof FieldReference fr && "split".equals(fr.fieldInfo.name)) {
                if ("8.0.4.1.0.1.0.0.07".equals(d.statementId())) {
                    if ("symbol.left()".equals(fr.scope.toString())) {
                        String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:symbol.left().split>:nullable instance type Split" : "nullable instance type Split";
                        assertEquals(expected, d.currentValue().toString());
                    } else if ("symbol.right()".equals(fr.scope.toString())) {
                        String expected = d.iteration() == 0 ? "<instanceOf:Symbol>?<f:symbol.right().split>:nullable instance type Split" : "nullable instance type Split";
                        assertEquals(expected, d.currentValue().toString());
                    } else fail("Scope " + fr.scope);
                }
            }
        };
        testSupportAndUtilClasses(List.of(Forward.class,
                        CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                5, 21, new DebugConfiguration.Builder()
                ////        .addEvaluationResultVisitor(evaluationResultVisitor)
                 //       .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
