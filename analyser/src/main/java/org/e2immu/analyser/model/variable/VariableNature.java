package org.e2immu.analyser.model.variable;

public interface VariableNature {

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

    class Marker implements VariableNature {
    }

    /*
    situation 1: normal variable (default value, rather than null)
     */
    Marker NORMAL = new Marker();

    /*
    situation 2: A dependent variable was created inside a block, without a clear reference to a single variable;
    now it still needs to exist after that block (see Enum_1 as example)
    this copy is created during the merge phase at the end of the block.
    */
    Marker LOCAL_VARIABLE_CREATED_IN_MERGE = new Marker();

    /*
    situation 3: a local pattern variable, often a local copy

    localCopyOf can be null, if the instanceof expression is not a variable, as in

    if(someMethodCall() instanceof Y y) { ... }

    isNegative is true when the instanceof pattern expression occurs in a negative expression, as in

    if(!(x instanceof Y y)) { ... here y does NOT exist } else { ... here, y exists! }

    The assignmentId will be the statement ID + "-E".
     */
    record Pattern(String assignmentId, boolean isNegative, Variable localCopyOf) implements VariableNature {
    }

    /*
    situation 4: local (read) copy of a variable field

    localCopyOf = field, statement time 0 -> name is field$0
    if the assignment id is not null, there has been an assignment inside the method, and the name becomes
      field$
    */
    record VariableFieldReadCopy(int statementTime,
                                 String assignmentId,
                                 Variable localCopyOf) implements VariableNature {
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
     */
    record CopyOfVariableInLoop(String statementIndexOfLoop,
                                String assignmentId,
                                Variable localCopyOf) implements VariableNature {
        public String suffix() {
            String first = "$" + statementIndexOfLoop;
            if (assignmentId != null) {
                return first + "$" + assignmentId;
            }
            return first;
        }

        @Override
        public String getStatementIndexOfThisLoopOrLoopCopyVariable() {
            return statementIndexOfLoop;
        }
    }

    /*
    situation 6: Loop variable, like 'i' in (for int i=0; ...) or 'x' in for(X x: xs) { ... }.
    Only thing we need to store is the statement id of the loop
     */
    record LoopVariable(String statementIndexOfLoop) implements VariableNature {
        @Override
        public boolean isLocalVariableInLoopDefinedOutside() {
            return true;
        }

        @Override
        public String getStatementIndexOfThisLoopOrLoopCopyVariable() {
            return statementIndexOfLoop;
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
                                      String statementIndexOfLoop) implements VariableNature {
        @Override
        public boolean isLocalVariableInLoopDefinedOutside() {
            return true;
        }
    }
}

