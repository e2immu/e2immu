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

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.SetUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MethodReference extends ExpressionWithMethodReferenceResolution {

    // either "this", a variable, or a type
    public final Expression scope;

    public MethodReference(Expression scope, MethodInfo methodInfo, ParameterizedType concreteType) {
        super(methodInfo, concreteType);
        this.scope = scope;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MethodReference(translationMap.translateExpression(scope),
                methodInfo,
                translationMap.translateType(concreteReturnType));
    }

    @Override
    public String expressionString(int indent) {
        String methodName = methodInfo.isConstructor ? "new" : methodInfo.name;
        return scope.expressionString(0) + "::" + methodName;
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public Set<String> imports() {
        return methodInfo.returnType().typeInfo != null ?
                Set.of(methodInfo.returnType().typeInfo.fullyQualifiedName)
                : Set.of();
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return SetUtil.immutableUnion(methodInfo.returnType().typesReferenced(), scope.typesReferenced());
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
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        StatementAnalyser.checkForIllegalMethodUsageIntoNestedOrEnclosingType(methodInfo, evaluationContext);

        Value value = scope.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);
        Value result;

        if (methodInfo.isConstructor) {
            // construction, similar to NewObject, without parameters
            // TODO arrays?
            result = new Instance(methodInfo.returnType(), methodInfo, List.of(), evaluationContext);
        } else {
            // normal method call, very similar to MethodCall.evaluate

            // check the not-null aspect
            int notNull = MultiLevel.value(methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            int forwardNotNull = MultiLevel.value(forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);

            if (forwardNotNull == MultiLevel.EFFECTIVE && notNull == MultiLevel.FALSE) {
                // we're in a @NotNul context, and the method is decidedly NOT @NotNull...
                evaluationContext.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION, "Result of method reference " + methodInfo.distinguishingName());
            }
            ObjectFlow objectFlow = ObjectFlow.NO_FLOW; // TODO
            if (methodInfo.methodAnalysis.get().singleReturnValue.isSet()) {
                Value singleValue = methodInfo.methodAnalysis.get().singleReturnValue.get();
                if (!(singleValue instanceof UnknownValue) && methodInfo.cannotBeOverridden()) {
                    result = singleValue;
                } else {
                    if (value instanceof NullValue) {
                        evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
                    }
                    result = new MethodValue(methodInfo, value, List.of(), objectFlow);
                }
            } else if (methodInfo.hasBeenDefined()) {
                result = UnknownValue.NO_VALUE; // delay, waiting
            } else {
                if (value instanceof NullValue) {
                    evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
                }
                result = new MethodValue(methodInfo, value, List.of(), objectFlow);
            }

        }
        visitor.visit(this, evaluationContext, result);
        return result;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(scope);
    }
}
