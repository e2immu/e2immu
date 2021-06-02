package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.support.SetOnce;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class AggregatingMethodAnalyser extends MethodAnalyser {

    public static final String MODIFIED = "modified";
    public static final String IMMUTABLE = "immutable";
    public static final String INDEPENDENT = "independent";
    public static final String FLUENT = "fluent";
    public static final String IDENTITY = "identity";
    public static final String NOT_NULL = "notNull";
    public static final String METHOD_VALUE = "methodValue";
    private final SetOnce<List<MethodAnalysis>> implementingAnalyses = new SetOnce<>();
    private final AnalyserComponents<String, Integer> analyserComponents;

    AggregatingMethodAnalyser(MethodInfo methodInfo,
                              MethodAnalysisImpl.Builder methodAnalysis,
                              List<? extends ParameterAnalyser> parameterAnalysers,
                              List<ParameterAnalysis> parameterAnalyses,
                              AnalyserContext analyserContextInput) {
        super(methodInfo, methodAnalysis, parameterAnalysers,
                parameterAnalyses, Map.of(), false, analyserContextInput);
        assert methodAnalysis.analysisMode == Analysis.AnalysisMode.AGGREGATED;

        // TODO improve!
        methodAnalysis.precondition.set(Precondition.empty(analyserContextInput.getPrimitives()));
        methodAnalysis.preconditionForEventual.set(Optional.empty());
        methodAnalysis.setEventual(MethodAnalysis.NOT_EVENTUAL);

        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>()
                .add(MODIFIED, iteration -> this.aggregate(VariableProperty.MODIFIED_METHOD, VariableInfoImpl.MAX))
                .add(IMMUTABLE, iteration -> this.aggregate(VariableProperty.IMMUTABLE, VariableInfoImpl.MIN))
                .add(INDEPENDENT, iteration -> this.aggregate(VariableProperty.INDEPENDENT, VariableInfoImpl.MIN))
                .add(FLUENT, iteration -> this.aggregate(VariableProperty.FLUENT, VariableInfoImpl.MIN))
                .add(IDENTITY, iteration -> this.aggregate(VariableProperty.IDENTITY, VariableInfoImpl.MIN))
                .add(NOT_NULL, iteration -> this.aggregate(VariableProperty.NOT_NULL_EXPRESSION, VariableInfoImpl.MIN))
                .add(METHOD_VALUE, iteration -> this.aggregateMethodValue());

        analyserComponents = builder.build();
    }

    @Override
    public void initialize() {
        Stream<MethodInfo> implementations = obtainImplementingTypes().map(ti -> ti.findMethodImplementing(methodInfo));
        List<MethodAnalysis> analysers = implementations.map(analyserContext::getMethodAnalysis).toList();
        implementingAnalyses.set(analysers);
    }

    private Stream<TypeInfo> obtainImplementingTypes() {
        TypeInspection myTypeInspection = methodInfo.typeInfo.typeInspection.get();
        if (myTypeInspection.isSealed()) {
            return myTypeInspection.permittedWhenSealed().stream();
        }
        TypeInfo generated = methodInfo.typeInfo.typeResolution.get().generatedImplementation();
        assert generated != null : methodInfo.fullyQualifiedName
                + " does not belong to a sealed class, so it must have a unique generated implementation";
        return Stream.of(generated);
    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        AnalysisStatus analysisStatus = analyserComponents.run(iteration);
        List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                .debugConfiguration().afterMethodAnalyserVisitors();
        if (!visitors.isEmpty()) {
            for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                        null, methodInfo, methodAnalysis,
                        parameterAnalyses, analyserComponents.getStatusesAsMap(),
                        this::getMessageStream));
            }
        }
        return analysisStatus;
    }

    private AnalysisStatus aggregateMethodValue() {
        if (!methodAnalysis.singleReturnValue.isSet()) {
            if (implementingAnalyses.get().stream().anyMatch(a -> a.getSingleReturnValue() == null)) {
                return DELAYS;
            }
            Expression singleValue = implementingAnalyses.get().stream().map(MethodAnalysis::getSingleReturnValue).findFirst().orElseThrow();
            // unless it is a constant, a parameter of the method, or statically assigned to a constructor (?) we can't do much
            Expression value;
            if(singleValue.isConstant()) {
                value = singleValue;
            } else {
                // TODO implement other cases, such as parameter values
                value = new UnknownExpression(methodInfo.returnType(), "interface method");
            }
            methodAnalysis.singleReturnValue.set(value);
            log(ANALYSER, "Set single value of {} to aggregate {}", methodInfo.fullyQualifiedName, singleValue);
        }
        return DONE;
    }

    private AnalysisStatus aggregate(VariableProperty variableProperty, IntBinaryOperator operator) {
        int current = methodAnalysis.getProperty(variableProperty);
        if (current == Level.DELAY) {
            int identity = operator == VariableInfoImpl.MIN ? variableProperty.best : variableProperty.falseValue;
            int value = implementingAnalyses.get().stream()
                    .mapToInt(a -> a.getProperty(variableProperty))
                    .reduce(identity, operator);
            if (value == Level.DELAY) {
                log(DELAYED, "Delaying aggregate of {} for {}", variableProperty, methodInfo.fullyQualifiedName);
                assert translatedDelay("AGG:" + variableProperty,
                        implementingAnalyses.get().stream().filter(a -> a.getProperty(variableProperty) == Level.DELAY)
                                .findFirst().orElseThrow().getMethodInfo().fullyQualifiedName + "." + variableProperty.name(),
                        methodInfo.fullyQualifiedName + "." + variableProperty.name());

                return DELAYS;
            }
            log(ANALYSER, "Set aggregate of {} to {} for {}", variableProperty, value, methodInfo.fullyQualifiedName);
            methodAnalysis.setProperty(variableProperty, value);
        }
        return DONE;
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        return Stream.of();
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        return null;
    }

    @Override
    public void logAnalysisStatuses() {
        // nothing
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return analyserComponents;
    }

    @Override
    public void makeImmutable() {
        // nothing
    }

    @Override
    protected String where(String componentName) {
        return methodInfo.fullyQualifiedName + ":AGG:" + componentName;
    }

    @Override
    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean b) {
        return List.of();
    }
}
