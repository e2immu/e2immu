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


import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@E2Container
public interface Statement {

    String statementString(int indent);

    Set<String> imports();

    SideEffect sideEffect(SideEffectContext sideEffectContext);

    default CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder().build();
    }

    static <E extends Expression> Stream<E> findExpressionRecursivelyInStatements(Statement statement, Class<E> clazz) {
        return statement.codeOrganization().findExpressionRecursivelyInStatements(clazz);
    }

    Set<TypeInfo> typesReferenced();

     Statement translate(Map<? extends Variable,? extends Variable> translationMap);
}
