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

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Messages;

import java.util.function.Function;

public class CheckIndependent {

    public static void checkLevel(Messages messages,
                                  WithInspectionAndAnalysis info,
                                  Class<?> annotation,
                                  AnnotationExpression annotationExpression,
                                  AbstractAnalysisBuilder analysis) {
        checkLevel(messages, VariableProperty.INDEPENDENT, info, annotation,
                annotationExpression, analysis);
    }

    static void checkLevel(Messages messages,
                           VariableProperty variableProperty,
                           WithInspectionAndAnalysis info,
                           Class<?> annotation,
                           AnnotationExpression annotationExpression,
                           AbstractAnalysisBuilder analysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("level", null);
        int value = analysis.getProperty(variableProperty);
        int level = MultiLevel.level(value) ;
        String levelString = level <= MultiLevel.LEVEL_2_IMMUTABLE || level == MultiLevel.MAX_LEVEL
                ? null : Integer.toString(level+1);

        CheckLinks.checkAnnotationWithValue(messages,
                analysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                levelString,
                info.getInspection().getAnnotations(),
                new Location(info));
    }
}
