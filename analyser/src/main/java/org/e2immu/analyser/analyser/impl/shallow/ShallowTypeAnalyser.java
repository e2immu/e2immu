package org.e2immu.analyser.analyser.impl.shallow;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.TypeAnalyserImpl;
import org.e2immu.analyser.analyser.impl.computing.ComputingTypeAnalyser;
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShallowTypeAnalyser extends TypeAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowTypeAnalyser.class);

    public ShallowTypeAnalyser(TypeInfo typeInfo,
                               TypeInfo primaryType,
                               AnalyserContext analyserContextInput) {
        super(typeInfo, primaryType, analyserContextInput, Analysis.AnalysisMode.CONTRACTED);
    }

    @Override
    public void initialize() {

    }

    /*
    Either the user has hinted a value, or simpleComputeIndependent has provided one.
    This value then affects the independence values of methods and parameters.
    The end result is computed again using computeIndependent.

    If the computed value differs from the initial one, we raise an error.
    If the computed value is HIGHER, we have made a programming error
    If the computed value is LOWER, the user should put a higher one
    */
    @Override
    public void check() {
        try {
            DV inMap = typeAnalysis.getPropertyFromMapNeverDelay(Property.INDEPENDENT);
            ValueExplanation computed = computeIndependent(typeInfo, typeAnalysis);
            if (computed.value.isDone()) {
                if (!inMap.equals(computed.value)) {
                    if (typeInfo.typeInspection.get().isPublic()
                            && Input.acceptFQN(typeInfo.fullyQualifiedName)
                            && independenceIsNotContracted(typeInfo)) {
                        analyserResultBuilder.add(Message.newMessage(typeInfo.newLocation(),
                                Message.Label.TYPE_HAS_DIFFERENT_VALUE_FOR_INDEPENDENT,
                                "Found " + inMap + ", computed " + computed.value
                                        + " in " + computed.explanation));
                    }
                }
                analyserResultBuilder.addMessages(computed.messages.stream());
            } // else: we're at the edge of the known/analysed types, we're not exploring further and rely on the value
        } catch (IllegalStateException ise) {
            LOGGER.error("Caught exception while validating independence of {}", typeInfo);
            throw ise;
        }
    }

    /*
    an alternative would be to allow the transition from HC=true to HC=false when the type parameters disappear
    e.g. PrimitiveIterator.OfInt has no type parameters, even if Iterator has them
     */
    private boolean independenceIsNotContracted(TypeInfo typeInfo) {
        AnnotationExpression independent = analyserContext.getE2ImmuAnnotationExpressions().independent;
        Optional<AnnotationExpression> opt = typeInfo.hasInspectedAnnotation(independent);
        return opt.stream().noneMatch(ae -> ae.e2ImmuAnnotationParameters().contract());
    }

    @Override
    public AnalyserResult analyse(SharedState sharedState) {
        try {
            shallowAnalyzer();
            if (!analyserContext.inAnnotatedAPIAnalysis()) {
                for (TypeAnalyserVisitor typeAnalyserVisitor : analyserContext.getConfiguration()
                        .debugConfiguration().afterTypePropertyComputations()) {
                    typeAnalyserVisitor.visit(new TypeAnalyserVisitor.Data(0, sharedState.breakDelayLevel(),
                            analyserContext.getPrimitives(),
                            typeInfo,
                            analyserContext.getTypeInspection(typeInfo),
                            typeAnalysis,
                            Map.of(),
                            analyserContext));
                }
            }
            analyserResultBuilder.setAnalysisStatus(AnalysisStatus.DONE);
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception in ShallowTypeAnalyser {}", typeInfo.fullyQualifiedName);
            throw rte;
        }
    }

    private void shallowAnalyzer() {
        LOGGER.debug("Shallow type analyser on {}", typeInfo.fullyQualifiedName);
        TypeInspection typeInspection = typeInfo.typeInspection.get();
        Analyser.AnalyserIdentification identification = typeInfo.isAbstract()
                ? Analyser.AnalyserIdentification.ABSTRACT_TYPE
                : Analyser.AnalyserIdentification.TYPE;

        analyserResultBuilder.addMessages(typeAnalysis.fromAnnotationsIntoProperties(identification,
                true, typeInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions()));

        ComputingTypeAnalyser.findAspects(typeAnalysis, typeInfo);
        LOGGER.debug("Found aspects {}",
                typeAnalysis.aspects.stream().map(e -> e.getKey() + ": " + e.getValue().fullyQualifiedName)
                        .collect(Collectors.joining(", ")));
        typeAnalysis.freezeApprovedPreconditionsFinalFields();
        typeAnalysis.freezeApprovedPreconditionsImmutable();

        /*
        The computation of hidden content types proceeds as follows:
        1. all unbound type parameters are hidden content

        IMPROVE currently not implementing 2.
        2. to the hidden content we add all public field types, method return types and method parameter types
           that are immutable with hidden content.

        This computation does not differentiate between interfaces (which provide a specification only) and classes
        which provide specification and implementation: we cannot see inside the class anyway in this analyser.
         */
        Set<ParameterizedType> typeParametersAsParameterizedTypes = typeInspection.typeParameters().stream()
                .filter(TypeParameter::isUnbound)
                .map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toSet());
        SetOfTypes hiddenContentTypes = new SetOfTypes(typeParametersAsParameterizedTypes);
        typeAnalysis.setHiddenContentTypes(hiddenContentTypes);

        ensureImmutableAndContainer();
        List<Message> messages = simpleComputeIndependent(analyserContext, typeAnalysis, this::isAccessible);
        analyserResultBuilder.addMessages(messages.stream());
        computeImmutableDeterminedByTypeParameters(typeInspection, typeAnalysis);
    }

    private boolean isAccessible(MethodInfo methodInfo) {
        MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);
        return analyserContext.inAnnotatedAPIAnalysis()
                ? methodInspection.isPubliclyAccessible(analyserContext)
                : !methodInspection.isPrivate();
    }

    private void ensureImmutableAndContainer() {
        DV immutable = typeAnalysis.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        if (immutable.isDelayed()) {
            typeAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.MUTABLE_DV);
        }
        DV container = typeAnalysis.getPropertyFromMapDelayWhenAbsent(Property.CONTAINER);
        if (container.isDelayed()) {
            typeAnalysis.setProperty(Property.CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        }
    }

    private void computeImmutableDeterminedByTypeParameters(TypeInspection typeInspection,
                                                            TypeAnalysisImpl.Builder typeAnalysisBuilder) {
        if (typeAnalysisBuilder.immutableDeterminedByTypeParameters().isDelayed()) {
            boolean res = typeInspection.typeParameters().stream()
                    .anyMatch(tp -> Boolean.TRUE != tp.isAnnotatedWithIndependent());
            typeAnalysisBuilder.setImmutableDeterminedByTypeParameters(res);
        }
    }

    /*
    relations to super-type:

    if the super-type is @Independent with hidden content, we can go anywhere (e.g. Serializable)
    if the super-type is @Independent without hidden content, we can go anywhere (minimum works well)
    if the super-type is @Dependent, we must have dependent
     */

    private record ValueExplanation(DV value, String explanation, List<Message> messages) {
    }

    private ValueExplanation computeIndependent(TypeInfo typeInfo, TypeAnalysis typeAnalysis) {
        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
            return new ValueExplanation(MultiLevel.INDEPENDENT_DV, "immutable", List.of());
        }
        Stream<ValueExplanation> methodStream = typeInfo.typeInspection.get()
                .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodInspection.get().isPubliclyAccessible())
                .map(m -> new ValueExplanation(analyserContext.getMethodAnalysis(m).getProperty(Property.INDEPENDENT),
                        "Method " + m.fullyQualifiedName, List.of()));
        Stream<ValueExplanation> parameterStream = typeInfo.typeInspection.get()
                .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodInspection.get().isPubliclyAccessible())
                .flatMap(m -> analyserContext.getMethodAnalysis(m).getParameterAnalyses().stream())
                .map(p -> new ValueExplanation(p.getProperty(Property.INDEPENDENT),
                        "Parameter " + p.getParameterInfo().fullyQualifiedName, List.of()));
        ValueExplanation myMethods =
                Stream.concat(methodStream, parameterStream)
                        .min(Comparator.comparing(p -> p.value.value()))
                        .orElse(new ValueExplanation(Property.INDEPENDENT.bestDv, "'no methods'", List.of()));

        Stream<TypeInfo> superTypes = typeInfo.typeResolution.get().superTypesExcludingJavaLangObject()
                .stream();
        List<Message> messages = new ArrayList<>();
        ValueExplanation fromSuperTypes = superTypes
                .filter(t -> t.typeInspection.get().isPublic())
                .map(t -> {
                    TypeAnalysis ta = analyserContext.getTypeAnalysisNullWhenAbsent(t);
                    if (ta == null) {
                        messages.add(Message.newMessage(t.newLocation(), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE));
                    }
                    return ta;
                })
                .filter(Objects::nonNull)
                .map(ta -> new ValueExplanation(ta.getProperty(Property.INDEPENDENT), "Type " + ta, messages))
                .min(Comparator.comparing(p -> p.value.value()))
                .orElse(new ValueExplanation(Property.INDEPENDENT.bestDv, "'no supertypes'", messages));
        return myMethods.value.le(fromSuperTypes.value) ? myMethods : fromSuperTypes;
    }

    /*
     In some situations, the INDEPENDENT value is easy to compute.
     Because we have a chicken-and-egg problem (the independent value can be computed from the methods, but the
     parameters may require an independent value, it is better to assign a value when obviously possible.
     */
    public static List<Message> simpleComputeIndependent(AnalysisProvider analysisProvider,
                                                         TypeAnalysisImpl.Builder builder,
                                                         Predicate<MethodInfo> isAccessible) {
        DV immutable = builder.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        DV independent = MultiLevel.independentCorrespondingToImmutableLevelDv(MultiLevel.level(immutable));
        List<Message> messages = new ArrayList<>();
        if (inMap.isDelayed()) {
            if (immutable.ge(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV)) {
                // minimal value; we'd have an inconsistency otherwise
                builder.setProperty(Property.INDEPENDENT, independent);
                return List.of();
            }
            boolean allMethodsOnlyPrimitives =
                    builder.getTypeInfo().typeInspection.get()
                            .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                            .filter(isAccessible)
                            .allMatch(m -> (m.isConstructor || m.isVoid() || m.returnType().isPrimitiveStringClass())
                                    && m.methodInspection.get().getParameters().stream()
                                    .allMatch(p -> p.parameterizedType.isPrimitiveStringClass()));
            if (allMethodsOnlyPrimitives) {
                Stream<TypeInfo> superTypes = builder.typeInfo.typeResolution.get().superTypesExcludingJavaLangObject()
                        .stream();
                DV fromSuperTypes = superTypes
                        .filter(t -> t.typeInspection.get().isPublic())
                        .map(t -> {
                            TypeAnalysis ta = analysisProvider.getTypeAnalysisNullWhenAbsent(t);
                            if (ta == null) {
                                messages.add(Message.newMessage(t.newLocation(), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE));
                            }
                            return ta;
                        })
                        .filter(Objects::nonNull)
                        .map(ta -> ta.getProperty(Property.INDEPENDENT))
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                if (fromSuperTypes.isDone()) {
                    builder.setProperty(Property.INDEPENDENT, fromSuperTypes);
                    return List.of();
                }
            }
            // fallback
            builder.setProperty(Property.INDEPENDENT, MultiLevel.DEPENDENT_DV);
        } else if (immutable.ge(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV) && inMap.lt(independent)) {
            messages.add(Message.newMessage(builder.typeInfo.newLocation(), Message.Label.INCONSISTENT_INDEPENDENCE_VALUE));
        }
        return messages;
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return null;
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return typeInfo.fullyQualifiedName;
    }

    @Override
    public boolean ignorePrivateConstructorsForFieldValue() {
        return false;
    }

    @Override
    public Stream<MethodAnalyser> allMethodAnalysersIncludingSubTypes() {
        // used for computing isReadInMethod (field values); shallow types do not have actual methods
        return Stream.of();
    }

    @Override
    public Stream<FieldAnalyser> allFieldAnalysers() {
        throw new UnsupportedOperationException();
    }
}
