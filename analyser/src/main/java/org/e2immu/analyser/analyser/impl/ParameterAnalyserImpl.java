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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.ParameterAnalyser;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.check.CheckIndependent;
import org.e2immu.analyser.analyser.check.CheckNotNull;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;

public abstract class ParameterAnalyserImpl extends AbstractAnalyser implements ParameterAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterAnalyserImpl.class);

    public final ParameterInfo parameterInfo;
    public final ParameterAnalysisImpl.Builder parameterAnalysis;

    protected ParameterAnalyserImpl(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
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

    public void check() {
        if (isUnreachable()) return;

        LOGGER.debug("Checking parameter {}", parameterInfo.fullyQualifiedName());
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        check(e2.notModified);
        check(e2.modified);

        analyserResultBuilder.add(CheckNotNull.check(parameterInfo, e2.notNull,
                parameterAnalysis, NOT_NULL_PARAMETER));
        check(e2.nullable);

        analyserResultBuilder.add(CheckIndependent.check(parameterInfo, e2.independent, parameterAnalysis));
        check(e2.beforeMark);

        check(e2.container);
        check(e2.immutableContainer);
        check(e2.immutable);

        checkWorseThanParent();
    }

    @Override
    public void write() {
        parameterAnalysis.transferPropertiesToAnnotations(analyserContext, analyserContext.getE2ImmuAnnotationExpressions());
    }

    private static final Set<Property> CHECK_WORSE_THAN_PARENT = Set.of(NOT_NULL_PARAMETER, MODIFIED_VARIABLE,
            CONTAINER_RESTRICTION, INDEPENDENT, IMMUTABLE);

    private void checkWorseThanParent() {
        for (Property property : CHECK_WORSE_THAN_PARENT) {
            DV valueFromOverrides = computeValueFromOverrides(property, true);
            DV value = parameterAnalysis.getProperty(property);
            if (valueFromOverrides.isDone() && value.isDone()) {
                boolean complain;
                if (property == Property.MODIFIED_VARIABLE) {
                    complain = value.gt(valueFromOverrides);
                } else {
                    complain = value.lt(valueFromOverrides);
                }
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

    protected DV computeValueFromOverrides(Property property, boolean filter) {
        return analyserContext.getMethodAnalysis(parameterInfo.owner).getOverrides(analyserContext, true)
                .stream()
                .map(ma -> ma.getMethodInfo().methodInspection.get().getParameters().get(parameterInfo.index))
                .filter(pi -> !filter || !(property == INDEPENDENT || property == IMMUTABLE) || !pi.parameterizedType.isUnboundTypeParameter())
                .map(pi -> analyserContext.getParameterAnalysis(pi).getParameterProperty(analyserContext,
                        pi, property))
                .reduce(DelayFactory.initialDelay(), DV::max);
    }

    private void check(AnnotationExpression annotationKey) {
        parameterInfo.error(parameterAnalysis, annotationKey).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(parameterInfo.newLocation(),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotationKey.typeInfo().simpleName);
            analyserResultBuilder.add(error);
        });
    }

}
