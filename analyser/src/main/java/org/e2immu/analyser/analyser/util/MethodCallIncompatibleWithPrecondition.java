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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.COMPANION;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class MethodCallIncompatibleWithPrecondition {
    /*
    look into the latest value of the fields
    they must have instance state that is incompatible with the precondition.

    return null upon delays.
     */
    public static Boolean isMark(EvaluationContext evaluationContext,
                                 Set<FieldInfo> fields,
                                 MethodAnalyser methodAnalyser) {
        StatementAnalysis statementAnalysis = methodAnalyser.methodAnalysis.getLastStatement();
        assert statementAnalysis.methodLevelData.combinedPrecondition.isFinal();
        Expression precondition = statementAnalysis.methodLevelData.combinedPrecondition.get().expression();
        Expression preconditionInTermsOfAspect = replaceByAspectsWherePossible(evaluationContext, precondition);

        // IMPROVE add stateData.conditionManagerForNextStatement.state to this
        for (FieldInfo fieldInfo : fields) {
            FieldReference fieldReference = new FieldReference(InspectionProvider.DEFAULT,
                    fieldInfo, new This(InspectionProvider.DEFAULT, methodAnalyser.methodInfo.typeInfo));
            VariableInfo variableInfo = statementAnalysis.findOrNull(fieldReference, VariableInfoContainer.Level.MERGE);
            if (!variableInfo.valueIsSet()) {
                log(DELAYED, "Delaying isMark, no value for field {} in last statement of {}",
                        fieldInfo.name, methodAnalyser.methodInfo.fullyQualifiedName);
                return null;
            }
            if (variableInfo.getValue() instanceof NewObject newObject) {
                Expression state = newObject.stateTranslateThisTo(fieldReference);
                if (!state.isBoolValueTrue()) {
                    Expression stateWithInvariants = enrichWithInvariants(evaluationContext, state);

                    /* Another issue that we have to deal with is that of overloaded methods
                       the precondition contains references to Set.size() while the invariants have been
                       computed on Collection.size()

                       We have to normalise, and cannot easily do that at equality level
                     */
                    Expression normalisedPrecondition = normaliseMethods(evaluationContext, preconditionInTermsOfAspect);
                    Expression normalisedStateWithInvariants = normaliseMethods(evaluationContext, stateWithInvariants);
                    Expression and = new And(evaluationContext.getPrimitives()).append(evaluationContext,
                            normalisedPrecondition, normalisedStateWithInvariants);
                    if (and.isBoolValueFalse()) return true;
                }
            }
        }
        return false;
    }

    private static Expression normaliseMethods(EvaluationContext evaluationContext, Expression expression) {
        TranslationMap.TranslationMapBuilder builder = new TranslationMap.TranslationMapBuilder();
        expression.visit(e -> {
            if (e instanceof MethodCall methodCall && !builder.translateMethod(methodCall.methodInfo)) {
                MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodCall.methodInfo);
                if (methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) == Level.FALSE) {
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
        TranslationMap.TranslationMapBuilder builder = new TranslationMap.TranslationMapBuilder();

        expression.visit(e -> {
            if (e instanceof MethodCall methodCall && methodCall.object instanceof VariableExpression ve) {
                // the first thing we need to know is if this methodCall.methodInfo is involved in an aspect
                MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodCall.methodInfo);
                for (Map.Entry<CompanionMethodName, CompanionAnalysis> entry : methodAnalysis.getCompanionAnalyses().entrySet()) {
                    if (entry.getKey().action() == CompanionMethodName.Action.VALUE) {
                        CompanionMethodName companionMethodName = entry.getKey();
                        CompanionAnalysis companionAnalysis = entry.getValue();
                        Expression value = companionAnalysis.getValue();
                        log(COMPANION, "Found value expression {} for aspect {} for method call", value, companionMethodName.aspect());

                        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(methodCall.methodInfo.typeInfo);
                        MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                        This thisVar = new This(InspectionProvider.DEFAULT, aspectMethod.typeInfo);
                        TranslationMap translationMap = new TranslationMap.TranslationMapBuilder()
                                .put(thisVar, ve.variable()).build();
                        Expression translated = value.translate(translationMap);
                        builder.put(e, translated);
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
            if (e instanceof MethodCall methodCall && methodCall.object instanceof VariableExpression ve) {
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
                        log(COMPANION, "Found invariant expression {} for method call", invariant);

                        TranslationMap translationMap = new TranslationMap.TranslationMapBuilder()
                                .put(new This(InspectionProvider.DEFAULT, aspectMain.typeInfo), ve.variable()).build();
                        Expression translated = invariant.translate(translationMap);
                        additionalComponents.add(translated);
                    }
                }
            }
            return true;
        });
        return new And(evaluationContext.getPrimitives()).append(evaluationContext,
                additionalComponents.toArray(Expression[]::new));
    }
}
