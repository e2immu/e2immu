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

package org.e2immu.analyser.resolver.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.AggregatingTypeAnalyser;
import org.e2immu.analyser.analyser.impl.ComputingTypeAnalyser;
import org.e2immu.analyser.analyser.impl.FieldAnalyserImpl;
import org.e2immu.analyser.analyser.impl.MethodAnalyserFactory;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.resolver.AnalyserGenerator;
import org.e2immu.analyser.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultAnalyserGeneratorImpl implements AnalyserGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAnalyserGeneratorImpl.class);

    public final Set<TypeInfo> primaryTypes;
    public final List<Analyser> analysers;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<ParameterInfo, ParameterAnalyser> parameterAnalysers;
    private final String name;


    // NOTE: we use LinkedHashMaps to preserve the sorting order

    public DefaultAnalyserGeneratorImpl(List<SortedType> sortedTypes, Configuration configuration, AnalyserContext analyserContext) {
        name = sortedTypes.stream().map(sortedType -> sortedType.primaryType().fullyQualifiedName).collect(Collectors.joining(","));
        primaryTypes = sortedTypes.stream().map(SortedType::primaryType).collect(Collectors.toUnmodifiableSet());

        // do the types first, so we can pass on a TypeAnalysis objects
        Map<TypeInfo, TypeAnalyser> typeAnalysersBuilder = new LinkedHashMap<>();
        sortedTypes.forEach(sortedType ->
                sortedType.methodsFieldsSubTypes().forEach(mfs -> {
                    if (mfs instanceof TypeInfo typeInfo && !typeInfo.typeAnalysis.isSet()) {
                        TypeAnalyser typeAnalyser;
                        if (typeInfo.isAggregated()) {
                            typeAnalyser = new AggregatingTypeAnalyser(typeInfo, sortedType.primaryType(), analyserContext);
                        } else {
                            typeAnalyser = new ComputingTypeAnalyser(typeInfo, sortedType.primaryType(), analyserContext);
                        }
                        typeAnalysersBuilder.put(typeInfo, typeAnalyser);
                    }
                }));
        typeAnalysers = Collections.unmodifiableMap(typeAnalysersBuilder);

        // then methods
        // filtering out those methods that have not been defined is not a good idea, since the MethodAnalysisImpl object
        // can only reach TypeAnalysisImpl, and not its builder. We'd better live with empty methods in the method analyser.
        Map<ParameterInfo, ParameterAnalyser> parameterAnalysersBuilder = new LinkedHashMap<>();
        Map<MethodInfo, MethodAnalyser> methodAnalysersBuilder = new LinkedHashMap<>();
        sortedTypes.forEach(sortedType -> {
            List<WithInspectionAndAnalysis> analyses = sortedType.methodsFieldsSubTypes();
            analyses.forEach(analysis -> {
                if (analysis instanceof MethodInfo methodInfo && !methodInfo.methodAnalysis.isSet()) {
                    TypeAnalyser typeAnalyser = typeAnalysers.get(methodInfo.typeInfo);
                    assert typeAnalyser != null : "Cannot find type analyser for " + methodInfo.typeInfo;
                    MethodAnalyser methodAnalyser = MethodAnalyserFactory.create(methodInfo, typeAnalyser.getTypeAnalysis(),
                            false, true, analyserContext);
                    for (ParameterAnalyser parameterAnalyser : methodAnalyser.getParameterAnalysers()) {
                        parameterAnalysersBuilder.put(parameterAnalyser.getParameterInfo(), parameterAnalyser);
                    }
                    methodAnalysersBuilder.put(methodInfo, methodAnalyser);
                    // finalizers are done early, before the first assignments
                    if (methodInfo.methodInspection.get().hasContractedFinalizer()) {
                        ((TypeAnalysisImpl.Builder) typeAnalyser.getTypeAnalysis())
                                .setProperty(Property.FINALIZER, DV.TRUE_DV);
                    }
                }
            });
        });

        parameterAnalysers = Collections.unmodifiableMap(parameterAnalysersBuilder);
        methodAnalysers = Collections.unmodifiableMap(methodAnalysersBuilder);

        // finally, we deal with fields, and wire everything together
        Map<FieldInfo, FieldAnalyser> fieldAnalysersBuilder = new HashMap<>();
        List<Analyser> allAnalysers = sortedTypes.stream().flatMap(sortedType ->
                sortedType.methodsFieldsSubTypes().stream().flatMap(mfs -> {
                    Analyser analyser;
                    if (mfs instanceof FieldInfo fieldInfo) {
                        if (!fieldInfo.fieldAnalysis.isSet()) {
                            TypeAnalysis ownerTypeAnalysis = typeAnalysers.get(fieldInfo.owner).getTypeAnalysis();
                            analyser = new FieldAnalyserImpl(fieldInfo, sortedType.primaryType(), ownerTypeAnalysis,
                                    analyserContext);
                            fieldAnalysersBuilder.put(fieldInfo, (FieldAnalyser) analyser);
                        } else {
                            analyser = null;
                            LOGGER.debug("Ignoring field {}, already has analysis", fieldInfo.fullyQualifiedName());
                        }
                    } else if (mfs instanceof MethodInfo) {
                        analyser = methodAnalysers.get(mfs);
                    } else if (mfs instanceof TypeInfo) {
                        analyser = typeAnalysers.get(mfs);
                    } else {
                        throw new UnsupportedOperationException("have " + mfs);
                    }
                    return analyser == null ? Stream.empty() : Stream.of(analyser);
                })).toList();
        fieldAnalysers = Map.copyOf(fieldAnalysersBuilder);

        List<MethodAnalyser> methodAnalysersInOrder = new ArrayList<>(methodAnalysers.size());
        List<FieldAnalyser> fieldAnalysersInOrder = new ArrayList<>(fieldAnalysers.size());
        List<TypeAnalyser> typeAnalysersInOrder = new ArrayList<>(typeAnalysers.size());
        allAnalysers.forEach(analyser -> {
            if (analyser instanceof MethodAnalyser ma) methodAnalysersInOrder.add(ma);
            else if (analyser instanceof TypeAnalyser ta) typeAnalysersInOrder.add(ta);
            else if (analyser instanceof FieldAnalyser fa) fieldAnalysersInOrder.add(fa);
            else throw new UnsupportedOperationException();
        });

        boolean forceAlphabeticAnalysis = configuration.analyserConfiguration().forceAlphabeticAnalysisInPrimaryType();
        if (forceAlphabeticAnalysis) {
            methodAnalysersInOrder.sort(Comparator.comparing(ma -> ma.getMethodInfo().fullyQualifiedName));
            typeAnalysersInOrder.sort(Comparator.comparing(ta -> ta.getTypeInfo().fullyQualifiedName));
            fieldAnalysersInOrder.sort(Comparator.comparing(fa -> fa.getFieldInfo().fullyQualifiedName));
        }
        analysers = ListUtil.immutableConcat(methodAnalysersInOrder, fieldAnalysersInOrder, typeAnalysersInOrder);
        assert analysers.size() == new HashSet<>(analysers).size() : "There are be duplicates among the analysers?";
    }

    @Override
    public List<Analyser> getAnalysers() {
        return analysers;
    }

    @Override
    public Map<FieldInfo, FieldAnalyser> getFieldAnalysers() {
        return fieldAnalysers;
    }

    @Override
    public Map<MethodInfo, MethodAnalyser> getMethodAnalysers() {
        return methodAnalysers;
    }

    @Override
    public Map<TypeInfo, TypeAnalyser> getTypeAnalysers() {
        return typeAnalysers;
    }

    @Override
    public Map<ParameterInfo, ParameterAnalyser> getParameterAnalysers() {
        return parameterAnalysers;
    }

    @Override
    public Set<TypeInfo> getPrimaryTypes() {
        return primaryTypes;
    }

    public String getName() {
        return name;
    }
}

