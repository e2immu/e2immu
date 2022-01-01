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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.Analyser;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;


abstract class AnalysisImpl implements Analysis {

    public final Map<Property, DV> properties;
    private final Map<AnnotationExpression, AnnotationCheck> annotations;

    protected AnalysisImpl(Map<Property, DV> properties, Map<AnnotationExpression, AnnotationCheck> annotations) {
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

    public DV getPropertyFromMapNeverDelay(Property property) {
        return properties.getOrDefault(property, property.valueWhenAbsent());
    }

    @Override
    public DV getPropertyFromMapDelayWhenAbsent(Property property) {
        DV v = properties.getOrDefault(property, null);
        if (v == null) return new CausesOfDelay.SimpleSet(location(), property.causeOfDelay());
        return v;
    }

    @Override
    public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification,
                                                  boolean acceptVerifyAsContracted,
                                                  Collection<AnnotationExpression> annotations,
                                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException("Only in builders!");
    }
}
