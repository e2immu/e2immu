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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.*;

import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.config.AnalyserProgram.Step.ALL;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public abstract class ParameterAnalyserImpl extends AbstractAnalyser implements ParameterAnalyser {
    public final ParameterInfo parameterInfo;
    public final ParameterAnalysisImpl.Builder parameterAnalysis;

    ParameterAnalyserImpl(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super("Parameter " + parameterInfo.fullyQualifiedName(), analyserContext);
        this.parameterInfo = parameterInfo;
        parameterAnalysis = new ParameterAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, parameterInfo);
    }

    @Override
    public ParameterInfo getParameterInfo() {
        return parameterInfo;
    }

    @Override
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

    @Override
    public AnalyserResult analyse(int iteration, EvaluationContext closure) {
        return analyse(iteration);
    }

    public void check() {
        AnalyserProgram analyserProgram = analyserContext.getAnalyserProgram();
        if (analyserProgram.accepts(ALL)) {
            log(ANALYSER, "Checking parameter {}", parameterInfo.fullyQualifiedName());
            E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
            check(NotModified.class, e2.notModified);
            check(Modified.class, e2.modified);

            check(NotNull.class, e2.notNull);
            check(NotNull1.class, e2.notNull1);
            check(Nullable.class, e2.nullable);

            check(Independent.class, e2.independent);
            check(Independent1.class, e2.independent1);
            check(Dependent.class, e2.dependent);

            check(BeforeMark.class, e2.beforeMark);
            check(E1Immutable.class, e2.e1Immutable);
            check(E1Container.class, e2.e1Container);
            check(E2Immutable.class, e2.e2Immutable);
            check(E2Container.class, e2.e2Container);

            check(Container.class, e2.container);
            checkWorseThanParent();
        }
    }

    @Override
    public void write() {
        parameterAnalysis.transferPropertiesToAnnotations(analyserContext, analyserContext.getE2ImmuAnnotationExpressions());
    }

    private static final Set<Property> CHECK_WORSE_THAN_PARENT = Set.of(NOT_NULL_PARAMETER, MODIFIED_VARIABLE,
            CONTAINER, INDEPENDENT, IMMUTABLE);

    private void checkWorseThanParent() {
        for (Property property : CHECK_WORSE_THAN_PARENT) {
            DV valueFromOverrides = computeValueFromOverrides(property);
            DV value = parameterAnalysis.getProperty(property);
            if (valueFromOverrides.isDone() && value.isDone()) {
                boolean complain = property == Property.MODIFIED_VARIABLE
                        ? value.gt(valueFromOverrides) : value.lt(valueFromOverrides);
                if (complain) {
                    String msg;
                    if (property == INDEPENDENT) {
                        msg = "Have " + value.label() + ", expect " + valueFromOverrides.label();
                    } else {
                        msg = property.name + ", parameter " + parameterInfo.name;
                    }
                    analyserResultBuilder.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                            msg));
                }
            }
        }
    }

    protected DV computeValueFromOverrides(Property property) {
        return analyserContext.getMethodAnalysis(parameterInfo.owner).getOverrides(analyserContext)
                .stream()
                .map(ma -> ma.getMethodInfo().methodInspection.get().getParameters().get(parameterInfo.index))
                .map(pi -> analyserContext.getParameterAnalysis(pi).getParameterProperty(analyserContext,
                        pi, property))
                .reduce(DV.MIN_INT_DV, DV::max);
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        parameterInfo.error(parameterAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(parameterInfo.newLocation(),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName());
            analyserResultBuilder.add(error);
        });
    }

}
