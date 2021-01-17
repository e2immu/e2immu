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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
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
    public OutputBuilder output() {
        String methodName = methodInfo.isConstructor ? "new" : methodInfo.name;
        return new OutputBuilder().add(scope.output()).add(Symbol.DOUBLE_COLON).add(new Text(methodName));
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

        if (methodInfo.isConstructor) {
            // construction, similar to NewObject, without parameters
            // TODO arrays? TODO ObjectFlow
            Location location = evaluationContext.getLocation(this);
            ObjectFlow objectFlow = builder.createInternalObjectFlow(location, methodInfo.returnType(), Origin.NEW_OBJECT_CREATION);
            MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
            NewObject initialInstance = NewObject.objectCreation(evaluationContext.getPrimitives(),
                    methodInfo, methodInfo.returnType(), Diamond.SHOW_ALL, List.of(), objectFlow);
            NewObject instance = MethodCall.checkCompanionMethodsModifying(builder, evaluationContext, methodInfo,
                    methodAnalysis, scope, initialInstance, List.of());
            builder.setExpression(instance);
        } else {
            // normal method call, very similar to MethodCall.evaluate
            MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
            // check the not-null aspect
            int notNull = MultiLevel.value(methodAnalysis.getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            int forwardNotNull = MultiLevel.value(forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);

            if (forwardNotNull == MultiLevel.EFFECTIVE && notNull == MultiLevel.FALSE) {
                // we're in a @NotNul context, and the method is decidedly NOT @NotNull...
                builder.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION, "Result of method reference " + methodInfo.distinguishingName());
            }
            Expression result;
            ObjectFlow objectFlow = ObjectFlow.NO_FLOW; // TODO
            Expression singleReturnValue = methodAnalysis.getSingleReturnValue();
            if (singleReturnValue != null) {
                if (!singleReturnValue.isUnknown() && methodInfo.cannotBeOverridden()) {
                    result = singleReturnValue;
                } else {
                    if (scopeResult.value().isInstanceOf(NullConstant.class)) {
                        builder.raiseError(Message.NULL_POINTER_EXCEPTION);
                    }
                    result = new MethodCall(scopeResult.value(), methodInfo, List.of(), objectFlow);
                }
            } else if (methodInfo.hasStatements()) {
                result = EmptyExpression.NO_VALUE; // delay, waiting
            } else {
                if (scopeResult.value() instanceof NullConstant) {
                    builder.raiseError(Message.NULL_POINTER_EXCEPTION);
                }
                result = new MethodCall(scopeResult.value(), methodInfo, List.of(), objectFlow);
            }
            builder.setExpression(result);
        }

        if (!methodInfo.methodResolution.isSet() || methodInfo.methodResolution.get().allowsInterrupts()) {
            builder.incrementStatementTime();
        }

        return builder.build();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(scope);
    }
}
