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
import org.e2immu.analyser.model.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


public abstract class AnalysisImpl implements Analysis {

    public final Map<VariableProperty, Integer> properties;
    private final Map<AnnotationExpression, AnnotationCheck> annotations;

    protected AnalysisImpl(Map<VariableProperty, Integer> properties, Map<AnnotationExpression, AnnotationCheck> annotations) {
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

    public int internalGetProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, variableProperty.valueWhenAbsent(annotationMode()));
    }

    @Override
    public int getPropertyAsIs(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }


    public Map<VariableProperty, Integer> getProperties(Set<VariableProperty> properties) {
        Map<VariableProperty, Integer> res = new HashMap<>();
        for (VariableProperty property : properties) {
            int value = getProperty(property);
            res.put(property, value);
        }
        return res;
    }
}
