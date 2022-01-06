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

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.util.StringUtil;

public interface VariableNature {

    static VariableNature normal(Variable variable, String index) {
        if (variable instanceof LocalVariableReference) {
            return new NormalLocalVariable(index);
        }
        return METHOD_WIDE;
    }

    default boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
        return false;
    }

    /*
    Is true starting from the eval expression of the loop statement, down to all statements in the block.
    Is true at all times for variables declared in the loop statement's init (for, forEach)
    */
    default boolean isLocalVariableInLoopDefinedOutside() {
        return false;
    }

    default String getStatementIndexOfThisLoopOrLoopCopyVariable() {
        return null;
    }

    default Variable localCopyOf() {
        return null;
    }

    default String suffix() {
        return "";
    }

    default boolean acceptForSubBlockMerging(String index) {
        return true;
    }

    default boolean acceptVariableForMerging(String index) {
        return true;
    }

    default boolean ignoreCurrent(String index) {
        return false;
    }

    NormalLocalVariable METHOD_WIDE = new NormalLocalVariable();

    /*
    situation 1: normal variable (default value, rather than null)
    local variable gets VIC value (analysis only) for merging
   */
    class NormalLocalVariable implements VariableNature {
        public final String parentBlockIndex;

        // do not move up beyond block of definition!
        public NormalLocalVariable() {
            parentBlockIndex = "";
        }

        public NormalLocalVariable(String statementIndex) {
            parentBlockIndex = computeParentBlockIndex(statementIndex);
        }

        @Override
        public boolean acceptForSubBlockMerging(String index) {
            return parentBlockIndex.isEmpty() || !index.equals(parentBlockIndex);
        }
    }

    private static String computeParentBlockIndex(String statementIndex) {
        assert !statementIndex.isEmpty();
        int dot = statementIndex.lastIndexOf('.');
        if (dot == -1) return "";

        int dot2 = statementIndex.substring(0, dot).lastIndexOf('.');
        return statementIndex.substring(0, dot2);

    }

    /*
    situation 2: A dependent variable was created inside a block, without a clear reference to a single variable;
    now it still needs to exist after that block (see Enum_1 as example)
    this copy is created during the merge phase at the end of the block.
    */
    NormalLocalVariable CREATED_IN_MERGE = new NormalLocalVariable();

    /*
    situation 3: a local pattern variable, often a local copy

    localCopyOf can be null, if the instanceof expression is not a variable, as in

    if(someMethodCall() instanceof Y y) { ... }

    isNegative is true when the instanceof pattern expression occurs in a negative expression, as in

    if(!(x instanceof Y y)) { ... here y does NOT exist } else { ... here, y exists! }

     */
    record Pattern(String scope, String parentBlockIndex, boolean isPositive,
                   Variable localCopyOf) implements VariableNature {
        public Pattern(String scope, boolean isPositive, Variable localCopyOf) {
            this(scope, VariableNature.computeParentBlockIndex(scope), isPositive, localCopyOf);
        }

        @Override
        public boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
            return !StringUtil.inScopeOf(scope, index);
        }

        @Override
        public boolean acceptForSubBlockMerging(String index) {
            return !index.equals(parentBlockIndex);
        }
    }

    /*
    situation 4: local (read) copy of a variable field

    localCopyOf = field, statement time 0 -> name is field$0
    if the assignment id is not null, there has been an assignment inside the method, and the name becomes
      field$
    */
    record CopyOfVariableField(int statementTime,
                               String assignmentId,
                               FieldReference localCopyOf) implements VariableNature {
        public CopyOfVariableField {
            assert localCopyOf != null;
        }

        @Override
        public boolean ignoreCurrent(String index) {
            return assignmentId != null && assignmentId.startsWith(index);
        }

        public String suffix() {
            if (assignmentId != null) {
                return "$" + statementTime + "$" + assignmentId;
            }
            return "$" + statementTime;
        }

        public boolean isWriteCopy() {
            return assignmentId != null;
        }
    }

    /*
    situation 5: local copy of variable defined outside loop, potentially assigned inside the loop
    assignmentId null means: not assigned in the loop

    statement index is the one of the loop!
    assignment ID when there has been an assignment inside the loop
     */
    record CopyOfVariableInLoop(String statementIndex,
                                Variable localCopyOf) implements VariableNature {
        public CopyOfVariableInLoop {
            assert localCopyOf != null;
        }

        public String suffix() {
            return "$" + statementIndex;
        }

        @Override
        public boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
            return indexOfPrevious != null && (indexOfPrevious.equals(statementIndex));
        }

        @Override
        public boolean acceptForSubBlockMerging(String index) {
            return !index.equals(statementIndex);
        }

        @Override
        public boolean acceptVariableForMerging(String index) {
            return !index.equals(statementIndex);
        }

        @Override
        public String getStatementIndexOfThisLoopOrLoopCopyVariable() {
            return statementIndex;
        }
    }

    /*
    situation 6: Loop variable, like 'i' in (for int i=0; ...) or 'x' in for(X x: xs) { ... }.
    Only thing we need to store is the statement id of the loop.

    Only stored in the VIC, because the LocalVariable has been created before we know statement IDs.
     */
    record LoopVariable(String statementIndex) implements VariableNature {
        @Override
        public boolean isLocalVariableInLoopDefinedOutside() {
            return true;
        }

        @Override
        public boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
            return indexOfPrevious != null && (indexOfPrevious.equals(statementIndex));
        }

        @Override
        public boolean acceptForSubBlockMerging(String index) {
            return !index.equals(statementIndex);
        }

        @Override
        public boolean acceptVariableForMerging(String index) {
            return !index.equals(statementIndex);
        }

        @Override
        public String getStatementIndexOfThisLoopOrLoopCopyVariable() {
            return statementIndex;
        }
    }

    /*
    situation 7
     */
    NormalLocalVariable FROM_ENCLOSING_METHOD = new NormalLocalVariable();

    /*
    situation 8
     */
    record VariableDefinedOutsideLoop(VariableNature previousVariableNature,
                                      String statementIndex) implements VariableNature {
        @Override
        public boolean isLocalVariableInLoopDefinedOutside() {
            return true;
        }
    }

    /*
    situation 9

    try resource variable
    Value in VIC overrides the default one in the local variable
    The variable only exists in statementIndex-Evaluation + sub-block 0
     */
    record TryResource(String statementIndex) implements VariableNature {
        @Override
        public boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
            return !index.startsWith(statementIndex + ".0.");
        }
    }
}

