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

package org.e2immu.analyser.output;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.QualificationImpl;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.SetUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OutputMethodInfo {

    public static OutputBuilder output(MethodInfo methodInfo, Qualification qualification, AnalysisProvider analysisProvider) {
        MethodInspection inspection = methodInfo.methodInspection.get();
        if (inspection.isStaticBlock()) {
            OutputBuilder result = new OutputBuilder().add(new Text("static"));
            Qualification bodyQualification = makeBodyQualification(qualification, inspection);
            MethodAnalysis methodAnalysisOrNull = analysisProvider.getMethodAnalysis(methodInfo);
            StatementAnalysis firstStatement = methodAnalysisOrNull != null ? methodAnalysisOrNull.getFirstStatement() : null;
            result.add(inspection.getMethodBody().output(bodyQualification, firstStatement));
            return result;
        }

        OutputBuilder afterAnnotations = new OutputBuilder();
        afterAnnotations.add(Arrays.stream(MethodModifier.sort(inspection.getModifiers()))
                .map(mod -> new OutputBuilder().add(new Text(mod)))
                .collect(OutputBuilder.joining(Space.ONE)));
        if (!inspection.getModifiers().isEmpty()) afterAnnotations.add(Space.ONE);

        if (!inspection.getTypeParameters().isEmpty()) {
            afterAnnotations.add(Symbol.LEFT_ANGLE_BRACKET);
            afterAnnotations.add(inspection.getTypeParameters().stream()
                    .map(tp -> tp.output(InspectionProvider.DEFAULT, qualification, new HashSet<>()))
                    .collect(OutputBuilder.joining(Symbol.COMMA)));
            afterAnnotations.add(Symbol.RIGHT_ANGLE_BRACKET).add(Space.ONE);
        }

        if (!methodInfo.isConstructor) {
            afterAnnotations.add(inspection.getReturnType().output(qualification)).add(Space.ONE);
        }
        afterAnnotations.add(new Text(methodInfo.name));
        if (!inspection.isCompactConstructor()) {
            if (inspection.getParameters().isEmpty()) {
                afterAnnotations.add(Symbol.OPEN_CLOSE_PARENTHESIS);
            } else {
                afterAnnotations.add(inspection.getParameters().stream()
                        .map(pi -> pi.outputDeclaration(qualification))
                        .collect(OutputBuilder.joining(Symbol.COMMA, Symbol.LEFT_PARENTHESIS, Symbol.RIGHT_PARENTHESIS,
                                Guide.generatorForParameterDeclaration())));
            }
        }
        if (!inspection.getExceptionTypes().isEmpty()) {
            afterAnnotations.add(Space.ONE_REQUIRED_EASY_SPLIT).add(new Text("throws")).add(Space.ONE)
                    .add(inspection.getExceptionTypes().stream()
                            .map(pi -> pi.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)));
        }
        MethodAnalysis methodAnalysisOrNull = analysisProvider.getMethodAnalysis(methodInfo);
        if (inspection.isAbstract()) {
            afterAnnotations.add(Symbol.SEMICOLON);
        } else {
            Qualification bodyQualification = makeBodyQualification(qualification, inspection);
            StatementAnalysis firstStatement = methodAnalysisOrNull != null ? methodAnalysisOrNull.getFirstStatement() : null;
            afterAnnotations.add(inspection.getMethodBody().output(bodyQualification, firstStatement));
        }

        Stream<OutputBuilder> annotationStream = methodInfo.buildAnnotationOutput(qualification);
        OutputBuilder mainMethod = Stream.concat(annotationStream, Stream.of(afterAnnotations))
                .collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT, Guide.generatorForAnnotationList()));

        Stream<OutputBuilder> companions = outputCompanions(inspection, methodAnalysisOrNull, qualification);
        return Stream.concat(companions, Stream.of(mainMethod))
                .collect(OutputBuilder.joining(Space.NONE, Guide.generatorForCompanionList()));
    }

    private static Qualification makeBodyQualification(Qualification qualification, MethodInspection inspection) {
        Set<String> localNamesFromBody = inspection.getMethodBody().variables().stream()
                .filter(v -> v instanceof LocalVariableReference || v instanceof ParameterInfo)
                .map(Variable::simpleName).collect(Collectors.toSet());
        Set<String> parameterNames = inspection.getParameters().stream()
                .map(ParameterInfo::simpleName).collect(Collectors.toSet());
        Set<String> localNames = SetUtil.immutableUnion(localNamesFromBody, parameterNames);

        List<FieldInfo> visibleFields = inspection.getMethodInfo().typeInfo.visibleFields(InspectionProvider.DEFAULT);
        QualificationImpl res = new QualificationImpl(qualification);
        visibleFields.stream().filter(fieldInfo -> !localNames.contains(fieldInfo.name)).forEach(res::addField);


        return res;
    }

    private static Stream<OutputBuilder> outputCompanions(MethodInspection methodInspection,
                                                          MethodAnalysis methodAnalysis,
                                                          Qualification qualification) {
        Stream<OutputBuilder> inspected = methodInspection.getCompanionMethods().values().stream()
                .map(companion -> companion.output(qualification));
        Stream<OutputBuilder> analysed;
        if (methodAnalysis != null) {
            analysed = methodAnalysis.getComputedCompanions().values().stream()
                    .map(companion -> companion.output(qualification));
        } else {
            analysed = Stream.empty();
        }
        return Stream.concat(inspected, analysed);
    }
}
