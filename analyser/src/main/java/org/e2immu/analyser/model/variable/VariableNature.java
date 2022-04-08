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

import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.util.StringUtil;

public interface VariableNature {

    static VariableNature normal(Variable variable, String index) {
        if (variable instanceof LocalVariableReference) {
            return new NormalLocalVariable(index);
        }
        return METHOD_WIDE;
    }

    // WHO? variables created in a block eval (TRY, INSTANCE-OF, LOOP)
    // where: copy to next statement
    default boolean doNotCopyToNextStatement(String indexOfPrevious, String index) {
        return false;
    }

    // WHO? all variables created in the block eval (TRY, INSTANCE-OF, LOOP)
    // used for warning about unused variables
    default String definedInBlock() {
        return null;
    }

    // WHO? all variables created in the block eval (TRY, INSTANCE-OF, LOOP), or inside the block (NORMAL)
    // or VAR-IN-LOOP-DEF-OUTSIDE
    // used to prevent propagation + currently in isReplaceVariable
    default String getStatementIndexOfBlockVariable() {
        return null;
    }

    // WHO? block eval (TRY, INSTANCE-OF, LOOP), NORMAL in block, but not VAR-IN-LOOP-DEF-OUTSIDE
    // used in merge action
    default boolean removeInSubBlockMerge(String index) {
        return false;
    }

    // WHO? block eval (TRY, INSTANCE-OF, LOOP) -- NORMAL in block and VAR-IN-LOOP-DEF-OUTSIDE must
    // have been accessed in the sub-block, by definition.
    // used in merge action
    default boolean removeInMerge(String index) {
        return false;
    }

    NormalLocalVariable METHOD_WIDE = new NormalLocalVariable();

    default VariableExpression.Suffix suffix() {
        return VariableExpression.NO_SUFFIX;
    }

    /**
     * situation 1: normal variable (default value, rather than null)
     * local variable gets VIC value (analysis only) for merging
     */
    class NormalLocalVariable implements VariableNature {
        public final String parentBlockIndex;

        // do not move up beyond block of definition!
        private NormalLocalVariable() {
            parentBlockIndex = "";
        }

        public NormalLocalVariable(String statementIndex) {
            parentBlockIndex = computeParentBlockIndex(statementIndex);
        }

        @Override
        public String getStatementIndexOfBlockVariable() {
            return parentBlockIndex;
        }

        @Override
        public boolean removeInSubBlockMerge(String index) {
            return VariableNature.computeRemoveInSubBlockMerge(parentBlockIndex, index);
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
    Variables that live in the whole method, like This or ReturnVariable, are never removed.
    Variables defined in a block, e.g. 1.0.0, are removed in merges for 1.0.0, 1, 2.0, but not 1.0.0.0.1
     */
    private static boolean computeRemoveInSubBlockMerge(String definingBlock, String index) {
        if (definingBlock.isEmpty()) return false;
        return !index.startsWith(definingBlock + ".");
    }

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
        public String definedInBlock() {
            return parentBlockIndex;
        }

        @Override
        public boolean doNotCopyToNextStatement(String indexOfPrevious, String index) {
            return !StringUtil.inScopeOf(scope, index);
        }

        @Override
        public boolean removeInSubBlockMerge(String index) {
            return VariableNature.computeRemoveInSubBlockMerge(parentBlockIndex, index);
        }

        @Override
        public boolean removeInMerge(String index) {
            return index.equals(parentBlockIndex);
        }

        @Override
        public String getStatementIndexOfBlockVariable() {
            return parentBlockIndex;
        }
    }

    /*
    situation 6: Loop variable, like 'i' in (for int i=0; ...) or 'x' in for(X x: xs) { ... }.
    Only thing we need to store is the statement id of the loop.

    Only stored in the VIC, because the LocalVariable has been created before we know statement IDs.
     */
    record LoopVariable(String statementIndex) implements VariableNature {

        @Override
        public boolean doNotCopyToNextStatement(String indexOfPrevious, String index) {
            return indexOfPrevious != null && (indexOfPrevious.equals(statementIndex));
        }

        @Override
        public boolean removeInSubBlockMerge(String index) {
            return VariableNature.computeRemoveInSubBlockMerge(statementIndex, index);
        }

        @Override
        public boolean removeInMerge(String index) {
            return index.equals(statementIndex);
        }

        @Override
        public String getStatementIndexOfBlockVariable() {
            return statementIndex;
        }

        @Override
        public String definedInBlock() {
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
        public String getStatementIndexOfBlockVariable() {
            return statementIndex;
        }

        @Override
        public VariableExpression.Suffix suffix() {
            return new VariableExpression.VariableInLoop(statementIndex());
        }

        /*
         normally, we'd say "false" here: the variable is defined outside, so we don't remove it.
         However, if the statement index is finer than the index, we look for a previous one with the index,
         as shown in Loops_21, where we exit successive loops and there is no chance for the variable to switch variable
         nature to its previous value.
         */
        @Override
        public boolean removeInSubBlockMerge(String index) {
            VariableNature vn = this;
            while (vn instanceof VariableDefinedOutsideLoop vdol && vdol.statementIndex.startsWith(index + ".")) {
                vn = vdol.previousVariableNature;
            }
            if (vn != null && vn != this) {
                return vn.removeInSubBlockMerge(index);
            }
            return false;
        }

        public boolean isOutside(String latestAssignmentIndex) {
            return latestAssignmentIndex.compareTo(statementIndex) < 0;
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
        public boolean doNotCopyToNextStatement(String indexOfPrevious, String index) {
            return indexOfPrevious != null && (indexOfPrevious.equals(statementIndex))
                    || !index.startsWith(statementIndex + ".0.");
        }

        @Override
        public boolean removeInSubBlockMerge(String index) {
            return VariableNature.computeRemoveInSubBlockMerge(statementIndex, index);
        }

        @Override
        public boolean removeInMerge(String index) {
            return index.equals(statementIndex);
        }

        @Override
        public String getStatementIndexOfBlockVariable() {
            return statementIndex;
        }

        @Override
        public String definedInBlock() {
            return statementIndex;
        }
    }

    class DelayedScope implements VariableNature {
        private int iteration;

        public int getIteration() {
            return iteration;
        }

        public void setIteration(int iteration) {
            assert this.iteration <= iteration;
            this.iteration = iteration;
        }
    }


    record ScopeVariable(String indexCreatedInMerge) implements VariableNature {

        public ScopeVariable() {
            this(null);
        }

        public boolean descendInto(String index) {
            return indexCreatedInMerge == null || index.compareTo(indexCreatedInMerge) >= 0;
        }
    }
}

