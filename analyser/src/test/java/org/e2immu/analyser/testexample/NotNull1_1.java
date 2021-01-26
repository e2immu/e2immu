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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.function.Function;

public class NotNull1_1 {

    @NotNull1
    Function<String, String> toQuotedLowerCase = s -> "'" + s.toLowerCase() + "'";

    @NotNull
    public String method(@NotNull String input) {
        return toQuotedLowerCase.apply(input);
    }

    public String causesError() {
        return toQuotedLowerCase.apply(null);
    }
}
