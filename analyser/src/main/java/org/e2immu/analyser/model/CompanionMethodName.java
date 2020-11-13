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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CompanionMethodName(String methodName, Action action, String aspect) {

    public enum Action {
        ASPECT(true, true, "Aspect", 0),
        MODIFICATION(true, true, "Modification", 2), //pre, post (modifying)
        VALUE(false, true, "Value", 1), // current (non-modifying)
        PRECONDITION(false, true, "Precondition", 1), // pre-mod
        POSTCONDITION(false, true, "Postcondition", 2), // pre, post (modifying)
        INVARIANT(false, true, "Invariant", 1),
        TRANSFER(true, true, "Transfer", 1), // post if modifying
        ERASE(false, true, "Erase", 1), // post
        GENERATE(false, true, "Generate", 1); // post

        public final boolean requiresAspect;
        public final boolean allowsAspect;
        public final String action;
        public final Pattern pattern;
        public final int aspectVariables;

        Action(boolean requiresAspect, boolean allowsAspect, String action, int aspectVariables) {
            this.requiresAspect = requiresAspect;
            this.allowsAspect = allowsAspect;
            this.action = action;
            this.aspectVariables = aspectVariables;
            this.pattern = composePattern();
        }

        private Pattern composePattern() {
            String p = "([^$]+)\\$" + action;
            if (allowsAspect) {
                p += "(\\$([^\\$]+))" + (requiresAspect ? "" : "?");
            }
            return Pattern.compile(p);
        }
    }

    public int numAspectVariables() {
        return aspect == null ? 0 : action.aspectVariables;
    }

    // generates a CompanionMethod based on a method's name, or null when the method's name
    // does not fit the pattern methodName$action$aspect

    public static CompanionMethodName extract(String methodName) {
        if (methodName.contains("$")) {
            for (Action action : Action.values()) {
                Matcher m = action.pattern.matcher(methodName);
                if (m.matches()) {
                    return new CompanionMethodName(m.group(1), action, action.allowsAspect ? m.group(3) : null);
                }
            }
            throw new UnsupportedOperationException("Method with $, but not recognized as companion? " + methodName);
        }
        return null;
    }
}
