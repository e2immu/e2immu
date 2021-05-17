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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.SMapList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShallowMethodAnalyser extends MethodAnalyser {

    public ShallowMethodAnalyser(MethodInfo methodInfo,
                                 MethodAnalysisImpl.Builder methodAnalysis,
                                 List<ParameterAnalysis> parameterAnalyses,
                                 AnalyserContext analyserContext) {
        super(methodInfo, methodAnalysis, List.of(), parameterAnalyses, Map.of(), false, analyserContext);
    }

    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        // no-op
    }

    @Override
    public void initialize() {
        // no-op
    }

    public void analyse() {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = collectAnnotations();
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        parameterAnalyses.forEach(parameterAnalysis -> {
            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
            messages.addAll(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    map.getOrDefault(builder.getParameterInfo(), Map.of()).keySet(), e2));
        });

        messages.addAll(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, map.getOrDefault(methodInfo, Map.of()).keySet(), e2));

        // IMPROVE reading preconditions from AAPI...
        methodAnalysis.precondition.set(Precondition.empty(analyserContext.getPrimitives()));
    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        // nothing here
        return AnalysisStatus.DONE;
    }

    private Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> collectAnnotations() {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = new HashMap<>();

        Map<AnnotationExpression, List<MethodInfo>> methodMap = new HashMap<>();
        map.put(methodInfo, methodMap);

        Stream.concat(Stream.of(methodInfo), methodInfo.methodResolution.get(methodInfo.fullyQualifiedName).overrides().stream()).forEach(mi -> {

            MethodInspection mii = mi.methodInspection.get();
            mii.getAnnotations().forEach(annotationExpression -> SMapList.add(methodMap, annotationExpression, mi));

            mii.getParameters().forEach(parameterInfo -> {
                Map<AnnotationExpression, List<MethodInfo>> parameterMap = map.computeIfAbsent(parameterInfo, k -> new HashMap<>());
                parameterInfo.parameterInspection.get().getAnnotations().forEach(annotationExpression ->
                        SMapList.add(parameterMap, annotationExpression, mi));
            });
        });

        map.forEach(this::checkContradictions);
        return map;
    }

    private void checkContradictions(WithInspectionAndAnalysis where,
                                     Map<AnnotationExpression,
                                             List<MethodInfo>> annotations) {
        if (annotations.size() < 2) return;
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        checkContradictions(where, annotations, e2.notModified, e2.modified);
        checkContradictions(where, annotations, e2.notNull, e2.nullable);
    }

    private void checkContradictions(WithInspectionAndAnalysis where,
                                     Map<AnnotationExpression, List<MethodInfo>> annotations,
                                     AnnotationExpression left,
                                     AnnotationExpression right) {
        List<MethodInfo> leftMethods = annotations.getOrDefault(left, List.of());
        List<MethodInfo> rightMethods = annotations.getOrDefault(right, List.of());
        if (!leftMethods.isEmpty() && !rightMethods.isEmpty()) {
            messages.add(Message.newMessage(new Location(where), Message.Label.CONTRADICTING_ANNOTATIONS,
                    left + " in " + leftMethods.stream()
                            .map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; ")) +
                            "; " + right + " in " + rightMethods.stream()
                            .map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; "))));
        }
    }

    @Override
    public void write() {
        // everything contracted, nothing to write
    }

    @Override
    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean b) {
        return List.of();
    }

    @Override
    public void check() {
        // everything contracted, nothing to check
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        return Stream.empty();
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logAnalysisStatuses() {
        // nothing here
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException("Shallow method analyser has no analyser components");
    }

    @Override
    public void makeImmutable() {
        // nothing here
    }
}
