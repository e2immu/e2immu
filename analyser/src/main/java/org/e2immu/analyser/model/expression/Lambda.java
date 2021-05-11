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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Lambda implements Expression {
    public enum OutputVariant {
        TYPED, // (@Modified X x, Y y) -> ...
        VAR,   // (var x, @NotNull var y) -> ...
        EMPTY, // (x, y) -> ...
        ;

        public OutputBuilder output(ParameterInfo parameterInfo, Qualification qualification) {
            OutputBuilder ob = new OutputBuilder();
            if (this != EMPTY) {
                Stream<OutputBuilder> annotationStream = parameterInfo.buildAnnotationOutput(qualification);
                OutputBuilder annotationOutput = annotationStream
                        .collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT, Guide.generatorForAnnotationList()));
                if (this == TYPED) {
                    ob.add(parameterInfo.parameterizedType.output(qualification)).add(Space.ONE);
                }
                if (this == VAR) {
                    ob.add(new Text("var")).add(Space.ONE);
                }
                if(!annotationOutput.isEmpty()) {
                    return annotationOutput.add(Space.ONE_REQUIRED_EASY_SPLIT).add(ob);
                }
            }
            return ob;
        }
    }

    public final MethodInfo methodInfo;
    public final Block block;
    private final List<ParameterInfo> parameters;
    private final List<OutputVariant> parameterOutputVariants;
    public final ParameterizedType abstractFunctionalType;
    public final ParameterizedType implementation;

    /**
     * @param abstractFunctionalType e.g. java.util.Supplier
     * @param implementation         anonymous type, with single abstract method with implementation block
     */
    public Lambda(InspectionProvider inspectionProvider,
                  ParameterizedType abstractFunctionalType,
                  ParameterizedType implementation,
                  List<OutputVariant> outputVariants) {
        methodInfo = inspectionProvider.getTypeInspection(implementation.typeInfo).methods().get(0);
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        this.block = methodInspection.getMethodBody();
        this.parameters = methodInspection.getParameters();

        assert abstractFunctionalType.isFunctionalInterface(inspectionProvider);
        this.abstractFunctionalType = Objects.requireNonNull(abstractFunctionalType);
        assert implementsFunctionalInterface(inspectionProvider, implementation);
        this.implementation = Objects.requireNonNull(implementation);
        this.parameterOutputVariants = outputVariants;
        assert outputVariants.size() == parameters.size();
    }

    private static boolean implementsFunctionalInterface(InspectionProvider inspectionProvider,
                                                         ParameterizedType parameterizedType) {
        if (parameterizedType.typeInfo == null) return false;
        return inspectionProvider.getTypeInspection(parameterizedType.typeInfo)
                .interfacesImplemented().stream().anyMatch(pt -> pt.isFunctionalInterface(inspectionProvider));
    }

    @Override
    public TypeInfo definesType() {
        return methodInfo.typeInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lambda lambda = (Lambda) o;
        return abstractFunctionalType.equals(lambda.abstractFunctionalType) &&
                implementation.equals(lambda.implementation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(abstractFunctionalType, implementation);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        throw new UnsupportedOperationException();
        //return new Lambda(translationMap.translateType(abstractFunctionalType), translationMap.translateType(implementation));
    }

    @Override
    public int order() {
        return 0;
    }

    private Expression singleExpression() {
        if (block.structure.statements().size() != 1) return null;
        Statement statement = block.structure.statements().get(0);
        if (!(statement instanceof ReturnStatement returnStatement)) return null;
        return returnStatement.expression;
    }

    // this is a functional interface
    @Override
    public ParameterizedType returnType() {
        return abstractFunctionalType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (parameters.isEmpty()) {
            outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
        } else if (parameters.size() == 1 && parameterOutputVariants.get(0) == OutputVariant.EMPTY) {
            outputBuilder.add(parameters.get(0).output(qualification));
        } else {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(parameters.stream().map(pi -> parameterOutputVariants.get(pi.index)
                            .output(pi, qualification)
                            .add(pi.output(qualification)))
                            .collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        outputBuilder.add(Symbol.LAMBDA);

        Expression singleExpression = singleExpression();
        if (singleExpression != null) {
            outputBuilder.add(outputInParenthesis(qualification, precedence(), singleExpression));
        } else {
            Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
            outputBuilder.add(Symbol.LEFT_BRACE);
            outputBuilder.add(guideGenerator.start());
            StatementAnalysis firstStatement = methodInfo.methodAnalysis.get().getFirstStatement().followReplacements();
            Block.statementsString(qualification, outputBuilder, guideGenerator, firstStatement);
            outputBuilder.add(guideGenerator.end());
            outputBuilder.add(Symbol.RIGHT_BRACE);
        }
        return outputBuilder;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    // TODO: the "true" in the types referenced of the parameter is not necessarily true (probably even false!)
    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                block.typesReferenced(),
                parameters.stream().flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        ParameterizedType parameterizedType = methodInfo.typeInfo.asParameterizedType(evaluationContext.getAnalyserContext());
        Expression result;

        if (evaluationContext.getLocalPrimaryTypeAnalysers() == null) {
            result = DelayedExpression.forMethod(methodInfo);
        } else {
            MethodAnalysis methodAnalysis = evaluationContext.findMethodAnalysisOfLambda(methodInfo);
            if (methodInfo.hasReturnValue()) {
                Expression srv = methodAnalysis.getSingleReturnValue();
                if (srv != null) {
                    InlinedMethod inlineValue = srv.asInstanceOf(InlinedMethod.class);
                    result = Objects.requireNonNullElse(inlineValue, srv);
                } else {
                    result = DelayedExpression.forMethod(methodInfo);
                }
            } else {
                result = NewObject.forGetInstance(evaluationContext.newObjectIdentifier(),
                        parameterizedType, new BooleanConstant(evaluationContext.getPrimitives(), true),
                        MultiLevel.EFFECTIVELY_NOT_NULL);
            }
            builder.markVariablesFromSubMethod(methodAnalysis);
        }

        builder.setExpression(result);
        return builder.build();
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        return NewObject.forGetInstance(evaluationResult.evaluationContext().newObjectIdentifier(),
                evaluationResult.evaluationContext().getPrimitives(), returnType());
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(block);
    }

    // TODO should we add the parameters to variables() ??
}
