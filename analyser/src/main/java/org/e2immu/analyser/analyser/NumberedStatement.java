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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.BreakOrContinueStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class NumberedStatement implements Comparable<NumberedStatement> {
    public final Statement statement;
    public final NumberedStatement parent;
    public SetOnce<Optional<NumberedStatement>> next = new SetOnce<>();
    public SetOnce<List<NumberedStatement>> blocks = new SetOnce<>();
    public SetOnce<Boolean> neverContinues = new SetOnce<>(); // returns, or escapes; set at the beginning of a block
    public SetOnce<Boolean> escapes = new SetOnce<>(); // escapes, on the beginning of a block
    public SetOnce<Boolean> errorValue = new SetOnce<>(); // if we detected an error value on this statement
    public SetOnce<Value> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0
    public SetOnce<Value> state = new SetOnce<>(); // the state as it is after evaluating the statement

    // a set of break and continue statements in sub-blocks of this statement
    public SetOnce<List<BreakOrContinueStatement>> breakAndContinueStatements = new SetOnce<>();
    public SetOnce<Set<Variable>> existingVariablesAssignedInLoop = new SetOnce<>();

    // used for patterns
    public SetOnce<Value> valueOfExpression = new SetOnce<>();

    public final int[] indices;
    public final SideEffect sideEffect;

    // Transformations
    // if the statement is not important anymore, set it to "ExpressionAsStatement" with "EmptyExpression"
    // the replacement should have the same indices
    public final SetOnce<NumberedStatement> replacement = new SetOnce<>();

    public NumberedStatement(@NotNull SideEffectContext sideEffectContext,
                             @NotNull Statement statement,
                             NumberedStatement parent,
                             @NotNull @NotModified int[] indices) {
        this.indices = Objects.requireNonNull(indices);
        this.statement = Objects.requireNonNull(statement);
        sideEffect = statement.sideEffect(sideEffectContext);
        this.parent = parent;
    }

    public String streamIndices() {
        return Arrays.stream(indices).mapToObj(Integer::toString).collect(Collectors.joining("."));
    }

    public String toString() {
        return streamIndices() + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(NumberedStatement o) {
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

    public boolean inErrorState() {
        boolean parentInErrorState = parent != null && parent.inErrorState();
        if (parentInErrorState) return true;
        return errorValue.isSet() && errorValue.get();
    }
}
