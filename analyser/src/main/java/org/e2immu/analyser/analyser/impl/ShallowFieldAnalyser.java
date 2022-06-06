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
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.FieldInspection;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.Map;
import java.util.stream.Stream;

public class ShallowFieldAnalyser {

    private final InspectionProvider inspectionProvider;
    private final AnalysisProvider analysisProvider;
    private final Messages messages = new Messages();
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    public ShallowFieldAnalyser(InspectionProvider inspectionProvider,
                                AnalysisProvider analysisProvider,
                                E2ImmuAnnotationExpressions e2) {
        this.inspectionProvider = inspectionProvider;
        this.analysisProvider = analysisProvider;
        e2ImmuAnnotationExpressions = e2;
    }

    public void analyser(FieldInfo fieldInfo, boolean typeIsEnum) {
        FieldAnalysisImpl.Builder fieldAnalysisBuilder = new FieldAnalysisImpl.Builder(inspectionProvider.getPrimitives(),
                AnalysisProvider.DEFAULT_PROVIDER,
                fieldInfo, analysisProvider.getTypeAnalysis(fieldInfo.owner));

        messages.addAll(fieldAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.FIELD, true,
                fieldInfo.fieldInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));

        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        boolean enumField = typeIsEnum && fieldInspection.isSynthetic();

        if (!fieldAnalysisBuilder.properties.isDone(Property.FINAL)) {
            // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
            fieldAnalysisBuilder.setProperty(Property.FINAL, DV.fromBoolDv(fieldInfo.isExplicitlyFinal() || enumField));
        }

        // unless annotated with something heavier, ...
        DV notNull;
        if (!fieldAnalysisBuilder.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
            notNull = enumField ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : AnalysisProvider.defaultNotNull(fieldInfo.type);
            fieldAnalysisBuilder.setProperty(Property.EXTERNAL_NOT_NULL, notNull);
        } else {
            notNull = fieldAnalysisBuilder.getPropertyFromMapNeverDelay(Property.EXTERNAL_NOT_NULL);
        }

        DV typeIsContainer;
        if (!fieldAnalysisBuilder.properties.isDone(Property.CONTAINER)) {
            if (fieldAnalysisBuilder.bestType == null) {
                typeIsContainer = MultiLevel.CONTAINER_DV;
            } else {
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysisNullWhenAbsent(fieldAnalysisBuilder.bestType);
                if (typeAnalysis != null) {
                    typeIsContainer = typeAnalysis.getProperty(Property.CONTAINER);
                } else {
                    typeIsContainer = Property.CONTAINER.falseDv;
                    if (fieldInfo.isPublic()) {
                        messages.add(Message.newMessage(fieldInfo.newLocation(), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                                fieldAnalysisBuilder.bestType.fullyQualifiedName));
                    }
                }
            }
            fieldAnalysisBuilder.setProperty(Property.CONTAINER, typeIsContainer);
        } else {
            typeIsContainer = fieldAnalysisBuilder.properties.getOrDefaultNull(Property.CONTAINER);
        }

        DV annotatedImmutable = fieldAnalysisBuilder.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        DV formallyImmutable = analysisProvider.defaultImmutable(fieldInfo.type, false);
        DV immutable = MultiLevel.MUTABLE_DV.maxIgnoreDelay(annotatedImmutable.maxIgnoreDelay(formallyImmutable));
        DV annotatedIndependent = fieldAnalysisBuilder.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        DV formallyIndependent = analysisProvider.defaultIndependent(fieldInfo.type);
        DV independent = MultiLevel.DEPENDENT_DV.maxIgnoreDelay(annotatedIndependent.maxIgnoreDelay(formallyIndependent));
        DV ignoreMods = fieldAnalysisBuilder.getPropertyFromMapNeverDelay(Property.IGNORE_MODIFICATIONS);

        Properties properties = Properties.of(Map.of(Property.NOT_NULL_EXPRESSION, notNull,
                Property.IMMUTABLE, immutable, Property.INDEPENDENT, independent, Property.CONTAINER, typeIsContainer,
                Property.IGNORE_MODIFICATIONS, ignoreMods, Property.IDENTITY, DV.FALSE_DV));
        Expression value;
        if (fieldAnalysisBuilder.getProperty(Property.FINAL).valueIsTrue()
                && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
            Expression initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser().unwrapIfConstant();
            if (initialiser .isConstant()) {
                value = initialiser;
            } else {
                value = Instance.forField(fieldInfo, initialiser.returnType(), properties);
            }
        } else {
            value = Instance.forField(fieldInfo, null, properties);
        }
        fieldAnalysisBuilder.setValue(value);
        fieldInfo.setAnalysis(fieldAnalysisBuilder.build());
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
