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

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Final;
import org.e2immu.annotation.NotModified;

import java.util.function.Function;

public class CheckFinalNotModified {

    /*
    creation of the @Final(after = ) annotations is in AbstractAnalysisBuilder
     */
    public static void check(Messages messages,
                             FieldInfo fieldInfo,
                             Class<?> annotation,
                             AnnotationExpression annotationExpression,
                             FieldAnalysis fieldAnalysis,
                             TypeAnalysis typeAnalysis) {

        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("after", null);
        boolean isModifiedOrVariable;
        if (Final.class.equals(annotation)) {
            isModifiedOrVariable = fieldAnalysis.getProperty(Property.FINAL).valueIsFalse();
        } else if (NotModified.class.equals(annotation)) {
            isModifiedOrVariable = fieldAnalysis.getProperty(Property.MODIFIED_VARIABLE).valueIsFalse();
        } else throw new UnsupportedOperationException();

        String mark = typeAnalysis.isEventual() && isModifiedOrVariable ? typeAnalysis.markLabel() : null;

        CheckLinks.checkAnnotationWithValue(messages,
                fieldAnalysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                mark,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }
}
