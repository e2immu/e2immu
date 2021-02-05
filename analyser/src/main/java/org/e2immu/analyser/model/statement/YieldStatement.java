/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;

public class YieldStatement extends StatementWithExpression {

    public YieldStatement(Expression expression) {
        super(new Structure.Builder().setExpression(expression).setForwardEvaluationInfo(ForwardEvaluationInfo.DEFAULT).build(),
                expression);
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("yield"));
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            outputBuilder.add(Space.ONE).add(expression.output());
        }
        outputBuilder.add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
        return outputBuilder;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new YieldStatement(translationMap.translateExpression(expression));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }
}
