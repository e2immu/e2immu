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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MethodValue implements Value {
    public final MethodInfo methodInfo;
    public final List<Value> parameters;
    public final Value object;
    public final ObjectFlow objectFlow;
    private final Primitives primitives;

    public MethodValue(Primitives primitives,
                       @NotNull MethodInfo methodInfo, @NotNull Value object, @NotNull List<Value> parameters, ObjectFlow objectFlow) {
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.parameters = Objects.requireNonNull(parameters);
        this.object = Objects.requireNonNull(object);
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.primitives = primitives;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodValue that = (MethodValue) o;
        boolean sameMethod = methodInfo.equals(that.methodInfo) ||
                checkSpecialCasesWhereDifferentMethodsAreEquals(methodInfo, that.methodInfo);
        return sameMethod &&
                parameters.equals(that.parameters) &&
                object.equals(that.object) &&
                methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.FALSE;
    }

    /*
     the interface and the implementation, or the interface and sub-interface
     */
    private boolean checkSpecialCasesWhereDifferentMethodsAreEquals(MethodInfo m1, MethodInfo m2) {
        Set<MethodInfo> overrides1 = m1.typeInfo.overrides(primitives, m1, true);
        if (m2.typeInfo.isInterface() && overrides1.contains(m2)) return true;
        Set<MethodInfo> overrides2 = m2.typeInfo.overrides(primitives, m2, true);
        return m1.typeInfo.isInterface() && overrides2.contains(m1);

        // any other?
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, methodInfo, parameters);
    }

    // NOTE: toString() is NOT used for "official" purposes
    @Override
    public String toString() {
        return object + "." + methodInfo.name
                + parameters.stream().map(Object::toString).collect(Collectors.joining(", ", "(", ")"));
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        List<EvaluationResult> reParams = parameters.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        EvaluationResult reObject = object.reEvaluate(evaluationContext, translation);
        List<Value> reParamValues = reParams.stream().map(er -> er.value).collect(Collectors.toList());
        Location location = getObjectFlow().location;
        EvaluationResult mv = MethodCall.methodValue(evaluationContext, location, methodInfo, methodInfo.methodAnalysis.get(), reObject.value, reParamValues, getObjectFlow());
        return new EvaluationResult.Builder(evaluationContext).compose(reParams).compose(reObject, mv).setValue(mv.value).build();
    }

    @Override
    public int order() {
        return ORDER_METHOD;
    }

    @Override
    public int internalCompareTo(Value v) {
        MethodValue mv = (MethodValue) v;
        int c = methodInfo.fullyQualifiedName().compareTo(mv.methodInfo.fullyQualifiedName());
        if (c != 0) return c;
        int i = 0;
        while (i < parameters.size()) {
            if (i >= mv.parameters.size()) return 1;
            c = parameters.get(i).compareTo(mv.parameters.get(i));
            if (c != 0) return c;
            i++;
        }
        return object.compareTo(mv.object);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        boolean recursiveCall = methodInfo == evaluationContext.getCurrentMethod().methodInfo;
        if (recursiveCall) {
            return variableProperty.best;
        }
        if (variableProperty == VariableProperty.SIZE) {
            return checkSize(evaluationContext, methodInfo, parameters);
        }
        if (variableProperty == VariableProperty.SIZE_COPY) {
            return checkSizeCopy(evaluationContext, methodInfo);
        }
        if (variableProperty == VariableProperty.NOT_NULL) {
            int fluent = evaluationContext.getMethodAnalysis(methodInfo).getProperty(VariableProperty.FLUENT);
            if (fluent == Level.TRUE) return Level.best(MultiLevel.EFFECTIVELY_NOT_NULL,
                    evaluationContext.getTypeAnalysis(methodInfo.typeInfo).getProperty(VariableProperty.NOT_NULL));
        }
        return evaluationContext.getMethodAnalysis(methodInfo).getProperty(variableProperty);
    }

    public static int checkSize(EvaluationContext evaluationContext, MethodInfo methodInfo, List<Value> parameters) {
        if (methodInfo == null) return Level.DELAY;
        // the method either belongs to a type that has size, or it returns a type that has size
        if (!methodInfo.returnType().hasSize(evaluationContext.getPrimitives(), evaluationContext.getAnalyserContext()) &&
                !methodInfo.typeInfo.hasSize(evaluationContext.getPrimitives(), evaluationContext.getAnalyserContext()))
            return Level.DELAY;

        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            int sizeCopy = evaluationContext.getParameterAnalysis(parameterInfo).getProperty(VariableProperty.SIZE_COPY);
            if (sizeCopy == Level.SIZE_COPY_MIN_TRUE || sizeCopy == Level.SIZE_COPY_TRUE) {
                // copyMin == True
                // copyEquals == True
                Value value = parameters.get(parameterInfo.index);
                int sizeOfValue = evaluationContext.getProperty(value, VariableProperty.SIZE);
                if (Level.haveEquals(sizeOfValue) && sizeCopy == 1) return sizeOfValue - 1;
                return sizeOfValue;
            }
        }
        return evaluationContext.getMethodAnalysis(methodInfo).getProperty(VariableProperty.SIZE);
    }

    public static int checkSizeCopy(EvaluationContext evaluationContext, MethodInfo methodInfo) {
        if (methodInfo == null) return Level.DELAY;
        // the method either belongs to a type that has size, or it returns a type that has size
        if (!methodInfo.returnType().hasSize(evaluationContext.getPrimitives(), evaluationContext.getAnalyserContext()) &&
                !methodInfo.typeInfo.hasSize(evaluationContext.getPrimitives(), evaluationContext.getAnalyserContext()))
            return Level.DELAY;

        // we give priority to the value of the parameters, rather than that of the method
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            int sizeCopy = evaluationContext.getParameterAnalysis(parameterInfo).getProperty(VariableProperty.SIZE_COPY);
            if (sizeCopy == Level.SIZE_COPY_MIN_TRUE || sizeCopy == Level.SIZE_COPY_TRUE) {
                return sizeCopy;
            }
        }
        return evaluationContext.getMethodAnalysis(methodInfo).getProperty(VariableProperty.SIZE_COPY);
    }


    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    /* We're in the situation of a = b.method(c, d), and we are computing the variables that `a` will be linked
     * to. There is no need to consider linking between `b`, `c` and `d` here because that linking takes place in the method's
     * definition itself. We consider 4 cases:
     *
     * 1. a is primitive, or e2immutable: independent
     * 2. method is @Independent: independent (the very definition)
     * 3. method is @Identity: only the first parameter
     * 4. b is @E2Immutable: only dependent on c, d
     *
     * Note that a dependence on a parameter is only possible when it is not primitive or @E2Immutable (see VariableValue).
     * On top of that comes the situation where the analyser has more detailed information than is in the annotations.
     * For now, we decide to ignore such information.
     *
     */

    private static final Set<Variable> NOT_LINKED = Set.of();

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {

        // RULE 0: void method cannot link
        ParameterizedType returnType = methodInfo.returnType();
        if (Primitives.isVoid(returnType)) return NOT_LINKED; // no assignment

        MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);

        // RULE 1: if the return type is E2IMMU, then no links at all
        boolean notSelf = returnType.typeInfo != evaluationContext.getCurrentType().typeInfo;
        if (notSelf) {
            int immutable = MultiLevel.value(methodAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
            if (immutable == MultiLevel.DELAY) return null;
            if (immutable >= MultiLevel.EVENTUAL) {
                return NOT_LINKED;
            }
        }

        // RULE 2: E2IMMU parameters cannot link: implemented recursively by rule 1 applied to the parameter!

        Set<Variable> result = new HashSet<>();
        for (Value p : parameters) {
            // the parameter value is not E2IMMU
            Set<Variable> cd = evaluationContext.linkedVariables(p);
            if (cd == null) return null;
            result.addAll(cd);
        }

        // RULE 3: E2IMMU object cannot link
        // RULE 4: independent method: no link to object

        int independent = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
        int objectE2Immutable = MultiLevel.value(evaluationContext.getProperty(object, VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (independent == Level.DELAY || objectE2Immutable == MultiLevel.DELAY) return null;
        boolean objectOfSameType = methodInfo.typeInfo == evaluationContext.getCurrentType().typeInfo;
        if (objectOfSameType || (objectE2Immutable < MultiLevel.EVENTUAL_AFTER && independent == MultiLevel.FALSE)) {
            Set<Variable> b = evaluationContext.linkedVariables(object);
            if (b == null) return null;
            result.addAll(b);
        }

        return result;
    }

    @Override
    public Set<Variable> variables() {
        return object.variables();
    }

    @Override
    public ParameterizedType type() {
        return methodInfo.returnType();
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        object.visit(consumer);
        parameters.forEach(v -> v.visit(consumer));
        consumer.accept(this);
    }
}
