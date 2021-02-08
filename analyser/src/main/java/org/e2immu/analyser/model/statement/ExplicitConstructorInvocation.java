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

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;
import java.util.stream.Collectors;

// this( )
public class ExplicitConstructorInvocation extends StatementWithStructure {

    public final boolean isSuper;

    public ExplicitConstructorInvocation(boolean isSuper, List<Expression> parameterExpressions) {
        super(new Structure.Builder().setUpdaters(parameterExpressions).build());
        this.isSuper = isSuper;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ExplicitConstructorInvocation(isSuper, structure.updaters().stream()
                .map(translationMap::translateExpression)
                .collect(Collectors.toList()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        String name = isSuper ? "super" : "this";
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text(name));
        if (structure.updaters().isEmpty()) {
            outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
        } else {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(structure.updaters().stream()
                            .map(expression -> expression.output(qualification))
                            .collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        return outputBuilder.add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
    }

    @Override
    public List<? extends Element> subElements() {
        return structure.updaters();
    }
}
