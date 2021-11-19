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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.check.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public abstract class MethodAnalyser extends AbstractAnalyser implements HoldsAnalysers {
    public final MethodInfo methodInfo;
    public final MethodInspection methodInspection;
    public final boolean isSAM;
    public final MethodAnalysisImpl.Builder methodAnalysis;
    public final List<? extends ParameterAnalyser> parameterAnalysers;
    public final List<ParameterAnalysis> parameterAnalyses;
    public final Map<CompanionMethodName, CompanionAnalyser> companionAnalysers;
    public final Map<CompanionMethodName, CompanionAnalysis> companionAnalyses;
    public final CheckConstant checkConstant;

    MethodAnalyser(MethodInfo methodInfo,
                   MethodAnalysisImpl.Builder methodAnalysis,
                   List<? extends ParameterAnalyser> parameterAnalysers,
                   List<ParameterAnalysis> parameterAnalyses,
                   Map<CompanionMethodName, CompanionAnalyser> companionAnalysers,
                   boolean isSAM,
                   AnalyserContext analyserContextInput) {
        super("Method " + methodInfo.name, analyserContextInput);
        this.checkConstant = new CheckConstant(analyserContext.getPrimitives(), analyserContext.getE2ImmuAnnotationExpressions());
        this.methodInfo = methodInfo;
        methodInspection = methodInfo.methodInspection.get();
        this.parameterAnalysers = parameterAnalysers;
        this.parameterAnalyses = parameterAnalyses;
        this.methodAnalysis = methodAnalysis;
        this.companionAnalysers = companionAnalysers;
        companionAnalyses = companionAnalysers.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> e.getValue().companionAnalysis));
        this.isSAM = isSAM;
    }

    public abstract Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers();

    public abstract Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies);

    public abstract StatementAnalyser findStatementAnalyser(String index);

    public abstract void logAnalysisStatuses();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodAnalyser that = (MethodAnalyser) o;
        return methodInfo.equals(that.methodInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo);
    }

    public Collection<? extends ParameterAnalyser> getParameterAnalysers() {
        return parameterAnalysers;
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return methodInfo;
    }

    @Override
    public void initialize() {
        boolean acceptVerify = methodAnalysis.analysisMode == Analysis.AnalysisMode.CONTRACTED;
        // copy CONTRACT annotations into the properties
        methodAnalysis.fromAnnotationsIntoProperties(AnalyserIdentification.METHOD, acceptVerify,
                methodInspection.getAnnotations(), analyserContext.getE2ImmuAnnotationExpressions());

        parameterAnalysers.forEach(pa -> {
            Collection<AnnotationExpression> annotations = pa.parameterInfo.getInspection().getAnnotations();
            pa.parameterAnalysis.fromAnnotationsIntoProperties(AnalyserIdentification.PARAMETER, acceptVerify,
                    annotations, analyserContext.getE2ImmuAnnotationExpressions());

            pa.initialize(analyserContext.fieldAnalyserStream());
        });
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis;
    }

    public boolean fromFieldToParametersIsDone() {
        return !hasCode() || getParameterAnalysers().stream().allMatch(parameterAnalyser ->
                parameterAnalyser.getParameterAnalysis().isAssignedToFieldDelaysResolved());
    }

    public boolean hasCode() {
        StatementAnalysis firstStatement = methodAnalysis.getFirstStatement();
        return firstStatement != null;
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        if (!methodInfo.isConstructor) {
            if (!methodInfo.isVoid()) {
                check(NotNull.class, e2.notNull);
                check(NotNull1.class, e2.notNull1);
                check(Fluent.class, e2.fluent);
                check(Identity.class, e2.identity);
                check(Container.class, e2.container);

                check(E1Immutable.class, e2.e1Immutable);
                check(E1Container.class, e2.e1Container);
                CheckImmutable.check(messages, methodInfo, E2Immutable.class, e2.e2Immutable, methodAnalysis, false, true, true);
                CheckImmutable.check(messages, methodInfo, E2Container.class, e2.e2Container, methodAnalysis, false, true, false);
                check(BeforeMark.class, e2.beforeMark);
                check(ERContainer.class, e2.eRContainer);

                checkConstant.checkConstantForMethods(messages, methodInfo, methodAnalysis);

                check(Nullable.class, e2.nullable);

                check(Dependent.class, e2.dependent);
                check(Independent.class, e2.independent);
                CheckIndependent.checkLevel(messages, methodInfo, Independent1.class, e2.independent1, methodAnalysis);
            }

            check(NotModified.class, e2.notModified);
            check(Modified.class, e2.modified);
        }

        CheckPrecondition.checkPrecondition(messages, methodInfo, methodAnalysis, companionAnalyses);

        CheckEventual.checkOnly(messages, methodInfo, methodAnalysis);
        CheckEventual.checkMark(messages, methodInfo, methodAnalysis);
        CheckEventual.checkTestMark(messages, methodInfo, methodAnalysis);

        getParameterAnalysers().forEach(ParameterAnalyser::check);
        getLocallyCreatedPrimaryTypeAnalysers().forEach(PrimaryTypeAnalyser::check);

        checkWorseThanOverriddenMethod();
    }

    private static final Set<Property> CHECK_WORSE_THAN_PARENT = Set.of(Property.NOT_NULL_EXPRESSION,
            Property.MODIFIED_METHOD, Property.CONTAINER, Property.IDENTITY, Property.FLUENT);

    private void checkWorseThanOverriddenMethod() {
        for (Property property : CHECK_WORSE_THAN_PARENT) {
            DV valueFromOverrides = methodAnalysis.valueFromOverrides(analyserContext, property);
            DV value = methodAnalysis.getProperty(property);
            if (valueFromOverrides.isDone() && value.isDone()) {
                boolean complain = property == Property.MODIFIED_METHOD ?
                        value.gt(valueFromOverrides) : value.lt(valueFromOverrides);
                if (complain) {
                    messages.add(Message.newMessage(new Location(methodInfo),
                            Message.Label.WORSE_THAN_OVERRIDDEN_METHOD, property.name));
                }
            }
        }
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(methodAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(methodInfo),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    @Override
    public void write() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        // before we check, we copy the properties into annotations
        methodAnalysis.transferPropertiesToAnnotations(analyserContext, e2);
        parameterAnalysers.forEach(ParameterAnalyser::write);
        getLocallyCreatedPrimaryTypeAnalysers().forEach(PrimaryTypeAnalyser::write);
    }

    public abstract List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean includeLocalCopies);

    public List<VariableInfo> getFieldAsVariableAssigned(FieldInfo fieldInfo) {
        return getFieldAsVariable(fieldInfo, false).stream().filter(VariableInfo::isAssigned)
                .toList();
    }

    public CausesOfDelay fromFieldToParametersStatus() {
        return parameterAnalysers.stream().filter(pa -> !pa.parameterAnalysis.isAssignedToFieldDelaysResolved())
                .map(pa -> (CausesOfDelay) new CausesOfDelay.SimpleSet(pa.parameterAnalysis.location, CauseOfDelay.Cause.ASSIGNED_TO_FIELD))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }
}
