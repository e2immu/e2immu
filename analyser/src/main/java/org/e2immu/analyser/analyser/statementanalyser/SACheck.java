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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.AssertStatement;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.*;

public record SACheck(StatementAnalysis statementAnalysis) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SACheck.class);

    private String index() {
        return statementAnalysis.index();
    }

    private Statement statement() {
        return statementAnalysis.statement();
    }

    private Location location() {
        return statementAnalysis.location();
    }

    private MethodInfo methodInfo() {
        return statementAnalysis.methodAnalysis().getMethodInfo();
    }

    /*
    Not-null escapes should not contribute to preconditions.
    All the rest should.
     */
    AnalysisStatus checkNotNullEscapesAndPreconditions(StatementAnalyserSharedState sharedState) {
        if (statementAnalysis.statement() instanceof AssertStatement) return DONE; // is dealt with in subBlocks
        DV escapeAlwaysExecuted = statementAnalysis.isEscapeAlwaysExecutedInCurrentBlock();
        CausesOfDelay delays = escapeAlwaysExecuted.causesOfDelay()
                .merge(statementAnalysis.stateData().conditionManagerForNextStatementStatus());
        if (!escapeAlwaysExecuted.valueIsFalse()) {
            if (escapeAlwaysExecuted.valueIsTrue()) {
                // escapeCondition should filter out all != null, == null clauses
                Expression precondition = statementAnalysis.stateData().getConditionManagerForNextStatement()
                        .precondition(sharedState.evaluationContext());
                CausesOfDelay preconditionIsDelayed = precondition.causesOfDelay().merge(delays);
                Expression translated = sharedState.evaluationContext().acceptAndTranslatePrecondition(precondition);
                if (translated != null) {
                    LOGGER.debug("Escape with precondition {}", translated);
                    Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
                    statementAnalysis.stateData().setPrecondition(pc, preconditionIsDelayed.isDelayed());
                    return AnalysisStatus.of(preconditionIsDelayed);
                }
            }

            if (delays.isDelayed()) return delays;
        }
        if (statementAnalysis.stateData().preconditionIsEmpty()) {
            // it could have been set from the assert statement (subBlocks) or apply via a method call
            statementAnalysis.stateData().setPreconditionAllowEquals(Precondition.empty(statementAnalysis.primitives()));
        } else if (!statementAnalysis.stateData().preconditionIsFinal()) {
            return statementAnalysis.stateData().getPrecondition().expression().causesOfDelay();
        }
        return DONE;
    }

    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li NYR + local variable created higher up + return: <code>int i=0; if(xxx) { i=3; return; }</code></li>
     *     <li>NYR + escape: <code>int i=0; if(xxx) { i=3; throw new UnsupportedOperationException(); }</code></li>
     * </ul>
     * Comes after unused local variable, we do not want 2 errors
     */
    AnalysisStatus checkUselessAssignments(NavigationData<StatementAnalyser> navigationData) {
        if (!statementAnalysis.flowData().interruptsFlowIsSet()) {
            LOGGER.debug("Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return statementAnalysis.flowData().interruptStatus().causesOfDelay();
        }
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData().bestAlwaysInterrupt();
        DV reached = statementAnalysis.flowData().getGuaranteedToBeReachedInMethod();
        if (reached.isDelayed()) return reached.causesOfDelay();

        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if ((atEndOfBlock || alwaysInterrupts) && methodInfo().isNotATestMethod()) {
            // important to be after this statement, because assignments need to be "earlier" in notReadAfterAssignment
            String indexEndOfBlock = StringUtil.beyond(index());
            statementAnalysis.rawVariableStream()
                    .filter(e -> e.getValue().variableNature() != VariableNature.FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof ReturnVariable)) // that's for the compiler!
                    .filter(this::uselessForDependentVariable)
                    .filter(vi -> vi.notReadAfterAssignment(indexEndOfBlock))
                    .forEach(variableInfo -> {
                        boolean isLocalAndLocalToThisBlock = statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name());
                        if (bestAlwaysInterrupt == InterruptsFlow.ESCAPE ||
                                isLocalAndLocalToThisBlock ||
                                variableInfo.variable().isLocal() && bestAlwaysInterrupt == InterruptsFlow.RETURN &&
                                        localVariableAssignmentInThisBlock(variableInfo)) {
                            Location location = location();
                            Message unusedLv = Message.newMessage(location,
                                    Message.Label.UNUSED_LOCAL_VARIABLE, variableInfo.name());
                            if (!statementAnalysis.containsMessage(unusedLv)) {
                                statementAnalysis.ensure(Message.newMessage(location,
                                        Message.Label.USELESS_ASSIGNMENT, variableInfo.name()));
                            }
                        }
                    });
        }
        return DONE;
    }

    private boolean uselessForDependentVariable(VariableInfo variableInfo) {
        if (variableInfo.variable() instanceof DependentVariable dv) {
            return dv.hasArrayVariable() && !variableHasBeenReadAfter(dv.arrayVariable(),
                    variableInfo.getAssignmentIds().getLatestAssignment());
        }
        return true;
    }

    private boolean variableHasBeenReadAfter(Variable variable, String assignment) {
        VariableInfo variableInfo = statementAnalysis.findOrThrow(variable);
        int c = variableInfo.getReadId().compareTo(assignment);
        return c > 0;
    }

    private boolean localVariableAssignmentInThisBlock(VariableInfo variableInfo) {
        assert variableInfo.variable().isLocal();
        if (!variableInfo.isAssigned()) return false;
        return StringUtil.inSameBlock(variableInfo.getAssignmentIds().getLatestAssignmentIndex(), index());
    }

    AnalysisStatus checkUnusedLocalVariables(NavigationData<StatementAnalyser> navigationData) {
        if (navigationData.next.get().isEmpty() && methodInfo().isNotATestMethod()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.rawVariableStream()
                    .filter(e -> !(e.getValue().variableNature() instanceof VariableNature.LoopVariable) &&
                            e.getValue().variableNature() != VariableNature.FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof DependentVariable))
                    .filter(vi -> statementAnalysis.isLocalVariableAndLocalToThisBlock(vi.name()) && !vi.isRead())
                    .forEach(vi -> statementAnalysis.ensure(Message.newMessage(location(),
                            Message.Label.UNUSED_LOCAL_VARIABLE, vi.name())));
        }
        return DONE;
    }

    AnalysisStatus checkUnusedLoopVariables(NavigationData<StatementAnalyser> navigationData) {
        if (statement() instanceof LoopStatement
                && !statementAnalysis.containsMessage(Message.Label.EMPTY_LOOP)
                && methodInfo().isNotATestMethod()) {
            statementAnalysis.rawVariableStream()
                    .filter(e -> e.getValue().variableNature() instanceof VariableNature.LoopVariable loopVariable &&
                            loopVariable.statementIndex().equals(index()))
                    .forEach(e -> {
                        String loopVarFqn = e.getKey();
                        StatementAnalyser first = navigationData.blocks.get().get(0).orElse(null);
                        StatementAnalysis statementAnalysis = first == null ? null : first.lastStatement().getStatementAnalysis();
                        if (statementAnalysis == null || !statementAnalysis.variableIsSet(loopVarFqn) ||
                                !statementAnalysis.getVariable(loopVarFqn).current().isRead()) {
                            this.statementAnalysis.ensure(Message.newMessage(location(), Message.Label.UNUSED_LOOP_VARIABLE, loopVarFqn));
                        }
                    });
        }
        return DONE;
    }

    /*
     * Can be delayed
     */
    AnalysisStatus checkUnusedReturnValueOfMethodCall(AnalyserContext analyserContext) {
        if (statementAnalysis.statement() instanceof ExpressionAsStatement eas
                && eas.expression instanceof MethodCall methodCall
                && methodInfo().isNotATestMethod()) {
            if (methodCall.methodInfo.returnType().isVoidOrJavaLangVoid()) return DONE;
            MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
            DV identity = methodAnalysis.getProperty(Property.IDENTITY);
            if (identity.isDelayed()) {
                LOGGER.debug("Delaying unused return value in {} {}, waiting for @Identity of {}",
                        index(), methodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
                return identity.causesOfDelay();
            }
            if (identity.valueIsTrue()) return DONE;
            DV fluent = methodAnalysis.getProperty(FLUENT);
            if (fluent.isDelayed()) {
                LOGGER.debug("Delaying unused return value in {} {}, waiting for @Fluent of {}",
                        index(), methodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
                return fluent.causesOfDelay();
            }
            if (fluent.valueIsTrue()) return DONE;
            DV modified = methodAnalysis.getProperty(MODIFIED_METHOD);
            if (modified.isDelayed() && !methodCall.methodInfo.isAbstract()) {
                LOGGER.debug("Delaying unused return value in {} {}, waiting for @Modified of {}",
                        index(), methodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
                return modified.causesOfDelay();
            }
            if (modified.valueIsFalse()) {
                MethodInspection methodCallInspection = analyserContext.getMethodInspection(methodCall.methodInfo);
                if (methodCallInspection.isStatic()) {
                    // for static methods, we verify if one of the parameters is modifying
                    CausesOfDelay delays = CausesOfDelay.EMPTY;
                    for (ParameterInfo parameterInfo : methodCallInspection.getParameters()) {
                        ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                        DV mv = parameterAnalysis.getProperty(MODIFIED_VARIABLE);
                        if (mv.valueIsTrue()) {
                            return DONE;
                        }
                        if (mv.isDelayed()) {
                            delays = delays.merge(mv.causesOfDelay());
                        }
                    }
                    if (delays.isDelayed()) {
                        LOGGER.debug("Delaying unused return value {} {}, waiting for @Modified of parameters in {}",
                                index(), methodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName());
                        return delays;
                    }
                }

                statementAnalysis.ensure(Message.newMessage(location(), Message.Label.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
            }
        }
        return DONE;
    }

}
