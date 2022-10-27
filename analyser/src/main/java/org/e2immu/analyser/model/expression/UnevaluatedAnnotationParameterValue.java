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

package org.e2immu.analyser.model.expression;


import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

import java.util.Objects;
import java.util.function.Predicate;

/*
This is a marker for the Resolver to replace this by a properly resolved VariableExpression.
See e.g. EventuallyE1Immutable_0
 */
public final class UnevaluatedAnnotationParameterValue extends BaseExpression implements Expression {
    private final ForwardReturnTypeInfo forwardReturnTypeInfo;
    private final com.github.javaparser.ast.expr.Expression expression;

    public UnevaluatedAnnotationParameterValue(Identifier identifier,
                                               ForwardReturnTypeInfo forwardReturnTypeInfo,
                                               com.github.javaparser.ast.expr.Expression expression) {
        super(identifier, 10);
        this.forwardReturnTypeInfo = forwardReturnTypeInfo;
        this.expression = expression;
    }

    @Override
    public ParameterizedType returnType() {
        return forwardReturnTypeInfo.type();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("<unevaluated annotation value@" + expression.getBegin().orElseThrow() + ">"));
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return 0;
    }

    public ForwardReturnTypeInfo forwardReturnTypeInfo() {
        return forwardReturnTypeInfo;
    }

    public com.github.javaparser.ast.expr.Expression expression() {
        return expression;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (UnevaluatedAnnotationParameterValue) obj;
        return Objects.equals(this.identifier, that.identifier) &&
                Objects.equals(this.forwardReturnTypeInfo, that.forwardReturnTypeInfo) &&
                Objects.equals(this.expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, forwardReturnTypeInfo, expression);
    }

    @Override
    public String toString() {
        return "UnevaluatedAnnotationParameterValue[" +
                "identifier=" + identifier + ", " +
                "forwardReturnTypeInfo=" + forwardReturnTypeInfo + ", " +
                "expression=" + expression + ']';
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        throw new UnsupportedOperationException();
    }
}
