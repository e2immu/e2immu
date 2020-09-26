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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.NavigationData;

import java.util.List;
import java.util.function.BiFunction;

public interface HasNavigationData<T extends HasNavigationData<T>> {
    NavigationData<T> getNavigationData();

    T followReplacements();

    T lastStatement();

    String index();

    Statement statement();

    StatementAnalysis parent();

    void wireNext(T newStatement);

    BiFunction<List<Statement>, String, T> generator(EvaluationContext evaluationContext);
}
