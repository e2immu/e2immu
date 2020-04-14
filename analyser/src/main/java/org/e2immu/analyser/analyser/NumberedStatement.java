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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NullNotAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@E2Immutable(after = "") // TODO
public class NumberedStatement implements Comparable<NumberedStatement> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NumberedStatement.class);

    public final Statement statement;
    public SetOnce<Optional<NumberedStatement>> next = new SetOnce<>();
    public SetOnce<List<NumberedStatement>> blocks = new SetOnce<>();
    public SetOnce<Boolean> neverContinues = new SetOnce<>(); // returns, or escapes; set at the beginning of a block
    public SetOnce<Boolean> escapes = new SetOnce<>(); // escapes, on the beginning of a block
    public SetOnce<Boolean> returnsNotNull = new SetOnce<>(); // if returns, whether not null or not

    public SetOnce<Set<Variable>> linkedVariables = new SetOnce<>();

    public final int[] indices;
    public final SideEffect sideEffect;

    public NumberedStatement(SideEffectContext sideEffectContext,
                             @NullNotAllowed Statement statement,
                             @NotModified @NullNotAllowed int[] indices) {
        this.indices = Objects.requireNonNull(indices);
        this.statement = Objects.requireNonNull(statement);
        sideEffect = statement.sideEffect(sideEffectContext);
    }

    public String streamIndices() {
        return Arrays.stream(indices).mapToObj(Integer::toString).collect(Collectors.joining("."));
    }

    public String toString() {
        return streamIndices() + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(@NullNotAllowed NumberedStatement o) {
        for (int i = 0; i < indices.length; i++) {
            if (i >= o.indices.length)
                return 1;
            int c = indices[i] - o.indices[i];
            if (c != 0)
                return c;
        }
        if (o.indices.length > indices.length) return -1;
        return 0;
    }
}
