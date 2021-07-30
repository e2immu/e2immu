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
    public final MethodInfo methodInfo;

    public ExplicitConstructorInvocation(Identifier identifier,
                                         boolean isSuper, MethodInfo methodInfo, List<Expression> parameterExpressions) {
        super(identifier, new Structure.Builder().setUpdaters(parameterExpressions).build());
        this.isSuper = isSuper;
        this.methodInfo = methodInfo;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ExplicitConstructorInvocation(identifier, isSuper, methodInfo, structure.updaters().stream()
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
