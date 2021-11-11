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

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.AnnotationExpression;

import java.util.Map;
import java.util.stream.Stream;


public abstract class AnalysisImpl implements Analysis {

    public final Map<VariableProperty, DV> properties;
    private final Map<AnnotationExpression, AnnotationCheck> annotations;

    protected AnalysisImpl(Map<VariableProperty, DV> properties, Map<AnnotationExpression, AnnotationCheck> annotations) {
        this.annotations = annotations;
        this.properties = properties;
    }

    @Override
    public AnnotationCheck getAnnotation(AnnotationExpression annotationExpression) {
        AnnotationCheck annotationCheck = annotations.get(annotationExpression);
        if (annotationCheck == null) {
            return AnnotationCheck.NO_INFORMATION;
        }
        return annotationCheck;
    }

    @Override
    public Stream<Map.Entry<AnnotationExpression, AnnotationCheck>> getAnnotationStream() {
        return annotations.entrySet().stream();
    }

    public DV getPropertyFromMapNeverDelay(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, variableProperty.valueWhenAbsent());
    }

    @Override
    public DV getPropertyFromMapDelayWhenAbsent(VariableProperty variableProperty) {
        DV v = properties.getOrDefault(variableProperty, null);
        if (v == null) return new DV.SingleDelay(location(), variableProperty.causeOfDelay());
        return v;
    }
}
