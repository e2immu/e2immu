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

        // define
        ASPECT(true, true, "Aspect", 0, 0),

        // a clause that is valid at all times, e.g. size() >= 0
        INVARIANT(false, true, "Invariant", 1, 1),

        // a clause that must be true before the method starts; otherwise an exception is thrown
        PRECONDITION(false, true, "Precondition", 1, 1), // pre-mod

        // return value of a primitive type, aspect or not
        VALUE(false, true, "Value", 1, 1), // current (non-modifying)

        // return value of the SAME type of object, change in aspect
        TRANSFER(true, true, "Transfer", 2, 1), // post if modifying

        // change to aspect of a modifying method
        // also use for constructors, but then without the "pre" (only post)
        MODIFICATION(true, true, "Modification", 2, 1), //pre, post (modifying)

        // clauses that can be added independent of the aspect after a modification (contains('a') after add('a'))
        // clauses that can be added about the aspect of the return value of a non-modifying method
        POSTCONDITION(false, true, "Postcondition", 2, 2), // pre, post (modifying),
        ;

        public final boolean requiresAspect;
        public final boolean allowsAspect;
        public final String action;
        public final Pattern pattern;
        public final int aspectVariablesModifying;
        public final int aspectVariablesNonModifying;

        Action(boolean requiresAspect, boolean allowsAspect, String action, int aspectVariablesModifying, int aspectVariablesNonModifying) {
            this.requiresAspect = requiresAspect;
            this.allowsAspect = allowsAspect;
            this.action = action;
            this.aspectVariablesModifying = aspectVariablesModifying;
            this.aspectVariablesNonModifying = aspectVariablesNonModifying;
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

    public int numAspectVariables(boolean modifyingMethod) {
        return aspect == null ? 0 : (modifyingMethod ? action.aspectVariablesModifying : action.aspectVariablesNonModifying);
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
