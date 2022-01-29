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

import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;

import java.util.List;

public class ExpressionAsStatement extends StatementWithExpression {

    private final boolean synthetic;

    public ExpressionAsStatement(Identifier identifier, Expression expression) {
        this(identifier, expression, false);
    }

    public ExpressionAsStatement(Identifier identifier, Expression expression, boolean synthetic) {
        super(identifier, createCodeOrganization(expression), expression);
        this.synthetic = synthetic;
    }

    @Override
    public boolean isSynthetic() {
        return synthetic;
    }

    private static Structure createCodeOrganization(Expression expression) {
        Structure.Builder builder = new Structure.Builder();
        builder.setForwardEvaluationInfo(ForwardEvaluationInfo.DEFAULT);
        if (expression instanceof LocalVariableCreation) {
            builder.addInitialisers(List.of(expression));
        } else {
            builder.setExpression(expression);
        }
        if (expression instanceof Lambda) {
            builder.setBlock(((Lambda) expression).block);
        }
        return builder.build();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        Expression tex = translationMap.translateExpression(expression);
        if (tex == expression) return this;
        return new ExpressionAsStatement(identifier, tex);
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(expression.output(qualification)).add(Symbol.SEMICOLON)
                .addIfNotNull(messageComment(statementAnalysis));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public String toString() {
        return "ExpressionAsStatement{" + expression.getClass() + ": " + expression + "}";
    }
}
