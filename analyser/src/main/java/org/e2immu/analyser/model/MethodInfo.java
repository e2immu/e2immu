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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.OutputMethodInfo;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;

import java.util.*;
import java.util.stream.Collectors;

@Container
@E2Immutable(after = "TypeAnalyser.analyse()") // and not MethodAnalyser.analyse(), given the back reference
public class MethodInfo implements WithInspectionAndAnalysis {
    public static final String UNARY_MINUS_OPERATOR_INT = "int.-(int)";
    public final Identifier identifier;
    public final TypeInfo typeInfo; // back reference, only @ContextClass after...
    public final String name;
    public final String fullyQualifiedName;
    public final String distinguishingName;
    public final boolean isConstructor;

    public final SetOnce<MethodInspection> methodInspection = new SetOnce<>();
    public final SetOnce<MethodAnalysis> methodAnalysis = new SetOnce<>();
    public final SetOnce<MethodResolution> methodResolution = new SetOnce<>();
    // implementations of this abstract method, by type; NOTE: some functional interface methods will have large maps
    private final SetOnceMap<TypeInfo, MethodInfo> implementations = new SetOnceMap<>();

    // -- a bit of primitives info

    public boolean isUnaryMinusOperatorInt() {
        return UNARY_MINUS_OPERATOR_INT.equals(fullyQualifiedName()) && this.methodInspection.get().getParameters().size() == 1;
    }

    public boolean isUnaryNot() {
        return this.name.equals("!");
    }

    public boolean isPostfix() {
        return (this.name.equals("++") || this.name.equals("--")) && returnType().typeInfo != null &&
                returnType().typeInfo.fullyQualifiedName.equals("long");
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public static String dropDollarGetClass(String string) {
        if (string.endsWith("$")) {
            if (!"getClass$".equals(string)) {
                throw new UnsupportedOperationException();
            }
            return "getClass";
        }
        return string;
    }

    /**
     * it is possible to observe a method without being able to see its return type. That does not make
     * the method a constructor... we cannot use the returnTypeObserved == null as isConstructor
     */
    public MethodInfo(Identifier identifier,
                      @NotNull TypeInfo typeInfo, @NotNull String name, String fullyQualifiedName,
                      String distinguishingName, boolean isConstructor) {
        this.identifier = Objects.requireNonNull(identifier);
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.name = Objects.requireNonNull(name);
        this.fullyQualifiedName = Objects.requireNonNull(fullyQualifiedName);
        this.distinguishingName = Objects.requireNonNull(distinguishingName);
        this.isConstructor = isConstructor;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    public boolean hasBeenInspected() {
        return methodInspection.isSet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;
        return fullyQualifiedName.equals(that.fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return fullyQualifiedName.hashCode();
    }

    @Override
    public Inspection getInspection() {
        return methodInspection.get();
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        if (analysis instanceof MethodAnalysis ma) {
            methodAnalysis.set(ma);
            Iterator<ParameterAnalysis> it = ma.getParameterAnalyses().iterator();
            for (ParameterInfo parameterInfo : methodInspection.get().getParameters()) {
                if (!it.hasNext()) throw new UnsupportedOperationException();
                ParameterAnalysis parameterAnalysis = it.next();
                parameterInfo.setAnalysis(parameterAnalysis);
            }
        } else throw new UnsupportedOperationException();
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return methodAnalysis.isSet();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        if (!hasBeenInspected()) return UpgradableBooleanMap.of();
        MethodInspection inspection = methodInspection.get();
        UpgradableBooleanMap<TypeInfo> constructorTypes = isConstructor ? UpgradableBooleanMap.of() :
                inspection.getReturnType().typesReferenced(true);
        UpgradableBooleanMap<TypeInfo> parameterTypes =
                inspection.getParameters().stream()
                        .flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> annotationTypes =
                inspection.getAnnotations().stream().flatMap(ae -> ae.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> analysedAnnotationTypes =
                hasBeenAnalysed() ? methodAnalysis.get().getAnnotationStream()
                        .filter(e -> e.getValue().isVisible())
                        .flatMap(e -> e.getKey().typesReferenced().stream())
                        .collect(UpgradableBooleanMap.collector()) : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> exceptionTypes =
                inspection.getExceptionTypes().stream().flatMap(et -> et.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> bodyTypes = hasBeenInspected() ?
                inspection.getMethodBody().typesReferenced() : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> companionMethodTypes = inspection.getCompanionMethods().values().stream()
                .flatMap(cm -> cm.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        return UpgradableBooleanMap.of(constructorTypes, parameterTypes, analysedAnnotationTypes,
                annotationTypes, exceptionTypes, companionMethodTypes, bodyTypes);
    }

    @Override
    public TypeInfo primaryType() {
        return typeInfo.primaryType();
    }

    public OutputBuilder output(Qualification qualification) {
        return output(qualification, AnalyserContext.NULL_IF_NOT_SET);
    }

    public OutputBuilder output(Qualification qualification, AnalysisProvider analysisProvider) {
        return OutputMethodInfo.output(this, qualification, analysisProvider);
    }

    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String distinguishingName() {
        return distinguishingName;
    }

    public ParameterizedType returnType() {
        return methodInspection.get().getReturnType();
    }

    public Optional<AnnotationExpression> hasInspectedAnnotation(String annotationFQN) {
        if (!hasBeenInspected()) return Optional.empty();
        return (getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN))).findFirst();
    }

    @Override
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        String annotationFQN = annotation.getName();
        return hasInspectedAnnotation(annotationFQN);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    public boolean isNotOverridingAnyOtherMethod() {
        return methodResolution.get().overrides().isEmpty();
    }

    public boolean isVoid() {
        return returnType().isVoidOrJavaLangVoid();
    }

    public boolean isNotATestMethod() {
        return hasInspectedAnnotation("org.junit.Test").isEmpty() &&
                hasInspectedAnnotation("org.junit.jupiter.api.Test").isEmpty();
    }

    public boolean noReturnValue() {
        return isVoid() || isConstructor;
    }

    public boolean hasReturnValue() {
        return !noReturnValue();
    }

    public boolean computedAnalysis() {
        assert methodInspection.isSet();
        return hasStatements();
    }

    /*
    Enum.valueOf() is a static, synthetic method, which can have code or not, depending on the availability of
    certain types (see EnumMethods). It is, however, never semantically "explicitlyEmpty".
     */
    public boolean explicitlyEmptyMethod() {
        if (hasStatements() || methodInspection.get().isStatic() && methodInspection.get().isSynthetic()) return false;
        boolean empty = !typeInfo.shallowAnalysis() && !methodInspection.get().isAbstract();
        assert !empty || noReturnValue();
        return empty;
    }

    public boolean hasStatements() {
        return !methodInspection.get().getMethodBody().isEmpty();
    }

    public boolean isCompanionMethod() {
        return CompanionMethodName.extract(name) != null;
    }

    @Override
    public MethodInfo getMethod() {
        return this;
    }

    @Override
    public String niceClassName() {
        return "Method";
    }

    // helper for tests
    public ParameterAnalysis parameterAnalysis(int index) {
        return methodInspection.get().getParameters().get(index).parameterAnalysis.get();
    }

    public boolean analysisAccessible(InspectionProvider inspectionProvider) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        if (typeInspection.inspector() == Inspector.BYTE_CODE_INSPECTION) {
            MethodInspection methodInspection = inspectionProvider.getMethodInspection(this);
            return methodInspection.isPubliclyAccessible(inspectionProvider);
        }
        return true; // by hand, java parsing
    }

    /*
     The one method dealing with the parameters={} parameter in @Independent1, @Dependent on parameters
     */
    public Map<ParameterInfo, LinkedVariables> crossLinks(AnalyserContext analyserContext) {
        return analyserContext.getMethodInspection(this).getParameters().stream()
                .map(analyserContext::getParameterAnalysis)
                .filter(pa -> !pa.getLinksToOtherParameters().isEmpty())
                .collect(Collectors.toUnmodifiableMap(ParameterAnalysis::getParameterInfo,
                        ParameterAnalysis::getLinksToOtherParameters));
    }

    @Override
    public Location newLocation() {
        return new LocationImpl(this);
    }

    @Override
    public CausesOfDelay delay(CauseOfDelay.Cause cause) {
        return DelayFactory.createDelay(newLocation(), cause);
    }

    private static final Set<String> ZERO_PARAMS = Set.of("toString", "hashCode", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait");

    public boolean belongToJLO(InspectionProvider inspectionProvider) {
        List<ParameterInfo> parameters = inspectionProvider.getMethodInspection(this).getParameters();
        int numParameters = parameters.size();
        if (numParameters == 0) {
            return ZERO_PARAMS.contains(name);
        }
        if (numParameters == 1) {
            return "equals".equals(name) || "wait".equals(name) && parameters.get(0).parameterizedType.isLong();
        }
        return numParameters == 2 && "wait".equals(name) && parameters.get(0).parameterizedType.isLong() &&
                parameters.get(1).parameterizedType.isInt();
    }

    public int getComplexity() {
        return 1;
    }

    /*
    rather than isPrivate, we need to see if the method is visible outside the primary type.

     */
    public boolean isAccessibleOutsidePrimaryType() {
        MethodInspection inspection = methodInspection.get();
        if (inspection.isPrivate()) return false;
        // we could still be in a private type
        return !typeInfo.isPrivateOrEnclosingIsPrivate();
    }

    public boolean inConstruction() {
        return isConstructor || methodResolution.get().callStatus() == MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
    }

    public MethodInfo implementationIn(TypeInfo typeInfo) {
        if (methodInspection.get().isAbstract()) {
            return implementations.getOrDefaultNull(typeInfo);
        }
        return this;
    }

    public void addImplementation(MethodInfo implementation) {
        if (!implementations.isSet(implementation.typeInfo))
            implementations.put(implementation.typeInfo, implementation);
    }

    public boolean hasImplementations() {
        return !implementations.isEmpty();
    }

    public Set<MethodInfo> getImplementations() {
        return implementations.stream().map(Map.Entry::getValue).collect(Collectors.toUnmodifiableSet());
    }

    public Expression extractSingleReturnExpression() {
        MethodInspection inspection = methodInspection.get();
        Block block = inspection.getMethodBody();
        if (block.isEmpty()) return null;
        Statement statement = block.getStructure().statements().get(block.structure.statements().size() - 1);
        if (statement instanceof ReturnStatement rs) {
            return rs.expression;
        }
        return null;
    }

    // call recursively, add if not yet present
    public Map<CompanionMethodName, CompanionAnalysis> collectCompanionMethods(AnalyserContext analyserContext) {
        MethodInspection inspection = analyserContext.getMethodInspection(this);
        Map<CompanionMethodName, MethodInfo> local = inspection.getCompanionMethods();
        Map<CompanionMethodName, CompanionAnalysis> result;
        if (local == null) {
            result = new HashMap<>();
        } else {
            MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysisNullWhenAbsent(this);
            if (methodAnalysis == null) {
                result = new HashMap<>();
            } else {
                result = local.keySet().stream().collect(Collectors.toMap(k -> k,
                        k -> Objects.requireNonNull(methodAnalysis.getCompanionAnalyses().get(k),
                                "No companion analyser corresponding to " + k + " found")));
            }
        }
        // NOTE: there is no order in the overrides() set. Therefore, we need to check using typeResolution
        for (MethodInfo override : methodResolution.get().overrides()) {
            MethodInspection overrideInspection = analyserContext.getMethodInspection(override);
            MethodAnalysis overrideAnalysis = analyserContext.getMethodAnalysisNullWhenAbsent(override);
            if (overrideAnalysis != null) {
                Map<CompanionMethodName, MethodInfo> map = overrideInspection.getCompanionMethods();
                if (map != null) {
                    map.keySet().forEach(k -> {
                        // let's not overwrite!
                        boolean overwrite;
                        CompanionAnalysis companionAnalysis = overrideAnalysis.getCompanionAnalyses().get(k);
                        if (result.containsKey(k)) {
                            TypeInfo existing = result.get(k).getCompanion().typeInfo;
                            TypeInfo newType = companionAnalysis.getCompanion().typeInfo;
                            overwrite = newType.typeResolution.get().superTypesExcludingJavaLangObject().contains(existing);
                        } else {
                            overwrite = true;
                        }
                        if (overwrite) {
                            result.put(k, companionAnalysis);
                        }
                    });
                }
            }
        }
        return Map.copyOf(result);
    }
}
