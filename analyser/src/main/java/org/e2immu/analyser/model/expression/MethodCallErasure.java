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
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class MethodCallErasure extends BaseExpression implements ErasureExpression {
    private final Set<ParameterizedType> returnTypes;
    private final String methodName;

    public MethodCallErasure(Set<ParameterizedType> returnTypes, String methodName) {
        super(Identifier.CONSTANT, 10);
        this.returnTypes = returnTypes;
        this.methodName = methodName;
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException(toString());
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(toString()));
    }

    @Override
    public String toString() {
        return "<method call erasure of " + methodName + ", returning " + returnTypes + ">";
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        throw new UnsupportedOperationException("Types referenced of " + this);
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        throw new ExpressionComparator.InternalError();
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        return returnTypes;
    }

    public String methodName() {
        return methodName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MethodCallErasure) obj;
        return Objects.equals(this.returnTypes, that.returnTypes) &&
                Objects.equals(this.methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnTypes, methodName);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(Visitor visitor) {
        throw new UnsupportedOperationException();
    }
}
