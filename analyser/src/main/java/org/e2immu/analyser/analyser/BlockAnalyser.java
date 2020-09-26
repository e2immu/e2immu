/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.pattern.*;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

// the statement analyser does not add annotations, but it does raise errors
// output of the only public method is in changes to the properties of the variables in the evaluation context

public class BlockAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnalyser.class);

    private final MethodAnalysis methodAnalysis;
    private final MethodInfo methodInfo;
    private final Messages messages = new Messages();

    public final StatementAnalysis statementAnalysis;

    public BlockAnalyser(MethodInfo methodInfo) {
        this.methodAnalysis = methodInfo.methodAnalysis.get();
        this.methodInfo = methodInfo;
    }

    public boolean computeVariablePropertiesOfBlock(StatementAnalysis startStatementIn, EvaluationContext evaluationContext) {
        VariableProperties variableProperties = (VariableProperties) evaluationContext;
        boolean changes = false;

        NumberedStatement startStatement = startStatementIn.followReplacements();
        NumberedStatement statement = Objects.requireNonNull(startStatement); // for IntelliJ

        boolean neverContinues = false;
        boolean escapesViaException = false;
        List<BreakOrContinueStatement> breakAndContinueStatementsInBlocks = new ArrayList<>();

        try {
            while (statement != null) {

                // first attempt at detecting a transformation
                if (!variableProperties.configuration.analyserConfiguration.skipTransformations) {
                    Optional<MatchResult> matchResult = variableProperties.patternMatcher.match(methodInfo, statement);
                    if (matchResult.isPresent()) {
                        MatchResult mr = matchResult.get();
                        Optional<Replacement> replacement = variableProperties.patternMatcher.registeredReplacement(mr.pattern);
                        if (replacement.isPresent()) {
                            Replacement r = replacement.get();
                            log(TRANSFORM, "Replacing {} with {} in {} at {}", mr.pattern.name, r.name, methodInfo.distinguishingName(), statement.index);
                            Replacer.replace(variableProperties, mr, r);
                            variableProperties.patternMatcher.reset(methodInfo);
                            changes = true;
                        }
                    }
                }
                statement = statement.followReplacements();

                String statementId = statement.index;
                variableProperties.setCurrentStatement(statement);

                if (variableProperties.conditionManager.inErrorState()) {
                    variableProperties.raiseError(Message.UNREACHABLE_STATEMENT);
                }

                if (computeVariablePropertiesOfStatement(statement, variableProperties)) {
                    changes = true;
                }
                statement = statement.followReplacements();

                for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                        ((VariableProperties) evaluationContext).configuration.debugConfiguration.statementAnalyserVariableVisitors) {
                    variableProperties.variableProperties().forEach(aboutVariable ->
                            statementAnalyserVariableVisitor.visit(
                                    new StatementAnalyserVariableVisitor.Data(((VariableProperties) evaluationContext).iteration, methodInfo,
                                            statementId, aboutVariable.name, aboutVariable.variable,
                                            aboutVariable.getCurrentValue(),
                                            aboutVariable.getStateOnAssignment(),
                                            aboutVariable.getObjectFlow(), aboutVariable.properties())));
                }
                for (StatementAnalyserVisitor statementAnalyserVisitor : ((VariableProperties) evaluationContext)
                        .configuration.debugConfiguration.statementAnalyserVisitors) {
                    statementAnalyserVisitor.visit(
                            new StatementAnalyserVisitor.Data(
                                    ((VariableProperties) evaluationContext).iteration, methodInfo, statement, statement.index,
                                    variableProperties.conditionManager.getCondition(),
                                    variableProperties.conditionManager.getState()));
                }

                if (statement.statement instanceof ReturnStatement || statement.statement instanceof ThrowStatement) {
                    neverContinues = true;
                }
                if (statement.statement instanceof ThrowStatement) {
                    escapesViaException = true;
                }
                if (statement.statement instanceof BreakOrContinueStatement) {
                    breakAndContinueStatementsInBlocks.add((BreakOrContinueStatement) statement.statement);
                }
                // it is here that we'll inherit from blocks inside the statement
                if (statement.neverContinues.isSet() && statement.neverContinues.get()) neverContinues = true;
                if (statement.breakAndContinueStatements.isSet()) {
                    breakAndContinueStatementsInBlocks.addAll(filterBreakAndContinue(statement, statement.breakAndContinueStatements.get()));
                    if (!breakAndContinueStatementsInBlocks.isEmpty()) {
                        variableProperties.setGuaranteedToBeReachedInCurrentBlock(false);
                    }
                }
                if (statement.escapes.isSet() && statement.escapes.get()) escapesViaException = true;
                statement = statement.next.get().orElse(null);
                if (statement != null && statement.replacement.isSet()) {
                    statement = statement.replacement.get();
                }
            }
            // at the end, at the top level, there is a return, even if it is implicit
            boolean atTopLevel = variableProperties.depth == 0;
            if (atTopLevel) {
                neverContinues = true;
            }

            if (!startStatement.neverContinues.isSet()) {
                log(VARIABLE_PROPERTIES, "Never continues at end of block of {}? {}", startStatement.index, neverContinues);
                startStatement.neverContinues.set(neverContinues);
            }
            if (!startStatement.escapes.isSet()) {
                if (variableProperties.conditionManager.delayedCondition() || variableProperties.delayedState()) {
                    log(DELAYED, "Delaying escapes because of delayed conditional, {}", startStatement.index);
                } else {
                    log(VARIABLE_PROPERTIES, "Escapes at end of block of {}? {}", startStatement.index, escapesViaException);
                    startStatement.escapes.set(escapesViaException);

                    if (escapesViaException) {
                        if (startStatement.parent == null) {
                            log(VARIABLE_PROPERTIES, "Observing unconditional escape");
                        } else {
                            notNullEscapes(variableProperties);
                            sizeEscapes(variableProperties);
                            precondition(variableProperties, startStatement.parent);
                        }
                    }
                }
            }
            // order is important, because unused gets priority
            if (unusedLocalVariablesCheck(variableProperties)) changes = true;
            if (uselessAssignments(variableProperties, escapesViaException, neverContinues)) changes = true;

            if (isLogEnabled(DEBUG_LINKED_VARIABLES) && !variableProperties.dependencyGraph.isEmpty()) {
                log(DEBUG_LINKED_VARIABLES, "Dependency graph of linked variables best case:");
                variableProperties.dependencyGraph.visit((n, list) -> log(DEBUG_LINKED_VARIABLES, " -- {} --> {}", n.detailedString(),
                        list == null ? "[]" : StringUtil.join(list, Variable::detailedString)));
            }

            if (!startStatement.breakAndContinueStatements.isSet())
                startStatement.breakAndContinueStatements.set(ImmutableList.copyOf(breakAndContinueStatementsInBlocks));
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in statement analyser: {}", statement);
            throw rte;
        }
    }

    // whatever that has not been picked up by the notNull and the size escapes
    private static void precondition(VariableProperties variableProperties, NumberedStatement parentStatement) {
        Value precondition = variableProperties.conditionManager.escapeCondition(variableProperties);
        if (precondition != UnknownValue.EMPTY) {
            boolean atLeastFieldOrParameterInvolved = precondition.variables().stream().anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
            if (atLeastFieldOrParameterInvolved) {
                log(VARIABLE_PROPERTIES, "Escape with precondition {}", precondition);

                // set the precondition on the top level statement
                if (!parentStatement.precondition.isSet()) {
                    parentStatement.precondition.set(precondition);
                }
                if (variableProperties.uponUsingConditional != null) {
                    log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                    variableProperties.uponUsingConditional.run();
                }
            }
        }
    }

    private static void notNullEscapes(VariableProperties variableProperties) {
        Set<Variable> nullVariables = variableProperties.conditionManager.findIndividualNullConditions();
        for (Variable nullVariable : nullVariables) {
            log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.detailedString());
            ((ParameterInfo) nullVariable).parameterAnalysis.get().improveProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

            // as a context property
            variableProperties.addProperty(nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
            if (variableProperties.uponUsingConditional != null) {
                log(VARIABLE_PROPERTIES, "Disabled errors on if-statement");
                variableProperties.uponUsingConditional.run();
            }
        }
    }

    private static void sizeEscapes(VariableProperties variableProperties) {
        Map<Variable, Value> individualSizeRestrictions = variableProperties.conditionManager.findIndividualSizeRestrictionsInCondition();
        for (Map.Entry<Variable, Value> entry : individualSizeRestrictions.entrySet()) {
            ParameterInfo parameterInfo = (ParameterInfo) entry.getKey();
            Value negated = NegatedValue.negate(entry.getValue());
            log(VARIABLE_PROPERTIES, "Escape with check on size on {}: {}", parameterInfo.detailedString(), negated);
            int sizeRestriction = negated.encodedSizeRestriction();
            if (sizeRestriction > 0) { // if the complement is a meaningful restriction
                parameterInfo.parameterAnalysis.get().improveProperty(VariableProperty.SIZE, sizeRestriction);

                variableProperties.addProperty(parameterInfo, VariableProperty.SIZE, sizeRestriction);
                if (variableProperties.uponUsingConditional != null) {
                    log(VARIABLE_PROPERTIES, "Disabled errors on if-statement");
                    variableProperties.uponUsingConditional.run();
                }
            }
        }
    }


    private Collection<? extends BreakOrContinueStatement> filterBreakAndContinue(NumberedStatement statement,
                                                                                  List<BreakOrContinueStatement> statements) {
        if (statement.statement instanceof LoopStatement) {
            String label = ((LoopStatement) statement.statement).label;
            // we only retain those break and continue statements for ANOTHER labelled statement
            // (the break statement has a label, and it does not equal the one of this loop)
            return statements.stream().filter(s -> s.label != null && !s.label.equals(label)).collect(Collectors.toList());
        } else if (statement.statement instanceof SwitchStatement) {
            // continue statements cannot be for a switch; must have a labelled statement to break from a loop outside the switch
            return statements.stream().filter(s -> s instanceof ContinueStatement || s.label != null).collect(Collectors.toList());
        } else return statements;
    }



    private static boolean haveSubBlocks(Structure structure) {
        return structure.haveNonEmptyBlock() ||
                structure.statements != null && !structure.statements.isEmpty() ||
                !structure.subStatements.isEmpty();
    }

    private Set<Variable> computeExistingVariablesAssignedInLoop(Statement statement, VariableProperties variableProperties) {
        return statement.collect(Assignment.class).stream()
                .flatMap(a -> a.assignmentTarget().stream())
                .filter(variableProperties::isKnown)
                .collect(Collectors.toSet());
    }

}
