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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.ComputeIndependent;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class MethodReference extends ExpressionWithMethodReferenceResolution {

    // either "this", a variable, or a type
    public final Expression scope;

    public MethodReference(Identifier identifier,
                           Expression scope, MethodInfo methodInfo, ParameterizedType concreteType) {
        super(identifier, scope.getComplexity() + 1, methodInfo, concreteType);
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
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            scope.visit(predicate);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, methodInfo);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedScope = scope.translate(inspectionProvider, translationMap);
        ParameterizedType transType = translationMap.translateType(concreteReturnType);
        if (translatedScope == scope && transType == concreteReturnType) return this;
        return new MethodReference(identifier, translatedScope, methodInfo, transType);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_METHOD_REFERENCE;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        if (v instanceof MethodReference mr) {
            int c = methodInfo.fullyQualifiedName.compareTo(mr.methodInfo.fullyQualifiedName);
            if (c == 0) return scope.compareTo(mr.scope);
            return c;
        }
        throw new ExpressionComparator.InternalError();
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

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);

        ForwardEvaluationInfo scopeForward;

        DV contextContainer = forwardEvaluationInfo.getProperty(Property.CONTEXT_CONTAINER);
        if (contextContainer.equals(MultiLevel.NOT_CONTAINER_DV)) {
            MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
            DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);

            scopeForward = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo)
                    .addProperty(Property.CONTEXT_MODIFIED, modified)
                    .addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV)
                    .setReevaluateVariableExpressions()
                    .setNotAssignmentTarget()
                    .setComplainInlineConditional()
                    .doNotIgnoreValueFromState()
                    .build();

            // as in MethodCall, we transfer modification of static methods onto 'this'
            if (methodInfo.methodInspection.get().isStatic()) {
                This thisType = new This(context.getAnalyserContext(), context.getCurrentType());
                builder.setProperty(thisType, Property.CONTEXT_MODIFIED, modified); // without being "read"
            }
        } else {
            scopeForward = forwardEvaluationInfo.copy().notNullNotAssignment().build();
        }
        EvaluationResult scopeResult = scope.evaluate(context, scopeForward);
        builder.compose(scopeResult);
        builder.setExpression(this);
        return builder.build();
    }

    /*
     Example: other.map.forEach(this::put), in SetOnceMap.putAll
     equivalent of other.map.forEach((k,v) -> this.put(k,v))
     k and v as parameters of put are linked to this at independent_hc level, and should cause a link
     from forEach:k --3--> this, forEach:v --3--> this, so that we end up with
       other.map <--4--> this, and other <--4--> this.
     We definitely don't want other <--2--> this.

     Example:

     */
    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        if (scope instanceof TypeExpression) {
            return LinkedVariables.EMPTY;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        Map<Variable, DV> newLvMap = new HashMap<>();
        EvaluationResult scopeResult = scope.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        LinkedVariables scopeLv = scopeResult.value().linkedVariables(context);
        ComputeIndependent computeIndependent = new ComputeIndependent(context.getAnalyserContext(), context.getCurrentType());

        for (ParameterAnalysis parameterAnalysis : methodAnalysis.getParameterAnalyses()) {
            DV paramIndependent = parameterAnalysis.getProperty(Property.INDEPENDENT);
            if (!MultiLevel.INDEPENDENT_DV.equals(paramIndependent)) {
                for (Map.Entry<Variable, DV> e : scopeLv) {
                    int index = parameterAnalysis.getParameterInfo().index;
                    // the concreteReturnType is the concrete functional type; grab the type parameter!
                    ParameterizedType concreteParameterType = this.concreteReturnType.parameters.get(index);
                    DV dv = computeIndependent.linkLevelOfParameterVsScope(e.getKey().parameterizedType(),
                            e.getValue(), concreteParameterType, paramIndependent);
                    if (!LinkedVariables.LINK_INDEPENDENT.equals(dv)) {
                        newLvMap.merge(e.getKey(), dv, DV::min);
                    }
                }
            }
        }
        return LinkedVariables.of(newLvMap);
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(scope);
    }

    public boolean objectIsThisOrSuper(InspectionProvider inspectionProvider) {
        VariableExpression ve;
        if ((ve = scope.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof This) return true;
        if (scope instanceof TypeExpression) {
            MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
            return !methodInspection.isStatic();
        }
        return false;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return switch (property) {
            case NOT_NULL_EXPRESSION -> notNull(context, methodInfo);
            case CONTAINER -> MultiLevel.CONTAINER_DV;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;

            case IDENTITY, IGNORE_MODIFICATIONS, FLUENT, CONTEXT_MODIFIED -> property.falseDv;
            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
            default -> throw new UnsupportedOperationException("Property: " + property);
        };
    }

    // very similar code in Lambda; also used by InlinedMethod
    static DV notNull(EvaluationResult context, MethodInfo methodInfo) {
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        DV nne;
        if (methodAnalysis.getParameterAnalyses().isEmpty()) {
            if (methodInfo.hasReturnValue()) {
                nne = methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION);
            } else {
                return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV;
            }
        } else {
            nne = methodAnalysis.getParameterAnalyses()
                    .stream().map(pa -> pa.getProperty(Property.NOT_NULL_PARAMETER)).reduce(DV.MAX_INT_DV, DV::min);
            assert nne != DV.MAX_INT_DV;
        }
        return MultiLevel.composeOneLevelMoreNotNull(nne);
    }
}
