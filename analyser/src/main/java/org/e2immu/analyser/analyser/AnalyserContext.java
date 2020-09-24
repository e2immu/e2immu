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

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.pattern.PatternMatcher;

import java.util.Map;

public interface AnalyserContext {
    Configuration getConfiguration();

    E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions();

    PatternMatcher getPatternMatcher();

    TypeInfo getPrimaryType();

    Map<MethodInfo, MethodAnalyser> getMethodAnalysers();

    Map<FieldInfo, FieldAnalyser> getFieldAnalysers();

    Map<TypeInfo, TypeAnalyser> getTypeAnalysers();

    Map<ParameterInfo, ParameterAnalyser> getParameterAnalysers();

    TypeAnalysis getPrimaryTypeAnalysis();

}
