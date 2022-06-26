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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Message;

import java.util.function.Function;

public class CheckIndependent {

    public static Message checkLevel(WithInspectionAndAnalysis info,
                              Class<?> annotation,
                              AnnotationExpression annotationExpression,
                              Analysis analysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("level", null);
        String levelString = levelString(analysis, Property.INDEPENDENT);

        return CheckLinks.checkAnnotationWithValue(analysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                levelString,
                info.getInspection().getAnnotations(),
                info.newLocation());
    }

    static String levelString(Analysis analysis, Property property) {
        DV value = analysis.getProperty(property);
        int level = MultiLevel.level(value);
        return level <= MultiLevel.Level.IMMUTABLE_2.level || level == MultiLevel.MAX_LEVEL
                ? null : Integer.toString(level + 1);
    }
}
