/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CompanionMethodName(String methodName, Action action,
                                  String aspect) implements Comparable<CompanionMethodName> {


    @Override
    public int compareTo(CompanionMethodName o) {
        return action.order - o.action.order;
    }

    public enum Action {

        /**
         * Used to define an aspect of the type. At the moment, aspects are numeric summaries, such as size or length.
         * They allow for simple computations to help verify common mistakes.
         *
         * The companion method should be void, as in 'void size$Aspect$Size() {}', companion to 'Collection.size()'.
         * Must be added to a @NotModified method.
         */
        ASPECT(0, true, true, "Aspect", 0, 0),

        /**
         * Clears the instance state. If accompanied by an aspect, the value is added to the instance state after clearing.
         * Example: `static boolean clear$Clear$Size(int i) { return i == 0; }` on `Collection.clear()` removes any
         * clauses present, and adds to the instance state that `this.size()==0`.
         *
         * Must be on a @Modified method.
         */
        CLEAR(1, false, true, "Clear", 1, 1),

        /**
         * An invariant is a boolean expression which is true at all times, e.g., this.size() >= 0.
         * This knowledge is used, at the moment, to convert size()!=0 into size()>0 in preconditions.
         *
         * Must sit next to the aspect definition, on a @NotModified method.
         */
        INVARIANT(2, false, true, "Invariant", 1, 1),

        /**
         * A precondition is a clause that must be true before the method starts; otherwise an exception is thrown.
         * For now, only implemented as a system without aspects, to verify that computed preconditions are correct.
         *
         * NOT YET IMPLEMENTED: preconditions on aspects.
         */
        PRECONDITION(3, false, true, "Precondition", 1, 1), // pre-mod

        /**
         * Compute the return value of a primitive type, potentially making use of an aspect.
         * As `int compareTo$Value(T t, int retVal) {  return equals(t) || t.equals(this) ? 0 : retVal; }` shows, the normal return value
         * is the last parameter called `retVal`.
         *
         * NOTE: this one does not add aspect information to the state!
         */
        VALUE(4, false, true, "Value", 1, 1), // current (non-modifying)

        // return value of the SAME type of object, change in aspect
        TRANSFER(5, true, true, "Transfer", 2, 1), // post if modifying

        // change to aspect of a modifying method
        // also use for constructors, but then without the "pre" (only post)
        MODIFICATION(6, true, true, "Modification", 2, 1), //pre, post (modifying)

        // opposite to post-condition (to remove an existing contains('a') before adding !contains('a'), for example)
        REMOVE(7, false, false, "Remove", 2, 2),

        // clauses that can be added independent of the aspect after a modification (contains('a') after add('a'))
        // clauses that can be added about the aspect of the return value of a non-modifying method,
        POSTCONDITION(8, false, false, "Postcondition", 2, 2); // pre, post (modifying),

        public final int order;
        public final boolean requiresAspect;
        public final boolean allowsAspect;
        public final String action;
        public final Pattern pattern;
        public final int aspectVariablesModifying;
        public final int aspectVariablesNonModifying;

        Action(int order, boolean requiresAspect, boolean allowsAspect, String action, int aspectVariablesModifying, int aspectVariablesNonModifying) {
            this.requiresAspect = requiresAspect;
            this.allowsAspect = allowsAspect;
            this.action = action;
            this.aspectVariablesModifying = aspectVariablesModifying;
            this.aspectVariablesNonModifying = aspectVariablesNonModifying;
            this.pattern = composePattern();
            this.order = order;
        }

        private Pattern composePattern() {
            String p = "([^$]+)\\$" + action;
            if (allowsAspect) {
                p += "(\\$([^\\$]+))" + (requiresAspect ? "" : "?");
            }
            return Pattern.compile(p);
        }
    }

    public static final EnumSet<Action> MODIFYING_METHOD_OR_CONSTRUCTOR = EnumSet.of(Action.CLEAR,
            Action.MODIFICATION, Action.REMOVE, Action.POSTCONDITION);
    public static final EnumSet<Action> NO_CODE = EnumSet.of(Action.ASPECT);

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


    public String composeMethodName() {
        return methodName + "$" + action.action + (aspect == null ? "" : "$" + aspect);
    }

    @Override
    public String toString() {
        return "CompanionMethodName{" +
                "methodName='" + methodName + '\'' +
                ", action=" + action +
                ", aspect='" + aspect + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompanionMethodName that = (CompanionMethodName) o;
        return action == that.action &&
                Objects.equals(aspect, that.aspect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, aspect);
    }
}
