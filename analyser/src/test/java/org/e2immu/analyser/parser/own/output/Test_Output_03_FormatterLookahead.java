
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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Output_03_FormatterLookahead extends CommonTestRunner {

    public Test_Output_03_FormatterLookahead() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof FieldReference fr && "forwardInfo".equals(fr.fieldInfo.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertEquals("startOfGuides.get(0)", fr.scope.toString());

                        String expected = d.iteration() == 0 ? "<f:startOfGuides.get(0).forwardInfo>" : "instance type ForwardInfo";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("scope-52:44".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) { // if(...) statement
                        assertEquals("-1-lineLength+`forwardInfo.chars`+(null==`forwardInfo.string`?0:`forwardInfo.string`.length())>=0?nullable instance type GuideOnStack:nullable instance type GuideOnStack", d.currentValue().toString());
                        assertEquals("startOfGuides:3", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3".equals(d.statementId())) { // LVC outputElement = list.get(forwardInfo.pos())
                        String expected = d.iteration() == 0 ? "<v:scope-52:44>"
                                : "-1-lineLength+`forwardInfo.chars`+(null==`forwardInfo.string`?0:`forwardInfo.string`.length())>=0?nullable instance type GuideOnStack:nullable instance type GuideOnStack";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "currentForwardInfo:-1,exceeds:-1,forwardInfo:-1,lineLength:-1,list:-1,options:-1,outputElement:-1,prioritySplit:-1,scope-90:64:-1,scope-guideOnStack:2.0.2.forwardInfo:-1,scope-guideOnStack:2.0.2:-1,start:-1,startOfGuides.get(0).forwardInfo:-1,startOfGuides.peek().forwardInfo:-1,startOfGuides:-1";
                            case 1 -> "forwardInfo:-1,list:-1,outputElement:-1,startOfGuides:-1";
                            default -> "forwardInfo:4,startOfGuides:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                String expected = d.iteration() <= 1
                        ? "<m:apply>"
                        : "/*inline apply*/null==`forwardInfo.string`?instance type boolean:-1-lineLength+`forwardInfo.chars`+(null==`forwardInfo.string`?0:`forwardInfo.string`.length())>=0?(!startOfGuides.isEmpty()||!`forwardInfo.symbol`||null!=prioritySplit.get()||list.get(`forwardInfo.pos`)==Space.NEWLINE)&&(!startOfGuides.isEmpty()||null!=`forwardInfo.string`||null!=prioritySplit.get()||list.get(`forwardInfo.pos`)==Space.NEWLINE):list.get(`forwardInfo.pos`)==Space.NEWLINE";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                String vars = d.iteration() <= 1
                        ? "NEWLINE,forwardInfo,lineLength,list,prioritySplit,startOfGuides"
                        : "NEWLINE,lineLength,list,prioritySplit,startOfGuides";
                assertEquals(vars,
                        d.methodAnalysis().getSingleReturnValue().variables(true)
                                .stream().map(Variable::simpleName).sorted().distinct().collect(Collectors.joining(",")));
            }
        };
        testSupportAndUtilClasses(List.of(Forward.class, Lookahead.class,
                        CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                0, 3, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
