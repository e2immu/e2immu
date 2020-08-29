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

import java.util.List;
import java.util.Optional;

/**
 * For now the patterns are tested one by one, but we will at some point put them in a TRIE structure,
 * based on the statement and expression types.
 * <p>
 * The result, in case of a match, is a MatchResult, which will contain sufficient information
 * to be applied to the
 */
public class PatternMatcher {
    private final List<Pattern> patterns;

    public PatternMatcher(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public Optional<MatchResult> match(NumberedStatement statement) {

        return Optional.empty();
    }
}
