/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.config.CompanionAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
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
                    log(DELAYED, "Delaying companion analysis of {} of {}, aspect function not known",
                            companionMethodName, mainMethod.fullyQualifiedName());
                    visit(iteration, DELAYS, null, null);
                    return AnalysisStatus.DELAYS;
                }
                throw new UnsupportedOperationException("Aspect function not found in type " + mainMethod.typeInfo.fullyQualifiedName);
            }
            if (CompanionMethodName.NO_CODE.contains(companionMethodName.action())) {
                // there is no code, and the type analyser deals with it
                companionAnalysis.value.set(EmptyExpression.EMPTY_EXPRESSION);
                visit(iteration, DONE, null, null);
                return DONE;
            }
            int modifyingMainMethod = analyserContext.getMethodAnalysis(mainMethod).getProperty(VariableProperty.MODIFIED);
            if (modifyingMainMethod == Level.DELAY) {
                // even though the method itself is annotated by contract (it has no code), method analysis may be delayed because
                // its companion methods need processing
                log(DELAYED, "Delaying companion analysis of {} of {}, modification of main method delayed",
                        companionMethodName, mainMethod.fullyQualifiedName());
                visit(iteration, DELAYS, null, null);
                return AnalysisStatus.DELAYS;
            }
            computeRemapParameters(!mainMethod.isConstructor && modifyingMainMethod == Level.TRUE);

            ReturnStatement returnStatement = (ReturnStatement) companionMethod.methodInspection.get()
                    .getMethodBody().structure.statements.get(0);
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, ConditionManager.INITIAL);
            EvaluationResult evaluationResult = returnStatement.expression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            if (evaluationResult.value == EmptyExpression.NO_VALUE) {
                log(DELAYED, "Delaying companion analysis of {} of {}, delay in evaluation",
                        companionMethodName, mainMethod.fullyQualifiedName());
                visit(iteration, DELAYS, evaluationContext, evaluationResult);
                return AnalysisStatus.DELAYS;
            }
            if (evaluationResult.value == null) {
                throw new RuntimeException("? have null result");
            }
            companionAnalysis.value.set(evaluationResult.value);

            log(ANALYSER, "Finished companion analysis of {} in {}", companionMethodName, mainMethod.fullyQualifiedName());
            visit(iteration, DONE, evaluationContext, evaluationResult);
            return DONE;
        } catch (RuntimeException e) {
            LOGGER.error("Caught runtime exception in companion analyser of {} of {}", companionMethodName, mainMethod.fullyQualifiedName());
            throw e;
        }
    }

    private void visit(int iteration, AnalysisStatus analysisStatus, EvaluationContext evaluationContext, EvaluationResult evaluationResult) {
        for (CompanionAnalyserVisitor companionAnalyserVisitor : analyserContext.getConfiguration().debugConfiguration.afterCompanionAnalyserVisitors) {
            companionAnalyserVisitor.visit(new CompanionAnalyserVisitor.Data(iteration, analysisStatus, evaluationContext, evaluationResult,
                    mainMethod, companionMethodName, companionMethod, companionAnalysis));
        }
    }

    private void computeRemapParameters(boolean modifyingMainMethod) {
        int aspectVariables = companionMethodName.numAspectVariables(modifyingMainMethod);
        ImmutableMap.Builder<String, Expression> remap = new ImmutableMap.Builder<>();
        int numIndices = companionMethod.methodInspection.get().getParameters().size();
        int mainIndices = mainMethod.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>();
        for (ParameterInfo parameterInfo : companionMethod.methodInspection.get().getParameters()) {
            Expression value;
            if (aspectVariables >= 1 && parameterInfo.index == 0) {
                // this is the aspect as a method call
                MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                Expression scope = new VariableExpression(new This(analyserContext, aspectMethod.typeInfo));
                value = new MethodCall(aspectMethod, scope, List.of(), ObjectFlow.NO_FLOW);
            } else if (aspectVariables >= 2 && parameterInfo.index == 1) {
                // this is the initial aspect value in a Modification$Aspect
                MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                ParameterizedType returnType = aspectMethod.returnType();
                // the value that we store is the same as that for the post-variable (see previous if-statement)
                Expression scope = new VariableExpression(new This(analyserContext, aspectMethod.typeInfo));
                MethodCall methodValue = new MethodValue(aspectMethod, scope, List.of(), ObjectFlow.NO_FLOW);
                value = new VariableExpression(new PreAspectVariable(returnType, methodValue));
                companionAnalysis.preAspectVariableValue.set(value);
            } else {
                ParameterInfo parameterInMain = parameterInfo.index - aspectVariables < mainIndices ?
                        mainMethod.methodInspection.get().getParameters().get(parameterInfo.index - aspectVariables) : null;
                if (parameterInMain != null && parameterInfo.parameterizedType().equalsErased(parameterInMain.parameterizedType())) {
                    value = new VariableExpression(parameterInMain);
                } else if (parameterInfo.index == numIndices - 1 && !mainMethod.isVoid() &&
                        parameterInfo.concreteReturnType().equalsErased(mainMethod.returnType())) {
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
        companionAnalysis.remapParameters.set(remap.build());
        companionAnalysis.parameterValues.set(parameterValues);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
        }

        @Override
        public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
            MethodAnalysis methodAnalysis = getMethodAnalysis(parameterInfo.owner);
            return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
            //return getAnalyserContext().getParameterAnalysis(parameterInfo);
        }

        @Override
        public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
            return getAnalyserContext().getMethodAnalysis(methodInfo);
        }

        @Override
        public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
            return getAnalyserContext().getTypeAnalysis(typeInfo);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Location getLocation() {
            return new Location(companionMethod);
        }

        @Override
        public Location getLocation(Expression expression) {
            return new Location(companionMethod);
        }

        @Override
        public TypeInfo getCurrentType() {
            return mainMethod.typeInfo;
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return new EvaluationContextImpl(iteration, conditionManager.addCondition(this, condition));
        }

        @Override
        public Expression currentValue(Variable variable) {
            if (variable instanceof ParameterInfo parameterInfo) {
                Map<String, Expression> remapping = companionAnalysis.remapParameters.getOrElse(null);
                if (remapping == null) return EmptyExpression.NO_VALUE; // delay!
                return Objects.requireNonNull(remapping.get(parameterInfo.name));
            }
            return new VariableExpression(variable);
        }

        @Override
        public Set<Variable> linkedVariables(Expression value) {
            return Set.of(); // not interested
        }
    }
}
