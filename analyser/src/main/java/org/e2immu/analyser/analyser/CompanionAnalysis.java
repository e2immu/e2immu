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

import org.e2immu.analyser.model.Value;
import org.e2immu.annotation.AnnotationType;

import java.util.List;

public interface CompanionAnalysis {

    AnnotationType getAnnotationType();

    /**
     * @return the value that represents the companion.
     */
    Value getValue();

    /**
     * The variable value referring to the "pre" aspect variable.
     * This value is part of the getValue() value.
     * We provide it to facilitate re-evaluation.
     *
     * @return NO_VALUE when there is none
     */
    Value getPreAspectVariableValue();

    /**
     * The values of the parameters, part of the getValue() value.
     * We provide them to facilitate re-evaluation.
     *
     * @return a list of parameters, never null.
     */
    List<Value> getParameterValues();
}
