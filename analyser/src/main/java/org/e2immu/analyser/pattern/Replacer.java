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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Statement;
import org.e2immu.annotation.UtilityClass;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@UtilityClass
public class Replacer {

    public static void replace(EvaluationContext evaluationContext, MatchResult matchResult, Replacement replacement) {
        if (!matchResult.pattern.equals(replacement.pattern)) throw new UnsupportedOperationException("Double check");

        log(TRANSFORM, "Start pasting replacement {} onto {} after matching pattern {}", replacement.name,
                matchResult.start.streamIndices(), replacement.pattern.name);
        if (replacement.statements.isEmpty()) return;
        List<NumberedStatement> replacementNsAtStartLevel = startReplacing(evaluationContext, replacement, replacement.statements);
        wireStart(replacementNsAtStartLevel.get(0), matchResult.start);
        if (matchResult.start.next.get().isPresent()) {
            wireEnd(replacementNsAtStartLevel.get(replacementNsAtStartLevel.size() - 1), matchResult.start.next.get().get());
        }
    }

    private static void wireEnd(NumberedStatement lastReplacement, NumberedStatement nextOriginal) {
        lastReplacement.next.set(Optional.of(nextOriginal));
    }

    private static void wireStart(NumberedStatement firstReplacement, NumberedStatement start) {
        start.replacement.set(firstReplacement);
    }

    private static List<NumberedStatement> startReplacing(EvaluationContext evaluationContext,
                                                          Replacement replacement,
                                                          List<Statement> statements) {
        return statements.stream().map(statement -> replace(evaluationContext, replacement, statement)).collect(Collectors.toList());
    }

    private static NumberedStatement replace(EvaluationContext evaluationContext, Replacement replacement, Statement statement) {
        throw new UnsupportedOperationException();
    }

}
