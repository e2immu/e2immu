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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MethodCallIncompatibleWithPrecondition {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodCallIncompatibleWithPrecondition.class);

    /*
    look into the latest value of the fields
    they must have instance state that is incompatible with the precondition.

    return null upon delays.
     */
    public static DV isMark(EvaluationContext evaluationContext,
                            Set<FieldInfo> fields,
                            MethodAnalyser methodAnalyser) {
        StatementAnalysis statementAnalysis = methodAnalyser.getMethodAnalysis().getLastStatement();
        assert statementAnalysis.methodLevelData().combinedPrecondition.isFinal();
        Expression precondition = statementAnalysis.methodLevelData().combinedPrecondition.get().expression();
        Expression preconditionInTermsOfAspect = replaceByAspectsWherePossible(evaluationContext, precondition);

        // IMPROVE add stateData.conditionManagerForNextStatement.state to this
        for (FieldInfo fieldInfo : fields) {
            FieldReference fieldReference = new FieldReference(InspectionProvider.DEFAULT, fieldInfo);
            VariableInfo variableInfo = statementAnalysis.findOrNull(fieldReference, VariableInfoContainer.Level.MERGE);
            if (!variableInfo.valueIsSet()) {
                LOGGER.debug("Delaying isMark, no value for field {} in last statement of {}",
                        fieldInfo.name, methodAnalyser.getMethodInfo().fullyQualifiedName);
                return new SimpleSet(new VariableCause(variableInfo.variable(),
                        statementAnalysis.location(), CauseOfDelay.Cause.VALUE));
            }
            if (evaluationContext.hasState(variableInfo.getValue())) {
                Expression state = variableInfo.getValue().stateTranslateThisTo(fieldReference);
                if (!state.isBoolValueTrue()) {
                    Expression stateWithInvariants = enrichWithInvariants(evaluationContext, state);

                    /* Another issue that we have to deal with is that of overloaded methods
                       the precondition contains references to Set.size() while the invariants have been
                       computed on Collection.size()

                       We have to normalise, and cannot easily do that at equality level
                     */
                    Expression normalisedPrecondition = normaliseMethods(evaluationContext, preconditionInTermsOfAspect);
                    Expression normalisedStateWithInvariants = normaliseMethods(evaluationContext, stateWithInvariants);
                    Expression and = And.and(evaluationContext, normalisedPrecondition, normalisedStateWithInvariants);
                    if (and.isBoolValueFalse()) return DV.TRUE_DV;
                }
            }
        }
        return DV.FALSE_DV;
    }

    private static Expression normaliseMethods(EvaluationContext evaluationContext, Expression expression) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        expression.visit(e -> {
            if (e instanceof MethodCall methodCall && !builder.translateMethod(methodCall.methodInfo)) {
                MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodCall.methodInfo);
                if (methodAnalysis.getProperty(Property.MODIFIED_METHOD).valueIsFalse()) {
                    // non-modifying method; from all overrides, choose the one that does not have an override
                    // IMPROVE there could be multiple, but then, how do we choose?
                    methodCall.methodInfo.methodResolution.get().overrides().stream()
                            .filter(m -> m.methodResolution.get().overrides().isEmpty()).findFirst()
                            .ifPresent(original -> builder.put(methodCall.methodInfo, original));
                }
            }
        });
        return expression.translate(builder.build());
    }

    /*
    set.isEmpty() => we add 0==set.size()
     */
    private static Expression replaceByAspectsWherePossible(EvaluationContext evaluationContext, Expression expression) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();

        expression.visit(e -> {
            VariableExpression ve;
            if (e instanceof MethodCall methodCall && ((ve = methodCall.object.asInstanceOf(VariableExpression.class)) != null)) {
                // the first thing we need to know is if this methodCall.methodInfo is involved in an aspect
                MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodCall.methodInfo);
                for (Map.Entry<CompanionMethodName, CompanionAnalysis> entry : methodAnalysis.getCompanionAnalyses().entrySet()) {
                    if (entry.getKey().action() == CompanionMethodName.Action.VALUE) {
                        CompanionMethodName companionMethodName = entry.getKey();
                        CompanionAnalysis companionAnalysis = entry.getValue();
                        Expression value = companionAnalysis.getValue();
                        if (companionMethodName.aspect() != null) {
                            LOGGER.debug("Found value expression {} for aspect {} for method call", value, companionMethodName.aspect());

                            TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(methodCall.methodInfo.typeInfo);
                            MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                            This thisVar = new This(InspectionProvider.DEFAULT, aspectMethod.typeInfo);
                            TranslationMap translationMap = new TranslationMapImpl.Builder()
                                    .put(thisVar, ve.variable()).build();
                            Expression translated = value.translate(translationMap);
                            builder.put(e, translated);
                        }
                    }
                }
            }
            return true;
        });
        return expression.translate(builder.build());
    }

    private static Expression enrichWithInvariants(EvaluationContext evaluationContext, Expression expression) {
        List<Expression> additionalComponents = new ArrayList<>();
        additionalComponents.add(expression);

        expression.visit(e -> {
            VariableExpression ve;
            if (e instanceof MethodCall methodCall && ((ve = methodCall.object.asInstanceOf(VariableExpression.class)) != null)) {
                // the first thing we need to know is if this methodCall.methodInfo is involved in an aspect
                TypeInfo typeInfo = methodCall.methodInfo.typeInfo;
                TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(typeInfo);
                for (MethodInfo aspectMain : typeAnalysis.getAspects().values()) {
                    MethodAnalysis aspectMainAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(aspectMain);
                    Optional<CompanionMethodName> oInvariant = aspectMainAnalysis.getCompanionAnalyses().keySet()
                            .stream().filter(cmn -> cmn.action() == CompanionMethodName.Action.INVARIANT).findFirst();
                    if (oInvariant.isPresent()) {
                        CompanionAnalysis companionAnalysis = aspectMainAnalysis.getCompanionAnalyses().get(oInvariant.get());
                        Expression invariant = companionAnalysis.getValue();
                        LOGGER.debug("Found invariant expression {} for method call", invariant);

                        TranslationMap translationMap = new TranslationMapImpl.Builder()
                                .put(new This(InspectionProvider.DEFAULT, aspectMain.typeInfo), ve.variable()).build();
                        Expression translated = invariant.translate(translationMap);
                        additionalComponents.add(translated);
                    }
                }
            }
            return true;
        });
        return And.and(evaluationContext, additionalComponents.toArray(Expression[]::new));
    }
}
