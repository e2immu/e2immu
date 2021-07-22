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
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.stream.Stream;

public class ShallowFieldAnalyser {

    private final InspectionProvider inspectionProvider;
    private final Messages messages = new Messages();
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    public ShallowFieldAnalyser(InspectionProvider inspectionProvider, E2ImmuAnnotationExpressions e2) {
        this.inspectionProvider = inspectionProvider;
        e2ImmuAnnotationExpressions = e2;
    }

    public void analyser(FieldInfo fieldInfo, boolean typeIsEnum) {
        FieldAnalysisImpl.Builder fieldAnalysisBuilder = new FieldAnalysisImpl.Builder(inspectionProvider.getPrimitives(),
                AnalysisProvider.DEFAULT_PROVIDER,
                fieldInfo, fieldInfo.owner.typeAnalysis.get());

        messages.addAll(fieldAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.FIELD, true,
                fieldInfo.fieldInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));

        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        boolean enumField = typeIsEnum && fieldInspection.isSynthetic();

        // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
        if (fieldInfo.isExplicitlyFinal() || enumField) {
            fieldAnalysisBuilder.setProperty(VariableProperty.FINAL, Level.TRUE);
        }
        // unless annotated with something heavier, ...
        if (enumField && fieldAnalysisBuilder.getProperty(VariableProperty.EXTERNAL_NOT_NULL) == Level.DELAY) {
            fieldAnalysisBuilder.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        }

        if (fieldAnalysisBuilder.getProperty(VariableProperty.FINAL) == Level.TRUE && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
            Expression initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser();
            Expression value;
            if (initialiser instanceof ConstantExpression<?> constantExpression) {
                value = constantExpression;
            } else {
                value = EmptyExpression.EMPTY_EXPRESSION; // IMPROVE
            }
            fieldAnalysisBuilder.effectivelyFinalValue.set(value);
        }
        fieldInfo.setAnalysis(fieldAnalysisBuilder.build());
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
