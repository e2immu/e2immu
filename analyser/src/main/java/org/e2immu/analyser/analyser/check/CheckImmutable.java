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
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CheckImmutable {

    public static Message check(WithInspectionAndAnalysis info,
                                Class<?> annotation,
                                AnnotationExpression annotationExpression,
                                Analysis analysis,
                                Expression constantValue) {
        List<CheckHelper.AnnotationKV> kvs = new ArrayList<>(3);
        Property property = info instanceof FieldInfo ? Property.EXTERNAL_IMMUTABLE : Property.IMMUTABLE;

        Function<AnnotationExpression, String> extractInspected1 = ae -> ae.extract("after", "");
        Map.Entry<AnnotationExpression, Boolean> inAnalysis = analysis.findAnnotation(annotation.getCanonicalName());
        String value1 = inAnalysis == null ? "" : inAnalysis.getKey().extract("after", "");
        // do not use the after=""... as a marker to check the presence (see test E2InContext_3)
        kvs.add(new CheckHelper.AnnotationKV(extractInspected1, value1, false));

        String computedValue = constantValue == null ? null : constantValue.toString();
        Function<AnnotationExpression, String> extractInspected = ae -> {
            String value = ae.extract("value", "");
            return constantValue instanceof StringConstant ? StringUtil.quote(value) : value;
        };
        if (constantValue != null) {
            kvs.add(new CheckHelper.AnnotationKV(extractInspected, computedValue));
        }

        return CheckHelper.checkAnnotationWithValue(
                analysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                kvs,
                info.getInspection().getAnnotations(),
                info.newLocation());
    }
}
