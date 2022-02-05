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
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CheckImmutable {

    public static Message check(WithInspectionAndAnalysis info,
                                Class<?> annotation,
                                AnnotationExpression annotationExpression,
                                Analysis analysis,
                                boolean level,
                                boolean recursive) {
        List<CheckLinks.AnnotationKV> kvs = new ArrayList<>(3);
        Property property = info instanceof FieldInfo ? Property.EXTERNAL_IMMUTABLE : Property.IMMUTABLE;

        Function<AnnotationExpression, String> extractInspected1 = ae -> ae.extract("after", "");
        String value1 = analysis.markLabelFromType();
        kvs.add(new CheckLinks.AnnotationKV(extractInspected1, value1));

        if (recursive) {
            Function<AnnotationExpression, String> extractInspected3 = ae -> {
                Boolean b = ae.extract("recursive", null);
                return b != null && b ? "true" : null;
            };
            String value3 = recursive(property, analysis);
            kvs.add(new CheckLinks.AnnotationKV(extractInspected3, value3));
        }
        if (level) {
            Function<AnnotationExpression, String> extractInspected2 = ae -> {
                Integer i = ae.extract("level", null);
                return i == null ? null : Integer.toString(i);
            };
            String value2 = CheckIndependent.levelString(analysis, property);
            kvs.add(new CheckLinks.AnnotationKV(extractInspected2, value2));
        }

        return CheckLinks.checkAnnotationWithValue(
                analysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                kvs,
                info.getInspection().getAnnotations(),
                info.newLocation());
    }

    private static String recursive(Property property, Analysis analysis) {
        DV immutable = analysis.getProperty(property);
        if (MultiLevel.level(immutable) == MultiLevel.MAX_LEVEL) return "true";
        return null;
    }
}
