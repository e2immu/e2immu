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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.CompanionAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.COMPANION;
import static org.e2immu.analyser.util.Logger.log;

public class CompanionAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanionAnalyser.class);

    private final AnalyserContext analyserContext;
    public final MethodInfo mainMethod;
    public final MethodInfo companionMethod;
    public final CompanionMethodName companionMethodName;
    public final CompanionAnalysisImpl.Builder companionAnalysis;
    public final TypeAnalysis typeAnalysis;

    public CompanionAnalyser(AnalyserContext analyserContext,
                             TypeAnalysis typeAnalysis,
                             CompanionMethodName companionMethodName,
                             MethodInfo companionMethod,
                             MethodInfo mainMethod,
                             AnnotationParameters annotationParameters) {
        this.analyserContext = analyserContext;
        this.companionMethod = companionMethod;
        this.companionMethodName = companionMethodName;
        this.mainMethod = mainMethod;
        companionAnalysis = new CompanionAnalysisImpl.Builder(annotationParameters);
        this.typeAnalysis = typeAnalysis;
    }

    public AnalysisStatus analyse(int iteration) {
        try {
            if (companionMethodName.aspect() != null && !typeAnalysis.aspectsIsSet(companionMethodName.aspect())) {
                if (iteration == 0) {
                    return mainMethod.typeInfo.delay(CauseOfDelay.Cause.ASPECT);
                }
                throw new UnsupportedOperationException("Aspect function not found in type " + mainMethod.typeInfo.fullyQualifiedName);
            }
            if (CompanionMethodName.NO_CODE.contains(companionMethodName.action())) {
                // there is no code, and the type analyser deals with it
                companionAnalysis.value.set(EmptyExpression.EMPTY_EXPRESSION);
                return DONE;
            }
            DV modifyingMainMethod = analyserContext.getMethodAnalysis(mainMethod).getProperty(Property.MODIFIED_METHOD);
            if (modifyingMainMethod.isDelayed() && !mainMethod.isConstructor) {
                // even though the method itself is annotated by contract (it has no code), method analysis may be delayed because
                // its companion methods need processing
                return modifyingMainMethod.causesOfDelay();
            }
            computeRemapParameters(!mainMethod.isConstructor && modifyingMainMethod.valueIsTrue());

            ReturnStatement returnStatement = (ReturnStatement) companionMethod.methodInspection.get()
                    .getMethodBody().structure.statements().get(0);
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                    ConditionManager.initialConditionManager(analyserContext.getPrimitives()));
            EvaluationResult evaluationResult = returnStatement.expression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            if (evaluationResult.value().isDelayed()) {
                return companionMethod.delay(CauseOfDelay.Cause.VALUE);
            }
            companionAnalysis.value.set(evaluationResult.value());

            log(ANALYSER, "Finished companion analysis of {} in {}", companionMethodName, mainMethod.fullyQualifiedName());
            return DONE;
        } catch (RuntimeException e) {
            LOGGER.error("Caught runtime exception in companion analyser of {} of {}", companionMethodName, mainMethod.fullyQualifiedName());
            throw e;
        }
    }

    private void computeRemapParameters(boolean modifyingMainMethod) {
        int aspectVariables = companionMethodName.numAspectVariables(modifyingMainMethod);
        Map<String, Expression> remap = new HashMap<>();
        int numIndices = companionMethod.methodInspection.get().getParameters().size();
        int mainIndices = mainMethod.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>();
        for (ParameterInfo parameterInfo : companionMethod.methodInspection.get().getParameters()) {
            Expression value;
            if (aspectVariables >= 1 && parameterInfo.index == 0) {
                // this is the aspect as a method call
                MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                Expression scope = new VariableExpression(new This(analyserContext, aspectMethod.typeInfo));
                value = new MethodCall(Identifier.generate(), scope, aspectMethod, List.of());
            } else if (aspectVariables >= 2 && parameterInfo.index == 1) {
                // this is the initial aspect value in a Modification$Aspect
                MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                ParameterizedType returnType = aspectMethod.returnType();
                // the value that we store is the same as that for the post-variable (see previous if-statement)
                Expression scope = new VariableExpression(new This(analyserContext, aspectMethod.typeInfo));
                MethodCall methodValue = new MethodCall(Identifier.generate(), scope, aspectMethod, List.of());
                value = new VariableExpression(new PreAspectVariable(returnType, methodValue));
                companionAnalysis.preAspectVariableValue.set(value);
            } else {
                ParameterInfo parameterInMain = parameterInfo.index - aspectVariables < mainIndices ?
                        mainMethod.methodInspection.get().getParameters().get(parameterInfo.index - aspectVariables) : null;
                if (parameterInMain != null && parameterInfo.parameterizedType().equalsErased(parameterInMain.parameterizedType())) {
                    value = new VariableExpression(parameterInMain);
                } else if (parameterInfo.index == numIndices - 1 && !mainMethod.isVoid() &&
                        parameterInfo.parameterizedType().equalsErased(mainMethod.returnType())) {
                    value = new VariableExpression(new ReturnVariable(mainMethod));
                } else {
                    throw new UnsupportedOperationException("Cannot map parameter " + parameterInfo.index + " of " +
                            companionMethodName + " of " + mainMethod.fullyQualifiedName());
                }
                parameterValues.add(value);
            }
            remap.put(parameterInfo.name, value);
        }
        log(COMPANION, "Companion map for {} of {}: {}", companionMethodName, mainMethod.fullyQualifiedName(), remap);
        companionAnalysis.remapParameters.set(Map.copyOf(remap));
        companionAnalysis.parameterValues.set(parameterValues);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager, null);
        }

        @Override
        public Primitives getPrimitives() {
            return analyserContext.getPrimitives();
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public DV getPropertyFromPreviousOrInitial(Variable variable, Property property, int statementTime) {
            return property.falseDv; // but no delay, see Equals.checkParameter
        }

        @Override
        public Location getLocation() {
            return companionMethod.newLocation();
        }

        @Override
        public Location getLocation(Identifier identifier) {
            return new LocationImpl(companionMethod, identifier);
        }

        @Override
        public TypeInfo getCurrentType() {
            return mainMethod.typeInfo;
        }

        @Override
        public EvaluationContext child(Expression condition) {
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition, condition.causesOfDelay(),
                    Precondition.empty(getPrimitives()), CausesOfDelay.EMPTY);
            return new EvaluationContextImpl(iteration, cm);
        }

        @Override
        public EvaluationContext childState(Expression state) {
            ConditionManager cm = conditionManager.addState(state, state.causesOfDelay());
            return new EvaluationContextImpl(iteration, cm);
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            if (variable instanceof ParameterInfo parameterInfo) {
                Map<String, Expression> remapping = companionAnalysis.remapParameters.getOrDefaultNull();
                if (remapping == null)
                    return DelayedVariableExpression.forParameter(parameterInfo,
                            new SimpleSet(
                                    new VariableCause(variable, getLocation(), CauseOfDelay.Cause.REMAP_PARAMETER)));
                return Objects.requireNonNull(remapping.get(parameterInfo.name));
            }
            return new VariableExpression(variable);
        }

        @Override
        public boolean hasState(Expression expression) {
            if (expression instanceof VariableExpression) return false;
            return expression.hasState();
        }

        @Override
        public Expression state(Expression expression) {
            return expression.state();
        }
    }
}
