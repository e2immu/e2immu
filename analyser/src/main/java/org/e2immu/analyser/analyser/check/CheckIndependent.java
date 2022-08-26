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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Message;

import java.util.function.Function;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.HIDDEN_CONTENT;

public class CheckIndependent {

    public static Message check(WithInspectionAndAnalysis info,
                              AnnotationExpression annotationKey,
                              Analysis analysis) {
        Function<AnnotationExpression, String> extract = ae -> ae.extract(HIDDEN_CONTENT, false).toString();
        boolean hiddenContent = MultiLevel.INDEPENDENT_HC_DV.equals(analysis.getProperty(Property.INDEPENDENT));
        return CheckHelper.checkAnnotationWithValue(analysis,
                annotationKey,
                HIDDEN_CONTENT,
                extract,
                hiddenContent ? "true" : "false",
                info.getInspection().getAnnotations(),
                info.newLocation());
    }
}
