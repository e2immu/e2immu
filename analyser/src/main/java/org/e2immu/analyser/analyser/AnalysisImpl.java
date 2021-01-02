/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            throw new UnsupportedOperationException("Cannot find annotation " + annotationExpression.output() + " in analysis");
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
