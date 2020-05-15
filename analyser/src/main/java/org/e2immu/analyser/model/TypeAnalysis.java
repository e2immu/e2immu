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
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


// ...
public class TypeAnalysis extends Analysis {

    public final boolean isPrimitiveObjectOrString;
    public static final int E2IMMUTABLE_TRUE = Level.compose(Level.TRUE, Level.E2IMMUTABLE);

    public TypeAnalysis(boolean isPrimitiveObjectOrString) {
        this.isPrimitiveObjectOrString = isPrimitiveObjectOrString;
    }

    // to avoid repetitions
    public final SetOnce<Boolean> startedPostAnalysisIntoNestedTypes = new SetOnce<>();

    public final SetOnce<Boolean> doNotAllowDelaysOnNotModified = new SetOnce<>();

    protected List<ElementType> extractWhere(AnnotationExpression annotationExpression) {
        ElementType[] elements = annotationExpression.extract("where", NOT_NULL_WHERE_ALL);
        return Arrays.stream(elements).collect(Collectors.toList());
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.IMMUTABLE) {
            if (isPrimitiveObjectOrString) return E2IMMUTABLE_TRUE;
        }
        return super.getProperty(variableProperty);
    }
}
