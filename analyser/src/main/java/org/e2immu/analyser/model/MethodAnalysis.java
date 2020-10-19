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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.ContractMark;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.AnnotationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MethodAnalysis extends Analysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalysis.class);

    public final ParameterizedType returnType;
    public final MethodInfo methodInfo;
    public final TypeAnalysis typeAnalysis;
    public final SetOnce<Set<MethodAnalysis>> overrides = new SetOnce<>();

    public final StatementAnalysis firstStatement;
    public final List<ParameterAnalysis> parameterAnalyses;

    // the value here (size will be one)
    public final SetOnce<List<Value>> preconditionForMarkAndOnly = new SetOnce<>();
    public final SetOnce<MarkAndOnly> markAndOnly = new SetOnce<>();

    // ************* object flow

    public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }

    public final SetOnce<Boolean> complainedAboutMissingStaticModifier = new SetOnce<>();
    public final SetOnce<Boolean> complainedAboutApprovedPreconditions = new SetOnce<>();


    // replacements

    // set when all replacements have been done
    public final SetOnce<StatementAnalysis> lastStatement = new SetOnce<>();

    // ************** PRECONDITION

    public final SetOnce<Value> precondition = new SetOnce<>();


    public MethodAnalysis(MethodInfo methodInfo, TypeAnalysis typeAnalysis, List<ParameterAnalysis> parameterAnalyses, StatementAnalyser firstStatementAnalyser) {
        super(methodInfo.hasBeenDefined(), methodInfo.name);
        this.parameterAnalyses = parameterAnalyses;
        this.methodInfo = methodInfo;
        this.returnType = methodInfo.returnType();
        this.typeAnalysis = typeAnalysis;
        if (methodInfo.isConstructor || methodInfo.isVoid()) {
            // we set a NO_FLOW, non-modifiable
            objectFlow = new FirstThen<>(ObjectFlow.NO_FLOW);
            objectFlow.set(ObjectFlow.NO_FLOW);
        } else {
            ObjectFlow initialObjectFlow = new ObjectFlow(new Location(methodInfo), returnType, Origin.INITIAL_METHOD_FLOW);
            objectFlow = new FirstThen<>(initialObjectFlow);
        }
        firstStatement = firstStatementAnalyser == null ? null : firstStatementAnalyser.statementAnalysis;
    }

    public MethodLevelData methodLevelData() {
        return lastStatement.isSet() ? lastStatement.get().methodLevelData : firstStatement.lastStatement().methodLevelData;
    }

    @Override
    protected Location location() {
        return new Location(methodInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return typeAnalysis.annotationMode();
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case MODIFIED:
                // all methods in java.lang.String are @NotModified, but we do not bother writing that down
                // we explicitly check on EFFECTIVE, because in an eventually E2IMMU we want the methods to remain @Modified
                if (!methodInfo.isConstructor &&
                        typeAnalysis.getProperty(VariableProperty.IMMUTABLE) == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return Level.FALSE;
                }
                return getPropertyCheckOverrides(VariableProperty.MODIFIED);

            case FLUENT:
            case IDENTITY:
            case SIZE:
                return getPropertyCheckOverrides(variableProperty);

            case INDEPENDENT:
                // TODO if we have an array constructor created on-the-fly, it should be EFFECTIVELY INDEPENDENT
                return getPropertyCheckOverrides(variableProperty);

            case NOT_NULL:
                if (returnType.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return MultiLevel.EFFECTIVELY_NOT_NULL;
                return getPropertyCheckOverrides(VariableProperty.NOT_NULL);

            case IMMUTABLE:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR || returnType.isVoid())
                    throw new UnsupportedOperationException(); //we should not even be asking

                int immutableType = formalProperty();
                int immutableDynamic = dynamicProperty(immutableType);
                return MultiLevel.bestImmutable(immutableType, immutableDynamic);

            case CONTAINER:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR || returnType.isVoid())
                    throw new UnsupportedOperationException(); //we should not even be asking
                int container = returnType.getProperty(VariableProperty.CONTAINER);
                if (container == Level.DELAY) return Level.DELAY;
                return Level.best(getPropertyCheckOverrides(VariableProperty.CONTAINER), container);

            default:
        }
        return super.getProperty(variableProperty);
    }

    private int dynamicProperty(int formalImmutableProperty) {
        int immutableTypeAfterEventual = MultiLevel.eventual(formalImmutableProperty, getObjectFlow().conditionsMetForEventual(returnType));
        return Level.best(super.getProperty(VariableProperty.IMMUTABLE), immutableTypeAfterEventual);
    }

    private int formalProperty() {
        return returnType.getProperty(VariableProperty.IMMUTABLE);
    }

    public int valueFromOverrides(VariableProperty variableProperty) {
        return getOverrides().stream().mapToInt(ma -> ma.getPropertyAsIs(variableProperty)).max().orElse(Level.DELAY);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(super.getPropertyAsIs(variableProperty));
        IntStream theStream;
        if (hasBeenDefined) {
            theStream = mine;
        } else {
            IntStream overrideValues = getOverrides().stream().mapToInt(ma -> ma.getPropertyAsIs(variableProperty));
            theStream = IntStream.concat(mine, overrideValues);
        }
        int max = theStream.max().orElse(Level.DELAY);
        if (max == Level.DELAY && !hasBeenDefined) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        int modified = getProperty(VariableProperty.MODIFIED);

        // @Precondition
        if (precondition.isSet()) {
            Value value = precondition.get();
            if (value != UnknownValue.EMPTY) {
                AnnotationExpression ae = e2ImmuAnnotationExpressions.precondition.get()
                        .copyWith("value", value.toString());
                annotations.put(ae, true);
            }
        }

        // @Dependent @Independent
        if (methodInfo.isConstructor || modified == Level.FALSE && allowIndependentOnMethod()) {
            int independent = getProperty(VariableProperty.INDEPENDENT);
            doIndependent(e2ImmuAnnotationExpressions, independent, methodInfo.typeInfo.isInterface());
        }

        if (methodInfo.isConstructor) return;

        // @NotModified, @Modified
        AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified.get() :
                e2ImmuAnnotationExpressions.modified.get();
        annotations.put(ae, true);

        if (returnType.isVoid()) return;

        // @Identity
        if (getProperty(VariableProperty.IDENTITY) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.identity.get(), true);
        }

        // all other annotations cannot be added to primitives
        if (returnType.isPrimitive()) return;

        // @Fluent
        if (getProperty(VariableProperty.FLUENT) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.fluent.get(), true);
        }

        // @NotNull
        doNotNull(e2ImmuAnnotationExpressions);

        // @Size
        doSize(e2ImmuAnnotationExpressions);

        // dynamic type annotations for functional interface types: @NotModified1
        doNotModified1(e2ImmuAnnotationExpressions);

        // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
        int formallyImmutable = formalProperty();
        int dynamicallyImmutable = dynamicProperty(formallyImmutable);
        if (MultiLevel.isBetterImmutable(dynamicallyImmutable, formallyImmutable)) {
            doImmutableContainer(e2ImmuAnnotationExpressions, dynamicallyImmutable, true);
        }
    }

    private boolean allowIndependentOnMethod() {
        return !returnType.isVoid() && returnType.isImplicitlyOrAtLeastEventuallyE2Immutable(typeAnalysis) != Boolean.TRUE;
    }

    protected void writeMarkAndOnly(MarkAndOnly markAndOnly) {
        ContractMark contractMark = new ContractMark(markAndOnly.markLabel);
        preconditionForMarkAndOnly.set(List.of(contractMark));
        this.markAndOnly.set(markAndOnly);
    }

    // the name refers to the @Mark and @Only annotations. It is the data for this annotation.

    public static class MarkAndOnly {
        public final List<Value> preconditions;
        public final String markLabel;
        public final boolean mark;
        public final Boolean after; // null for a @Mark without @Only

        public MarkAndOnly(List<Value> preconditions, String markLabel, boolean mark, Boolean after) {
            this.preconditions = preconditions;
            this.mark = mark;
            this.markLabel = markLabel;
            this.after = after;
        }

        @Override
        public String toString() {
            return markLabel + "=" + preconditions + "; after? " + after + "; @Mark? " + mark;
        }
    }

    public Set<MethodAnalysis> getOverrides() {
        if (overrides.isSet()) return overrides.get();
        Set<MethodAnalysis> computed = overrides(methodInfo);
        overrides.set(ImmutableSet.copyOf(computed));
        return computed;
    }

    private static Set<MethodAnalysis> overrides(MethodInfo methodInfo) {
        try {
            return methodInfo.typeInfo.overrides(methodInfo, true).stream().map(mi -> mi.methodAnalysis.get()).collect(Collectors.toSet());
        } catch (RuntimeException rte) {
            LOGGER.error("Cannot compute method analysis of {}", methodInfo.distinguishingName());
            throw rte;
        }
    }
}
