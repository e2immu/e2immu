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
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.VariableAccessReport;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;

record SAHelper(StatementAnalysis statementAnalysis) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAHelper.class);

    static Filter.FilterResult<ParameterInfo> moveConditionToParameter(EvaluationResult context, Expression expression) {
        Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<ParameterInfo> result = filter.filter(expression, filter.individualNullOrNotNullClauseOnParameter());
        if (result != null && !result.accepted().isEmpty() && result.rest().isBoolValueTrue()) {
            return result;
        }
        return null;
    }

    static Expression bestValue(ChangeData valueChangeData, VariableInfo vi1) {
        if (valueChangeData != null && valueChangeData.value() != null) {
            return valueChangeData.value();
        }
        if (vi1 != null) {
            return vi1.getValue();
        }
        return null;
    }

    static void mergePreviousAndChangeOnlyGroupPropertyValues(
            EvaluationContext evaluationContext,
            Variable variable,
            Map<Property, DV> previous,
            Map<Property, DV> changeData,
            GroupPropertyValues groupPropertyValues) {
        GroupPropertyValues.PROPERTIES.forEach(k -> {
            DV prev = previous.getOrDefault(k, k.valueWhenAbsent());
            DV change = changeData == null ? k.valueWhenAbsent() : changeData.getOrDefault(k, k.valueWhenAbsent());
            DV value = groupPropertyValue(k, prev, change, evaluationContext, variable);
            groupPropertyValues.set(k, variable, value);
        });
    }

    private static DV groupPropertyValue(Property k, DV prev, DV change, EvaluationContext evaluationContext, Variable variable) {
        return switch (k) {
            case EXTERNAL_IGNORE_MODIFICATIONS -> prev.minIgnoreNotInvolved(change);
            case CONTAINER_RESTRICTION, EXTERNAL_IMMUTABLE, EXTERNAL_NOT_NULL, CONTEXT_IMMUTABLE -> prev.max(change);
            case CONTEXT_MODIFIED -> VariableInfo.MAX_CM.apply(prev, change);
            case CONTEXT_CONTAINER -> evaluationContext.isMyselfExcludeThis(variable).toFalse(k)
                    ? MultiLevel.NOT_CONTAINER_DV
                    : VariableInfo.MAX_CC.apply(prev, change);
            case CONTEXT_NOT_NULL ->
                    AnalysisProvider.defaultNotNull(variable.parameterizedType()).max(prev).max(change);
            default -> throw new UnsupportedOperationException();
        };
    }

    static Map<Property, DV> mergePreviousAndChange(
            EvaluationContext evaluationContext,
            Variable variable,
            Map<Property, DV> previous,
            Map<Property, DV> changeData,
            GroupPropertyValues groupPropertyValues,
            boolean valuePropertiesFromPrevious) {
        Set<Property> both = new HashSet<>(previous.keySet());
        both.addAll(changeData.keySet());
        both.addAll(GroupPropertyValues.PROPERTIES);
        Map<Property, DV> res = new HashMap<>(changeData);


        both.remove(IN_NOT_NULL_CONTEXT);
        handleInNotNullContext(previous, res);

        both.forEach(k -> {
            DV prev = previous.getOrDefault(k, k.valueWhenAbsent());
            DV change = changeData.getOrDefault(k, k.valueWhenAbsent());
            if (k.isGroupProperty()) {
                DV value = groupPropertyValue(k, prev, change, evaluationContext, variable);
                groupPropertyValues.set(k, variable, value);
            } else {
                switch (k) {
                    // value properties are copied from previous, only when the value from previous is copied as well
                    case NOT_NULL_EXPRESSION, CONTAINER, IMMUTABLE, IDENTITY, INDEPENDENT, IGNORE_MODIFICATIONS -> {
                        if (valuePropertiesFromPrevious) res.put(k, prev);
                    }
                    // all other properties are copied from change data
                    default -> { // no need, change data already present
                    }
                }
            }
        });
        res.keySet().removeAll(GroupPropertyValues.PROPERTIES);
        return res;
    }

    private static void handleInNotNullContext(Map<Property, DV> previous, Map<Property, DV> res) {
        DV prev = previous.getOrDefault(IN_NOT_NULL_CONTEXT, null);
        assert prev == null || prev.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        DV change = res.getOrDefault(IN_NOT_NULL_CONTEXT, null);
        if (change != null) {
            assert change.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            if (prev != null) {
                res.put(IN_NOT_NULL_CONTEXT, prev.min(change));
            }
        } else {
            if (prev != null) res.put(IN_NOT_NULL_CONTEXT, prev);
        }
    }


    /*
    Variable is target of assignment. In terms of CNN/CM it should be neutral (rather than delayed), as its current value
    is not of relevance.

    There is no overlap between valueProps and variableProps
     */
    static Properties mergeAssignment(Variable variable,
                                      Properties valueProps,
                                      Properties changeData,
                                      Properties externalProperties,
                                      GroupPropertyValues groupPropertyValues) {
        Properties res = valueProps.combine(changeData);

        // reasoning: only relevant when assigning to a field, this assignment is in StaticallyAssignedVars, so
        // the field's value is taken anyway
        externalProperties.stream().forEach(e -> groupPropertyValues.set(e.getKey(), variable, e.getValue()));

        DV cnn = res.remove(CONTEXT_NOT_NULL);
        groupPropertyValues.set(CONTEXT_NOT_NULL, variable, cnn == null ? AnalysisProvider.defaultNotNull(variable.parameterizedType()) : cnn);

        DV cm = res.remove(CONTEXT_MODIFIED);
        groupPropertyValues.set(CONTEXT_MODIFIED, variable, cm == null ? DV.FALSE_DV : cm);

        DV cImm = res.remove(CONTEXT_IMMUTABLE);
        groupPropertyValues.set(CONTEXT_IMMUTABLE, variable, cImm == null ? MultiLevel.MUTABLE_DV : cImm);

        DV cCont = res.remove(CONTEXT_CONTAINER);
        groupPropertyValues.set(CONTEXT_CONTAINER, variable, cCont == null ? MultiLevel.NOT_CONTAINER_DV : cCont);

        return res;
    }

    public static Expression computeStaticSideEffect(AnalyserContext analyserContext,
                                                     StatementAnalysis statementAnalysis) {
        Expression expression = statementAnalysis.statement().getStructure().expression();
        if (expression.isEmpty()) {
            return EmptyExpression.EMPTY_EXPRESSION;
        }

        AtomicReference<CausesOfDelay> causesOfDelay = new AtomicReference<>(CausesOfDelay.EMPTY);
        List<Expression> expressions = new ArrayList<>();

        expression.visit(e -> {
            if (e instanceof MethodCall methodCall) {
                if (statementAnalysis.methodAnalysis().getMethodInfo().equals(methodCall.methodInfo)) {
                    return;
                }
                if(methodCall.methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle()) {
                    return;
                }

                // SITUATION 1: the method has been marked with @StaticSideEffects
                MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
                DV isSSE = methodAnalysis.getProperty(STATIC_SIDE_EFFECTS);

                CausesOfDelay delay1;
                if (isSSE.isDelayed()) {
                    delay1 = isSSE.causesOfDelay();
                } else {
                    if (isSSE.valueIsTrue()) {
                        expressions.add(methodCall);
                        return;
                    }
                    delay1 = CausesOfDelay.EMPTY;
                }

                /*
                 SITUATION 2: the method's object is a static field whose modifications are ignored,
                 and it does not modify its parameters
                 */
                CausesOfDelay delay2;
                if (methodCall.object instanceof VariableExpression ve
                        && ve.variable() instanceof FieldReference fr
                        && fr.isStatic()) {
                    DV modifying = methodAnalysis.getProperty(MODIFIED_METHOD);
                    if (modifying.valueIsFalse()) {
                        return;
                    }
                    FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo());
                    DV ignoreMods = fieldAnalysis.getProperty(EXTERNAL_IGNORE_MODIFICATIONS);
                    if (MultiLevel.NOT_IGNORE_MODS_DV.equals(ignoreMods)) {
                        return;
                    }
                    if (modifying.isDelayed() || ignoreMods.isDelayed()) {
                        delay2 = modifying.causesOfDelay().merge(ignoreMods.causesOfDelay());
                    } else {
                        assert modifying.valueIsTrue() && MultiLevel.IGNORE_MODS_DV.equals(ignoreMods);
                        expressions.add(methodCall);
                        return;
                    }
                } else {
                    delay2 = CausesOfDelay.EMPTY;
                }
                causesOfDelay.set(causesOfDelay.get().merge(delay1).merge(delay2));
            }
            if (e instanceof Assignment assignment
                    && assignment.target instanceof VariableExpression ve
                    && ve.variable() instanceof FieldReference fr && fr.isStatic()) {
                FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo());
                DV ignoreMods = fieldAnalysis.getProperty(EXTERNAL_IGNORE_MODIFICATIONS);
                if (ignoreMods.isDelayed()) {
                    causesOfDelay.set(causesOfDelay.get().merge(ignoreMods.causesOfDelay()));
                }
                if (MultiLevel.IGNORE_MODS_DV.equals(ignoreMods)) {
                    expressions.add(assignment);
                }
            }
        });
        if (!expressions.isEmpty()) return expressions.get(0);
        if (causesOfDelay.get().isDelayed())
            return DelayedExpression.forStaticSideEffects(statementAnalysis.statement().getIdentifier(),
                    statementAnalysis.primitives(), expression, causesOfDelay.get());
        return EmptyExpression.EMPTY_EXPRESSION;
    }

    public void visitStatementVisitors(String statementId,
                                       AnalyserResult result,
                                       StatementAnalyserSharedState sharedState,
                                       DebugConfiguration debugConfiguration,
                                       AnalyserComponents<String, StatementAnalyserSharedState> analyserComponents,
                                       VariableAccessReport variableAccessReport) {
        MethodInfo methodInfo = statementAnalysis.methodAnalysis().getMethodInfo();
        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                debugConfiguration.statementAnalyserVariableVisitors()) {
            statementAnalysis.rawVariableStream()
                    .map(Map.Entry::getValue)
                    .forEach(variableInfoContainer -> statementAnalyserVariableVisitor.visit(
                            new StatementAnalyserVariableVisitor.Data(
                                    sharedState.evaluationContext().getIteration(),
                                    sharedState.context(),
                                    methodInfo,
                                    statementId,
                                    variableInfoContainer.current().name(),
                                    variableInfoContainer.current().variable(),
                                    variableInfoContainer.current().getValue(),
                                    variableInfoContainer.current().getProperties(),
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor : debugConfiguration.statementAnalyserVisitors()) {
            ConditionManager cm = statementAnalysis.stateData().getConditionManagerForNextStatement();
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            result,
                            sharedState.evaluationContext().getIteration(),
                            sharedState.context(),
                            methodInfo,
                            statementAnalysis,
                            statementAnalysis.index(),
                            cm == null ? null : cm.condition(),
                            cm == null ? "" : cm.conditionVariables().stream().map(Variable::simpleName).sorted().collect(Collectors.joining(", ")),
                            cm == null ? null : cm.state(),
                            cm == null ? null : cm.absoluteState(sharedState.context()),
                            cm,
                            sharedState.localConditionManager(),
                            analyserComponents.getStatusesAsMap(),
                            variableAccessReport));
        }
    }

    // see VariableScope_13:  int j = y instance of X x ? x.i: 3;
    // after this statement, x does not exist, and x.i needs to become scope-x:0.i

    public static EvaluationResult scopeVariablesForPatternVariables(EvaluationResult evaluationResult2, String index) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        for (Map.Entry<Variable, ChangeData> e : evaluationResult2.changeData().entrySet()) {
            if (evaluationResult2.evaluationContext().isPatternVariableCreatedAt(e.getKey(), index)) {
                Variable pv = e.getKey();

                // we have pattern variables, which should not exist in the next iteration. This is not a problem in itself, but it is
                // where there are also fields that have these pattern variables in their scope. Because the value may have to live on,
                // a scope variable will need creating for every pattern variable used in a scope
                List<Map.Entry<Variable, ChangeData>> entriesOfFieldRefs =
                        evaluationResult2.changeData().entrySet().stream()
                                .filter(e1 -> e1.getKey() instanceof FieldReference fr && fr.hasAsScopeVariable(pv)).toList();
                if (!entriesOfFieldRefs.isEmpty()) {

                    // we'll have to create a scope variable
                    String name = "scope-" + pv.simpleName() + ":" + index;
                    VariableNature vn = new VariableNature.ScopeVariable(index);
                    TypeInfo currentType = evaluationResult2.getCurrentType();
                    LocalVariable lv = new LocalVariable(Set.of(LocalVariableModifier.FINAL), name,
                            pv.parameterizedType(), List.of(), currentType, vn);
                    Expression scopeValue = Objects.requireNonNullElseGet(e.getValue().value(),
                            () -> DelayedVariableExpression.forVariable(e.getKey(), evaluationResult2.statementTime(),
                                    evaluationResult2.causesOfDelay()));
                    LocalVariableReference scopeVariable = new LocalVariableReference(lv, scopeValue);
                    Identifier statementIdentifier = evaluationResult2.evaluationContext()
                            .getLocation(Stage.EVALUATION).identifier();
                    Expression scope = new VariableExpression(statementIdentifier, scopeVariable);

                    builder.put(pv, scopeVariable);
                    builder.addVariableExpression(pv, scope);

                    // then, other field references
                    for (Map.Entry<Variable, ChangeData> e2 : entriesOfFieldRefs) {
                        FieldReference fr = (FieldReference) e2.getKey();
                        assert fr.scopeVariable() != null && fr.scopeVariable().equals(pv)
                                : "other situations not yet implemented";

                        FieldReference newFr = new FieldReferenceImpl(evaluationResult2.getAnalyserContext(),
                                fr.fieldInfo(), scope, scopeVariable, fr.getOwningType());
                        VariableExpression ve = new VariableExpression(statementIdentifier, newFr,
                                VariableExpression.NO_SUFFIX, scope, null);
                        builder.addVariableExpression(fr, ve);
                        builder.put(fr, newFr);
                    }
                }
            }
        }
        if (builder.isEmpty()) return evaluationResult2;
        // what to do? create a new evaluation result, where (1) the new scope variables have been added, (2) the fields are replaced
        // and (3) all values related to these fields are replaced (that's the most difficult)
        TranslationMap translationMap = builder.build();
        return evaluationResult2.translate(translationMap);

    }
}
