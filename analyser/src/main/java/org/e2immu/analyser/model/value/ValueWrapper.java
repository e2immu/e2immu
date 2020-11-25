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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Value;

public interface ValueWrapper {

    // there can never be two wrappers of the same class next to each other (meaning, on exactly the same Value object)

    int WRAPPER_ORDER_PROPERTY = 1;
    int WRAPPER_ORDER_NEGATED = 2;
    int WRAPPER_ORDER_CONSTRAINED_NUMERIC_VALUE = 3;

    Value getValue();

    int wrapperOrder();

}
