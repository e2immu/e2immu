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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.COMPANION;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class CompanionAnalyser {

    public final MethodInfo mainMethod;
    public final MethodInfo companionMethod;
    public final CompanionMethodName companionMethodName;
    public final CompanionAnalysisImpl.Builder companionAnalysis;
    public final TypeAnalysisImpl.Builder typeAnalysis;

    public CompanionAnalyser(TypeAnalysisImpl.Builder typeAnalysis, CompanionMethodName companionMethodName, MethodInfo companionMethod, MethodInfo mainMethod) {
        this.companionMethod = companionMethod;
        this.companionMethodName = companionMethodName;
        this.mainMethod = mainMethod;
        companionAnalysis = new CompanionAnalysisImpl.Builder();
        this.typeAnalysis = typeAnalysis;
    }

    public AnalysisStatus analyse(int iteration) {
        if (companionMethodName.aspect() != null && !typeAnalysis.aspects.isSet(companionMethodName.aspect())) {
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
        companionAnalysis.value.set(evaluationResult.value);

        return AnalysisStatus.DONE;
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
                MethodInfo aspectMethod = typeAnalysis.aspects.get(companionMethodName.aspect());
                Value scope = new VariableValue(new This(aspectMethod.typeInfo));
                value = new MethodValue(aspectMethod, scope, List.of(), ObjectFlow.NO_FLOW);
            } else if (aspectVariables >= 2 && parameterInfo.index == 1) {
                // this is the initial aspect value in a Modification$Aspect
                MethodInfo aspectMethod = typeAnalysis.aspects.get(companionMethodName.aspect());
                ParameterizedType returnType = aspectMethod.returnType();
                value = new VariableValue(new PreAspectVariable(returnType));
            } else {
                ParameterInfo parameterInMain = parameterInfo.index + aspectVariables < mainIndices ?
                        mainMethod.methodInspection.get().parameters.get(parameterInfo.index + aspectVariables) : null;
                if (parameterInMain != null && parameterInfo.name.equals(parameterInMain.name)) {
                    value = new VariableValue(parameterInMain);
                } else if (parameterInfo.index == numIndices - 1 && !mainMethod.isVoid() &&
                        parameterInfo.concreteReturnType().equals(mainMethod.returnType())) {
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


        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
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
