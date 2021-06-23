package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.util.StringUtil;

public interface VariableNature {

    static VariableNature normal(Variable variable, String index) {
        if (variable instanceof LocalVariableReference) {
            return new NormalLocalVariable(index);
        }
        return NORMAL;
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

    default String assignmentId() {
        return null;
    }

    default boolean acceptForSubBlockMerging(String index) {
        return true;
    }

    default boolean acceptVariableForMerging(String index) {
        return true;
    }

    class Marker implements VariableNature {
    }

    /*
    situation 1: normal variable (default value, rather than null)
    local variable gets VIC value (analysis only) for merging
     */
    Marker NORMAL = new Marker();

    class NormalLocalVariable implements VariableNature {
        public final String parentBlockIndex;

        // do not move up beyond block of definition!
        public NormalLocalVariable(String statementIndex) {
            int dot = statementIndex.lastIndexOf('.');
            if (dot == -1) parentBlockIndex = "";
            else {
                int dot2 = statementIndex.substring(0, dot).lastIndexOf('.');
                this.parentBlockIndex = statementIndex.substring(0, dot2);
            }
        }

        @Override
        public boolean acceptForSubBlockMerging(String index) {
            return !index.equals(parentBlockIndex);
        }
    }

    /*
    situation 2: A dependent variable was created inside a block, without a clear reference to a single variable;
    now it still needs to exist after that block (see Enum_1 as example)
    this copy is created during the merge phase at the end of the block.
    */
    Marker CREATED_IN_MERGE = new Marker();

    /*
    situation 3: a local pattern variable, often a local copy

    localCopyOf can be null, if the instanceof expression is not a variable, as in

    if(someMethodCall() instanceof Y y) { ... }

    isNegative is true when the instanceof pattern expression occurs in a negative expression, as in

    if(!(x instanceof Y y)) { ... here y does NOT exist } else { ... here, y exists! }

    The assignmentId will be the statement ID + "-E".
     */
    record Pattern(String assignmentId, boolean isPositive, Variable localCopyOf) implements VariableNature {
        @Override
        public boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
            return !StringUtil.inScopeOf(assignmentId, index);
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

        public String suffix() {
            if (assignmentId != null) {
                return "$" + statementTime + "$" + assignmentId;
            }
            return "$" + statementTime;
        }
    }

    /*
    situation 5: local copy of variable defined outside loop, potentially assigned inside the loop
    assignmentId null means: not assigned in the loop

    statement index is the one of the loop!
    assignment ID when there has been an assignment inside the loop
     */
    record CopyOfVariableInLoop(String statementIndex,
                                String assignmentId,
                                Variable localCopyOf) implements VariableNature {
        public CopyOfVariableInLoop {
            assert statementIndex != null;
            assert localCopyOf != null;
            assert assignmentId == null || assignmentId.startsWith(statementIndex);
        }

        public String suffix() {
            String first = "$" + statementIndex;
            if (assignmentId != null) {
                return first + "$" + assignmentId;
            }
            return first;
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
    Marker FROM_ENCLOSING_METHOD = new Marker();

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

    copy for conditional assignments of fields
    Meant for variable, static fields (ConditionalInitialization tests).
    Only travels "upwards" to the end of the method (never down into blocks)
     */
    record ConditionalInitialization(String statementIndex, FieldInfo source) implements VariableNature {
        @Override
        public boolean doNotCopyToNextStatement(boolean previousIsParent, String indexOfPrevious, String index) {
            return previousIsParent;
        }

        @Override
        public boolean acceptForSubBlockMerging(String index) {
            return !index.equals(statementIndex);
        }
    }

    /*
    situation 10

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

