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


import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@E2Immutable
@NullNotAllowed
@NotNull
public interface Statement {

    String statementString(int indent);

    Set<String> imports();

    default List<Block> blocks() {
        return List.of();
    }

    default Optional<Expression> expression() {
        return Optional.empty();
    }

    default List<LocalVariableReference> newLocalVariables() {
        return List.of();
    }

    default <E extends Expression> List<E> findInExpression(Class<E> clazz) {
        return List.of();
    }

    SideEffect sideEffect(SideEffectContext sideEffectContext);
}
