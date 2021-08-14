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
import org.e2immu.annotation.*;

import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public abstract class ParameterAnalyser extends AbstractAnalyser {
    public final ParameterInfo parameterInfo;
    public final ParameterAnalysisImpl.Builder parameterAnalysis;

    ParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super("Parameter " + parameterInfo.fullyQualifiedName(), analyserContext);
        this.parameterInfo = parameterInfo;
        parameterAnalysis = new ParameterAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, parameterInfo);
    }

    public ParameterAnalysis getParameterAnalysis() {
        return parameterAnalysis;
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return parameterInfo;
    }

    @Override
    public Analysis getAnalysis() {
        return parameterAnalysis;
    }

    @Override
    public void initialize() {
        throw new UnsupportedOperationException("Use different initializer");
    }

    public abstract void initialize(Stream<FieldAnalyser> fieldAnalyserStream);

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        return analyse(iteration);
    }

    protected abstract AnalysisStatus analyse(int iteration);

    public void check() {
        log(ANALYSER, "Checking parameter {}", parameterInfo.fullyQualifiedName());
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        check(NotModified.class, e2.notModified);
        check(NotNull.class, e2.notNull);
        check(NotNull1.class, e2.notNull1);
        check(NotNull2.class, e2.notNull2);
        check(NotModified1.class, e2.notModified1);
        check(PropagateModification.class, e2.propagateModification);
        check(Dependent1.class, e2.dependent1);
        check(Dependent2.class, e2.dependent2);

        check(BeforeMark.class, e2.beforeMark);
        check(E1Immutable.class, e2.e1Immutable);
        check(E1Container.class, e2.e1Container);
        check(E2Immutable.class, e2.e2Immutable);
        check(E2Container.class, e2.e2Container);

        // opposites
        check(Nullable.class, e2.nullable);
        check(Modified.class, e2.modified);

        checkWorseThanParent();
    }

    @Override
    public void write() {
        parameterAnalysis.transferPropertiesToAnnotations(analyserContext, analyserContext.getE2ImmuAnnotationExpressions());
    }

    private static final Set<VariableProperty> CHECK_WORSE_THAN_PARENT = Set.of(NOT_NULL_PARAMETER, MODIFIED_VARIABLE,
            NOT_MODIFIED_1, PROPAGATE_MODIFICATION, IMMUTABLE);

    private void checkWorseThanParent() {
        for (VariableProperty variableProperty : CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = analyserContext.getMethodAnalysis(parameterInfo.owner).getOverrides(analyserContext)
                    .stream()
                    .map(ma -> ma.getMethodInfo().methodInspection.get().getParameters().get(parameterInfo.index))
                    .mapToInt(pi -> analyserContext.getParameterAnalysis(pi).getParameterProperty(analyserContext,
                            pi, variableProperty))
                    .max().orElse(Level.DELAY);
            int value = parameterAnalysis.getProperty(variableProperty);
            if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
                boolean complain = variableProperty == VariableProperty.MODIFIED_VARIABLE
                        ? value > valueFromOverrides : value < valueFromOverrides;
                if (complain) {
                    messages.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                            variableProperty.name + ", parameter " + parameterInfo.name));
                }
            }
        }
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        parameterInfo.error(parameterAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

}
