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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoContainerImpl;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.TryStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.*;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;

record SAInitializersAndUpdaters(StatementAnalysis statementAnalysis) {

    private String index() {
        return statementAnalysis.index();
    }

    private Statement statement() {
        return statementAnalysis.statement();
    }

    /*
    we create the variable(s), to make sure they exist in INIT level, but defer computation of their value to evaluation.
    In effect, we split int i=3; into int i (INIT); i=3 (EVAL);

    Loop and catch variables are special in that their scope is restricted to the statement and its block.
    We deal with them here, however they are assigned in the structure.

    Explicit constructor invocation uses "updaters" in the structure, but that is essentially level 3 evaluation.

    The for-statement has explicit initialisation and updating. These statements need evaluation, but the actual
    values are only used for independent for-loop analysis (not yet implemented) rather than for assigning real
    values to the loop variable.

    Loop (and catch) variables will be defined in level 2. A special local variable with a $<index> suffix will
    be created to represent a generic loop value.

    The special thing about creating variables at level 2 in a statement is that they are not transferred to the next statement,
    nor are they merged into level 4.
     */
    List<Expression> initializersAndUpdaters(ForwardAnalysisInfo forwardAnalysisInfo, EvaluationContext evaluationContext) {
        List<Expression> expressionsToEvaluate = new ArrayList<>();

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e), or for(int i=...), or int i=3, j=4;
        // variable will be set to a NewObject case of a catch

        if (forwardAnalysisInfo.catchVariable() != null) {
            // inject a catch(E1 | E2 e) { } exception variable, directly with assigned value, "read"
            LocalVariableCreation catchVariable = forwardAnalysisInfo.catchVariable();
            LocalVariable catchLv = catchVariable.declarations.get(0).localVariable();
            String name = catchLv.name();
            if (!statementAnalysis.variableIsSet(name)) {
                LocalVariableReference lvr = new LocalVariableReference(catchLv);
                Properties properties = Properties.of(Map.of(
                        IMMUTABLE, IMMUTABLE.falseDv,
                        INDEPENDENT, INDEPENDENT.falseDv,
                        CONTAINER, CONTAINER.falseDv,
                        IDENTITY, IDENTITY.falseDv,
                        IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv,
                        NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV));
                VariableInfoContainer vic = VariableInfoContainerImpl.newCatchVariable(
                        statementAnalysis.location(Stage.INITIAL), lvr, index(),
                        Instance.forCatchOrThis(index(), lvr, properties),
                        statementAnalysis.navigationData().hasSubBlocks());
                ((StatementAnalysisImpl) statementAnalysis).putVariable(name, vic);
            }
        }

        Set<Variable> variableCreatedInLoop = new HashSet<>();
        for (Expression expression : statementAnalysis.statement().getStructure().initialisers()) {

            List<Assignment> patterns = new SAPatternVariable(statementAnalysis)
                    .patternVariables(evaluationContext, expression);
            expressionsToEvaluate.addAll(patterns);

            if (expression instanceof LocalVariableCreation lvc) {
                boolean addInitializersSeparately;
                if (statement() instanceof LoopStatement) {
                    addInitializersSeparately = true;
                } else {
                    expressionsToEvaluate.add(PropertyWrapper.wrapPreventIncrementalEvaluation(lvc));
                    addInitializersSeparately = false;
                }
                for (LocalVariableCreation.Declaration declaration : lvc.declarations) {
                    String name = declaration.localVariable().name();
                    variableCreatedInLoop.add(declaration.localVariableReference());

                    if (!statementAnalysis.variableIsSet(name)) {

                        // create the local (loop) variable

                        LocalVariableReference lvr = new LocalVariableReference(declaration.localVariable());
                        VariableNature variableNature;
                        if (statement() instanceof LoopStatement) {
                            variableNature = new VariableNature.LoopVariable(index(), statementAnalysis);
                        } else if (statement() instanceof TryStatement) {
                            variableNature = new VariableNature.TryResource(index());
                        } else {
                            variableNature = new VariableNature.NormalLocalVariable(index());
                        }
                        statementAnalysis.createVariable(evaluationContext, lvr,
                                VariableInfoContainer.NOT_A_VARIABLE_FIELD, variableNature);
                        if (statement() instanceof LoopStatement) {
                            ((StatementAnalysisImpl) statementAnalysis).ensureLocalVariableAssignedInThisLoop(lvr.fullyQualifiedName());
                        }
                    }

                    // what should we evaluate? catch: assign a value which will be read; for(int i=0;...) --> 0 instead of i=0;
                    if (addInitializersSeparately && declaration.expression() != EmptyExpression.EMPTY_EXPRESSION) {
                        expressionsToEvaluate.add(PropertyWrapper.wrapPreventIncrementalEvaluation(declaration.expression()));
                    }
                }
            } else {
                if (expression != null && expression != EmptyExpression.EMPTY_EXPRESSION) {
                    expressionsToEvaluate.add(PropertyWrapper.wrapPreventIncrementalEvaluation(expression));
                }
            }
        }

        // part 2: updaters, + determine which local variables are modified in the updaters

        if (statementAnalysis.statement() instanceof LoopStatement) {
            for (Expression expression : statementAnalysis.statement().getStructure().updaters()) {
                expression.visit(e -> {
                    VariableExpression ve;
                    if (e instanceof Assignment assignment && ((ve = assignment.target.asInstanceOf(VariableExpression.class)) != null)) {
                        boolean locallyCreated = variableCreatedInLoop.contains(ve.variable());
                        if (locallyCreated) {
                            // for(int i=0; i...)
                            expressionsToEvaluate.add(PropertyWrapper.wrapPreventIncrementalEvaluation(assignment.value));
                        } else {
                            // when exactly?  int i=9; for(; ...; i++) or int i; for(i=3; ...; i++)
                            expressionsToEvaluate.add(PropertyWrapper.wrapPreventIncrementalEvaluation(assignment.cloneWithHackForLoop()));
                        }
                    }
                });
            }
        } else if (statementAnalysis.statement() instanceof ExplicitConstructorInvocation eci) {
            Structure structure = statement().getStructure();
            expressionsToEvaluate.addAll(replaceExplicitConstructorInvocation(evaluationContext, eci, structure.updaters()));
        }

        return expressionsToEvaluate;
    }


    private List<Expression> replaceExplicitConstructorInvocation(EvaluationContext evaluationContext,
                                                                  ExplicitConstructorInvocation eci,
                                                                  List<Expression> updaters) {
         /* structure.updaters contains all the parameter values
               expressionsToEvaluate should contain assignments for each instance field, as found in the last statement of the
               explicit method
             */
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();

        MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(eci.methodInfo);
        assert methodAnalysis != null : "Cannot find method analysis for " + eci.methodInfo.fullyQualifiedName;

        EvaluationResult context = EvaluationResult.from(evaluationContext);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        TranslationMapImpl.Builder translationMapBuilder = new TranslationMapImpl.Builder();
        CauseOfDelay causeOfDelay = new SimpleCause(evaluationContext.getLocation(EVALUATION), CauseOfDelay.Cause.ECI);

        boolean weMustWait;
        if (!methodAnalysis.hasBeenAnalysedUpToIteration0() && methodAnalysis.isComputed()) {
            assert evaluationContext.getIteration() == 0 : "In iteration " + evaluationContext.getIteration();
            /* if the method has not gone through 1st iteration of analysis, we need to wait.
             this should never be a circular wait because we're talking a strict constructor hierarchy
             the delay has to have an effect on CM in the next iterations, because introducing the assignments here
             will cause delays (see LoopStatement constructor, where "expression" appears in statement 1, iteration 1)
             because of the 'super' call to StatementWithExpression which comes after LoopStatement.
             Without method analysis we have no idea which variables will be affected
             */

            weMustWait = true;
        } else {
            weMustWait = false;
        }

        List<Expression> assignments = new ArrayList<>();
        int i = 0;
        for (Expression updater : updaters) {
            ParameterInfo parameterInfo = eci.methodInfo.methodInspection.get().getParameters().get(i);
            translationMapBuilder.put(new VariableExpression(parameterInfo), updater);
            translationMapBuilder.addVariableExpression(parameterInfo, updater);

            /*
            next to the assignments, we also do normal evaluations of the arguments of the ECI
            Especially in the first iteration, when the translated expression is not yet done, we must have a way to
            indicate that variables in the arguments (typically, parameters of the constructor with the ECI) are read
            See e.g. ECI_7 (ECI 6 also goes through this, but never in the weMustWait state.)
            */
            if (updater instanceof VariableExpression ve && ve.variable() instanceof ParameterInfo pi) {
                Map<Property, DV> contextProperties;
                if (weMustWait) {
                    DV dv = DelayFactory.createDelay(causeOfDelay);
                    contextProperties = Map.of(CONTEXT_CONTAINER, dv, CONTEXT_NOT_NULL, dv,
                            CONTEXT_IMMUTABLE, dv, CONTEXT_MODIFIED, dv);
                } else {
                    StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                    if (lastStatement == null) {
                        contextProperties = Map.of();
                    } else {
                        VariableInfoContainer vic = lastStatement.getVariable(parameterInfo.fullyQualifiedName);
                        VariableInfo vi = vic.current();
                        contextProperties = vi.contextProperties().toImmutableMap();
                    }
                }
                ForwardEvaluationInfo forwardEvaluationInfo = new ForwardEvaluationInfo.Builder()
                        .addProperties(contextProperties)
                        .build();
                VariableExpression newVe = new VariableExpressionFixedForward(pi, forwardEvaluationInfo);
                assignments.add(newVe);
            } else {
                assignments.add(updater);
            }
            i++;
        }


        TypeInfo eciType = methodAnalysis.getMethodInfo().typeInfo;
        TranslationMap translationMap = translationMapBuilder.setRecurseIntoScopeVariables(true).build();
        List<FieldInfo> visibleFields = eciType.visibleFields(analyserContext);
        for (FieldInfo fieldInfo : visibleFields) {
            FieldInspection fieldInspection = analyserContext.getFieldInspection(fieldInfo);
            if (!fieldInspection.isStatic()) {
                boolean assigned = false;
                for (VariableInfo variableInfo : methodAnalysis.getFieldAsVariable(fieldInfo)) {
                    if (variableInfo.isAssigned()) {
                        Expression start = variableInfo.getValue();
                        FieldReference fr = new FieldReference(analyserContext, fieldInfo);
                        Expression translated1 = start.translate(analyserContext, translationMap);
                        Expression translated = evaluationContext.getIteration() > 0
                                ? replaceUnknownFields(evaluationContext, translated1) : translated1;

                        ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().doNotReevaluateVariableExpressionsDoNotComplain().build();
                        EvaluationResult er = translated.evaluate(context, fwd);
                        Expression end = er.value();
                        builder.compose(er);

                        Assignment assignment = new Assignment(Identifier.generate("assignment eci"),
                                statementAnalysis.primitives(),
                                new VariableExpression(fr),
                                end, null, null, false, false, null, null);
                        assignments.add(assignment);
                        assigned = true;
                    }
                }
                if (!assigned && weMustWait) {
                    FieldReference fr = new FieldReference(analyserContext, fieldInfo);
                    Expression end = DelayedExpression.forECI(fieldInfo.getIdentifier(), eciVariables(), DelayFactory.createDelay(causeOfDelay));
                    Assignment assignment = new Assignment(Identifier.generate("assignment eci"),
                            statementAnalysis.primitives(),
                            new VariableExpression(fr),
                            end, null, null, false, false, null, null);
                    assignments.add(assignment);
                }
            }
        }

        return assignments;
    }

    private Expression eciVariables() {
        MethodInfo methodInfo = statementAnalysis.methodAnalysis().getMethodInfo();
        List<Variable> variables = methodInfo.methodInspection.get().getParameters().stream().map(v -> (Variable) v).toList();
        return MultiExpressions.from(statement().getIdentifier(), variables);
    }

    private Expression replaceUnknownFields(EvaluationContext evaluationContext, Expression expression) {
        List<Variable> variables = expression.variables(true);
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        Identifier identifier = statement().getIdentifier();
        for (Variable variable : variables) {
            if (!statementAnalysis.variableIsSet(variable.fullyQualifiedName())) {
                Properties properties = evaluationContext.getAnalyserContext()
                        .defaultValueProperties(variable.parameterizedType());
                ExpandedVariable ev = new ExpandedVariable(identifier, variable, properties);
                builder.addVariableExpression(variable, ev);
                builder.put(new VariableExpression(variable), ev);
            }
        }
        TranslationMap translationMap = builder.build();
        return expression.translate(evaluationContext.getAnalyserContext(), translationMap);
    }

}
