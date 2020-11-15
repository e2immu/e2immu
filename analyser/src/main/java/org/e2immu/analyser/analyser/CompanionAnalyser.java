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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.AnnotationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                             AnnotationType annotationType) {
        this.analyserContext = analyserContext;
        this.companionMethod = companionMethod;
        this.companionMethodName = companionMethodName;
        this.mainMethod = mainMethod;
        companionAnalysis = new CompanionAnalysisImpl.Builder(annotationType);
        this.typeAnalysis = typeAnalysis;
    }

    public AnalysisStatus analyse(int iteration) {
        try {
            if (companionMethodName.aspect() != null && !typeAnalysis.aspectsIsSet(companionMethodName.aspect())) {
                log(DELAYED, "Delaying companion analysis of {} of {}, aspect function not known",
                        companionMethodName, mainMethod.fullyQualifiedName());
                return AnalysisStatus.DELAYS;
            }
            computeRemapParameters();

            ReturnStatement returnStatement = (ReturnStatement) companionMethod.methodInspection.get()
                    .methodBody.get().structure.statements.get(0);
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, ConditionManager.INITIAL);
            EvaluationResult evaluationResult = returnStatement.expression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            if (evaluationResult.value == UnknownValue.NO_VALUE) {
                log(DELAYED, "Delaying companion analysis of {} of {}, delay in evaluation",
                        companionMethodName, mainMethod.fullyQualifiedName());
                return AnalysisStatus.DELAYS;
            }
            if (evaluationResult.value == null) {
                throw new RuntimeException("? have null result");
            }
            companionAnalysis.value.set(evaluationResult.value);

            log(ANALYSER, "Finished companion analysis of {} in {}", companionMethodName, mainMethod.fullyQualifiedName());
            return AnalysisStatus.DONE;
        } catch (RuntimeException e) {
            LOGGER.error("Caught runtime exception in companion analyser of {} of {}", companionMethodName, mainMethod.fullyQualifiedName());
            throw e;
        }
    }

    private void computeRemapParameters() {
        int aspectVariables = companionMethodName.numAspectVariables();
        ImmutableMap.Builder<String, Value> remap = new ImmutableMap.Builder<>();
        int numIndices = companionMethod.methodInspection.get().parameters.size();
        int mainIndices = mainMethod.methodInspection.get().parameters.size();
        for (ParameterInfo parameterInfo : companionMethod.methodInspection.get().parameters) {
            Value value;
            if (aspectVariables >= 1 && parameterInfo.index == 0) {
                // this is the aspect as a method call
                MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                Value scope = new VariableValue(new This(aspectMethod.typeInfo));
                value = new MethodValue(aspectMethod, scope, List.of(), ObjectFlow.NO_FLOW);
            } else if (aspectVariables >= 2 && parameterInfo.index == 1) {
                // this is the initial aspect value in a Modification$Aspect
                MethodInfo aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
                ParameterizedType returnType = aspectMethod.returnType();
                value = new VariableValue(new PreAspectVariable(returnType));
            } else {
                ParameterInfo parameterInMain = parameterInfo.index - aspectVariables < mainIndices ?
                        mainMethod.methodInspection.get().parameters.get(parameterInfo.index - aspectVariables) : null;
                if (parameterInMain != null && parameterInfo.parameterizedType().equalsErased(parameterInMain.parameterizedType())) {
                    value = new VariableValue(parameterInMain);
                } else if (parameterInfo.index == numIndices - 1 && !mainMethod.isVoid() &&
                        parameterInfo.concreteReturnType().equalsErased(mainMethod.returnType())) {
                    value = new VariableValue(new ReturnVariable(mainMethod));
                } else {
                    throw new UnsupportedOperationException("Cannot map parameter " + parameterInfo.index + " of " +
                            companionMethodName + " of " + mainMethod.fullyQualifiedName());
                }
            }
            remap.put(parameterInfo.name, value);
        }
        log(COMPANION, "Companion map for {} of {}: {}", companionMethodName, mainMethod.fullyQualifiedName(), remap);
        companionAnalysis.remapParameters.set(remap.build());
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {

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

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
        }

        @Override
        public Location getLocation() {
            return new Location(mainMethod);
        }

        @Override
        public TypeInfo getCurrentType() {
            return mainMethod.typeInfo;
        }

        @Override
        public Value currentValue(Variable variable) {
            if (variable instanceof ParameterInfo parameterInfo) {
                Map<String, Value> remapping = companionAnalysis.remapParameters.getOrElse(null);
                if (remapping == null) return UnknownValue.NO_VALUE; // delay!
                return Objects.requireNonNull(remapping.get(parameterInfo.name));
            }
            return new VariableValue(variable);
        }
    }
}
