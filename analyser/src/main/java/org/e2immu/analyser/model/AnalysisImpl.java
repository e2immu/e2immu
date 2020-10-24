/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.util.IncrementalMap;

import java.util.*;
import java.util.stream.Stream;


public abstract class AnalysisImpl implements IAnalysis {

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);
    public final boolean hasBeenDefined;
    private final Map<AnnotationExpression, Boolean> annotations;

    protected AnalysisImpl(boolean hasBeenDefined, Map<AnnotationExpression, Boolean> annotations) {
        this.hasBeenDefined = hasBeenDefined;
        this.annotations = annotations;
    }

    @Override
    public boolean isHasBeenDefined() {
        return hasBeenDefined;
    }

    @Override
    public Boolean getAnnotation(AnnotationExpression annotationExpression) {
        return annotations.get(annotationExpression);
    }

    @Override
    public Stream<Map.Entry<AnnotationExpression, Boolean>> getAnnotationStream() {
        return annotations.entrySet().stream();
    }

    public int internalGetProperty(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, hasBeenDefined ? Level.DELAY : variableProperty.valueWhenAbsent(annotationMode()));
    }

    @Override
    public int getPropertyAsIs(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, Level.DELAY);
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
