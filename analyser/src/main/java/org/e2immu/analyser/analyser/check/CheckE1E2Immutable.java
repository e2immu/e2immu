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

import org.e2immu.analyser.analyser.TypeAnalysisImpl;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Messages;

import java.util.function.Function;

public class CheckE1E2Immutable {

    /*
    creation of the @E1Container, @E1Immutable, @E2Container, @E2Immutable annotations is in AbstractAnalysisBuilder
     */
    public static void check(Messages messages,
                             TypeInfo typeInfo,
                             Class<?> annotation,
                             AnnotationExpression annotationExpression,
                             TypeAnalysisImpl.Builder typeAnalysis) {

        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("after", null);
        String mark = typeAnalysis.isEventual() ? typeAnalysis.markLabel() : null;

        CheckLinks.checkAnnotationWithValue(messages,
                typeAnalysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                mark,
                typeInfo.typeInspection.get().getAnnotations(),
                new Location(typeInfo));
    }
}
