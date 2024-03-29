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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Lambda extends BaseExpression implements Expression {

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
                if (!annotationOutput.isEmpty()) {
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
    // IntFunction<? extends T>
    public final ParameterizedType abstractFunctionalType;
    // ownerTypeOfTheMethodWhereTheLambdaIsDefined.$1
    public final ParameterizedType implementation;
    // String (concrete for the T in the IntFunction)
    public final ParameterizedType concreteReturnType;

    /**
     * @param abstractFunctionalType e.g. java.util.Supplier
     * @param implementation         anonymous type, with single abstract method with implementation block
     */
    public Lambda(Identifier identifier,
                  InspectionProvider inspectionProvider,
                  ParameterizedType abstractFunctionalType,
                  ParameterizedType implementation,
                  ParameterizedType concreteReturnType,
                  List<OutputVariant> outputVariants) {
        super(identifier);
        methodInfo = inspectionProvider.getTypeInspection(implementation.typeInfo).methods().get(0);
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        this.block = methodInspection.getMethodBody();
        this.parameters = methodInspection.getParameters();

        assert abstractFunctionalType.isFunctionalInterface(inspectionProvider);
        this.abstractFunctionalType = Objects.requireNonNull(abstractFunctionalType);
        assert implementsFunctionalInterface(inspectionProvider, implementation);
        this.implementation = Objects.requireNonNull(implementation);
        this.concreteReturnType = Objects.requireNonNull(concreteReturnType);
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return this; // translation will be used by DelayedExpression, we cannot simply throw an exception
        //throw new UnsupportedOperationException();
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

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            Expression single = singleExpression();
            if (single != null) single.visit(predicate);
        }
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
            if (methodInfo.methodAnalysis.isSet()) {
                outputBuilder.add(guideGenerator.start());
                StatementAnalysis firstStatement = methodInfo.methodAnalysis.get().getFirstStatement().followReplacements();
                Block.statementsString(qualification, outputBuilder, guideGenerator, firstStatement);
                outputBuilder.add(guideGenerator.end());
            } else {
                outputBuilder.add(new Text("... debugging ..."));
            }
            outputBuilder.add(Symbol.RIGHT_BRACE);
        }
        return outputBuilder;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        // gets called by the computation of links between parameters in MethodCall
        return property.falseDv; // maybe some values could be improved, but not relevant a t m
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(block.typesReferenced(),
                parameters.stream()
                        .flatMap(p -> p.typesReferenced(parameterOutputVariants.get(p.index) == OutputVariant.TYPED).stream())
                        .collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        ParameterizedType parameterizedType = methodInfo.typeInfo.asParameterizedType(context.getAnalyserContext());
        Expression result;

        if (context.evaluationContext().getLocalPrimaryTypeAnalysers() == null) {
            result = noLocalAnalysersYet(context);
        } else {
            MethodAnalysis methodAnalysis = context.evaluationContext().findMethodAnalysisOfLambda(methodInfo);
            boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
            boolean recursiveCall = MethodCall.recursiveCall(methodInfo, context.evaluationContext());
            boolean firstInCycle = breakCallCycleDelay || recursiveCall;
            if (firstInCycle) {
                result = makeInstance(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            } else {
                result = withLocalAnalyser(parameterizedType, methodAnalysis);
            }
        }

        builder.setExpression(result);
        return builder.build();
    }

    private Expression withLocalAnalyser(ParameterizedType parameterizedType, MethodAnalysis methodAnalysis) {
        Expression result;
        if (methodInfo.hasReturnValue()) {
            Expression srv = methodAnalysis.getSingleReturnValue();
            DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
            DV nneParam;
            if (methodAnalysis.getParameterAnalyses().isEmpty()) {
                // supplier
                nneParam = methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION);
            } else {
                // function
                nneParam = methodAnalysis.getParameterAnalyses()
                        .stream().map(pa -> pa.getProperty(Property.NOT_NULL_PARAMETER)).reduce(DV.MAX_INT_DV, DV::min);
                assert nneParam != DV.MAX_INT_DV;
            }
            if (srv.isDone() && modified.isDone() && nneParam.isDone()) {
                if (modified.valueIsFalse()) {
                    result = srv;
                    assert result instanceof InlinedMethod || result.isConstant();
                } else {
                    // modifying method, we cannot simply substitute
                    DV nne = MultiLevel.composeOneLevelMoreNotNull(nneParam);
                    assert nne.isDone();
                    result = makeInstance(parameterizedType, nne);
                }
            } else {
                CausesOfDelay causes = srv.causesOfDelay().merge(modified.causesOfDelay()).merge(nneParam.causesOfDelay());
                result = DelayedExpression.forMethod(identifier, methodInfo, implementation, this, causes, Map.of());
            }
        } else {
            // the lambda
            result = makeInstance(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        }
        return result;
    }

    private Expression makeInstance(ParameterizedType parameterizedType, DV nne) {
        Expression result;
        Properties valueProperties = Properties.of(Map.of(Property.NOT_NULL_EXPRESSION, nne,
                Property.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
                Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV,
                Property.CONTAINER, MultiLevel.CONTAINER_DV,
                Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv,
                Property.IDENTITY, Property.IDENTITY.falseDv));
        result = Instance.forGetInstance(identifier, parameterizedType, valueProperties);
        return result;
    }

    private Expression noLocalAnalysersYet(EvaluationResult evaluationContext) {
        CausesOfDelay delay = DelayFactory.createDelay(evaluationContext.getCurrentType(), CauseOfDelay.Cause.TYPE_ANALYSIS);
        return DelayedExpression.forMethod(identifier, methodInfo, implementation, this, delay, Map.of());
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(block);
    }

    // TODO should we add the parameters to variables() ??

    public ParameterizedType concreteReturnType() {
        return concreteReturnType;
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        /*
        do not return the variables of this block (goes via general method, subElements...)
        e.g. Expressions_0.extractEquals, pattern variable eq comes out of the Lambda into the enclosing method

        wait until the Lambda has become an InlinedMethod if you want access to the variables (see e.g. E2ImmutableComposition_0)
         */
        return List.of();
    }
}
