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

package org.e2immu.analyser.pattern;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.Statement;

import java.util.ArrayList;
import java.util.List;

public class MatchResult {

    public final List<Statement> statements;
    public final NumberedStatement start;

    private MatchResult(List<Statement> statements, NumberedStatement start) {
        this.statements = statements;
        this.start = start;
    }

    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        for (Statement statement : statements) {
            sb.append(statement.statementString(indent));
        }
        return sb.toString();
    }

    public static class MatchResultBuilder {
        private final List<Statement> statements = new ArrayList<>();
        private final NumberedStatement start;

        public MatchResultBuilder(NumberedStatement start) {
            this.start = start;
        }

        public MatchResult build() {
            return new MatchResult(ImmutableList.copyOf(statements), start);
        }
    }

    public void apply() {

    }
}
