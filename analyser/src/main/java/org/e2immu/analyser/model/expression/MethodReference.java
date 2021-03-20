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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;

public class MethodReference extends ExpressionWithMethodReferenceResolution {

    // either "this", a variable, or a type
    public final Expression scope;

    public MethodReference(Expression scope, MethodInfo methodInfo, ParameterizedType concreteType) {
        super(methodInfo, concreteType);
        this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodReference that = (MethodReference) o;
        return scope.equals(that.scope) && methodInfo.equals(that.methodInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, methodInfo);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MethodReference(translationMap.translateExpression(scope),
                methodInfo,
                translationMap.translateType(concreteReturnType));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NYE;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String methodName = methodInfo.isConstructor ? "new" : methodInfo.name;
        return new OutputBuilder().add(scope.output(qualification)).add(Symbol.DOUBLE_COLON).add(new Text(methodName));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        if (!methodInfo.methodInspection.isSet()) return UpgradableBooleanMap.of(scope.typesReferenced());
        return UpgradableBooleanMap.of(methodInfo.returnType().typesReferenced(false), scope.typesReferenced());
    }

    // if we pass on one of our own methods to some other method, we need to take into account our exposure to the
    // outside world...
    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        Objects.requireNonNull(evaluationContext);
        // we know the method we're passing on...
        if (evaluationContext.getCurrentType().inTypeInnerOuterHierarchy(methodInfo.typeInfo).isPresent()) {
            return SideEffect.NONE_CONTEXT;
        }
        // no idea which method we're passing on... should not be a problem
        return SideEffect.LOCAL;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        EvaluationResult scopeResult = scope.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        builder.compose(scopeResult);
        builder.setExpression(this);
        return builder.build();
    }

    private ParameterizedType makeParameterizedTypeFromContext(InspectionProvider inspectionProvider, TypeInfo typeInfo, ParameterizedType returnType) {
        return typeInfo.asParameterizedType(inspectionProvider); //  TODO
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(scope);
    }

    public boolean objectIsThisOrSuper(InspectionProvider inspectionProvider) {
        if (scope instanceof VariableExpression ve && ve.variable() instanceof This) return true;
        if (scope instanceof TypeExpression) {
            MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
            return !methodInspection.isStatic();
        }
        return false;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return switch (variableProperty) {
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL;
            case CONTAINER -> Level.TRUE;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;

            case IDENTITY, FLUENT, CONTEXT_MODIFIED -> Level.FALSE;
            default -> throw new UnsupportedOperationException("Property: "+variableProperty);
        };
    }
}
