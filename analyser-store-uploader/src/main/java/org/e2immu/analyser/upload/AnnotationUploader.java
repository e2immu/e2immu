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

package org.e2immu.analyser.upload;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SMapList;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.UPLOAD;
import static org.e2immu.analyser.util.Logger.log;

// cannot be E2Immu... fields get modified
@Container
@E1Immutable
public class AnnotationUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationUploader.class);
    public static final String TYPE_SUFFIX = "-t";
    public static final String PARAMETER_SUFFIX = "-p";
    public static final String METHOD_SUFFIX = "-m";
    public static final String FIELD_SUFFIX = "-f";
    public static final String TYPE_OF_METHOD_SUFFIX = "-mt";
    public static final String TYPE_OF_FIELD_SUFFIX = "-ft";

    private final UploadConfiguration configuration;
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private final List<Pair<String, AnnotationExpression>> typePairs;
    private final List<Pair<String, AnnotationExpression>> methodPairs;
    private final List<Pair<String, AnnotationExpression>> fieldPairs;
    private final List<Pair<String, AnnotationExpression>> parameterPairs;
    private final List<Pair<String, AnnotationExpression>> dynamicTypeAnnotations;

    private static String lc(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }

    public AnnotationUploader(UploadConfiguration configuration, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.configuration = configuration;

        typePairs = List.of(
                new Pair<>(lc(E2Container.class), e2ImmuAnnotationExpressions.e2Container),
                new Pair<>(lc(E2Immutable.class), e2ImmuAnnotationExpressions.e2Immutable),
                new Pair<>(lc(E1Container.class), e2ImmuAnnotationExpressions.e1Container),
                new Pair<>(lc(E1Immutable.class), e2ImmuAnnotationExpressions.e1Immutable),
                new Pair<>(lc(Container.class), e2ImmuAnnotationExpressions.container),
                new Pair<>(lc(MutableModifiesArguments.class), e2ImmuAnnotationExpressions.mutableModifiesArguments)
        );

        methodPairs = List.of(
                new Pair<>(lc(Independent.class), e2ImmuAnnotationExpressions.independent),
                new Pair<>(lc(Dependent.class), e2ImmuAnnotationExpressions.dependent),
                new Pair<>(lc(NotModified.class), e2ImmuAnnotationExpressions.notModified),
                new Pair<>(lc(Modified.class), e2ImmuAnnotationExpressions.modified)
        );

        fieldPairs = List.of(
                new Pair<>(lc(Variable.class), e2ImmuAnnotationExpressions.variableField),
                new Pair<>(lc(Modified.class), e2ImmuAnnotationExpressions.modified),
                new Pair<>(lc(Final.class), e2ImmuAnnotationExpressions.effectivelyFinal),
                new Pair<>(lc(NotModified.class), e2ImmuAnnotationExpressions.notModified)
        );

        parameterPairs = List.of(
                new Pair<>(lc(NotModified.class), e2ImmuAnnotationExpressions.notModified),
                new Pair<>(lc(Modified.class), e2ImmuAnnotationExpressions.modified)
        );

        dynamicTypeAnnotations = List.of(
                new Pair<>(lc(E2Container.class), e2ImmuAnnotationExpressions.e2Container),
                new Pair<>(lc(E2Immutable.class), e2ImmuAnnotationExpressions.e2Immutable),
                new Pair<>(lc(E1Container.class), e2ImmuAnnotationExpressions.e1Container),
                new Pair<>(lc(E1Immutable.class), e2ImmuAnnotationExpressions.e1Immutable),
                new Pair<>(lc(BeforeMark.class), e2ImmuAnnotationExpressions.beforeMark)
        );
    }

    public Map<String, String> createMap(Collection<TypeInfo> types) {
        LOGGER.info("Uploading annotations of {} types", types.size());
        Set<TypeInfo> referredTo = new HashSet<>();
        Map<String, List<String>> map = new HashMap<>();
        for (TypeInfo type : types) {
            add(map, type);
            log(UPLOAD, "Adding annotations of {}", type.fullyQualifiedName);
            type.typesReferenced().stream().map(Map.Entry::getKey).forEach(referredTo::add);
        }
        referredTo.removeAll(types);

        log(UPLOAD, "Uploading annotations of {} types referred to", referredTo.size());
        for (TypeInfo type : referredTo) {
            log(UPLOAD, "Adding annotations of {}", type.fullyQualifiedName);
            add(map, type);
        }
        log(UPLOAD, "Writing {} annotations", map.size());
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
    }

    private static boolean typeAnnotatedWith(TypeInfo typeInfo, AnnotationExpression ae) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.getOrElse(null);
        return typeAnalysis != null && typeInfo.annotatedWith(typeAnalysis, ae) == Boolean.TRUE;
    }

    private static boolean methodAnnotatedWith(MethodInfo methodInfo, AnnotationExpression ae) {
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.getOrElse(null);
        return methodAnalysis != null && methodInfo.annotatedWith(methodAnalysis, ae) == Boolean.TRUE;
    }

    private static boolean fieldAnnotatedWith(FieldInfo fieldInfo, AnnotationExpression ae) {
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.getOrElse(null);
        return fieldAnalysis != null && fieldInfo.annotatedWith(fieldAnalysis, ae) == Boolean.TRUE;
    }

    private static boolean parameterAnnotatedWith(ParameterInfo parameterInfo, AnnotationExpression ae) {
        ParameterAnalysis parameterAnalysis = parameterInfo.parameterAnalysis.getOrElse(null);
        return parameterAnalysis != null && parameterInfo.annotatedWith(parameterAnalysis, ae) == Boolean.TRUE;
    }

    private void add(Map<String, List<String>> map, TypeInfo type) {
        String typeQn = type.fullyQualifiedName;
        if (!configuration.accept(type.packageName())) {
            log(UPLOAD, "Rejecting type {} because of upload package configuration", typeQn);
            return;
        }
        for (Pair<String, AnnotationExpression> pair : typePairs) {
            if (typeAnnotatedWith(type, pair.v)) {
                SMapList.add(map, typeQn, pair.k + TYPE_SUFFIX);
                log(UPLOAD, "Added {} as type", pair.k);
                break;
            }
        }
        TypeInspection inspection = type.typeInspection.getOrElse(null);
        if (inspection == null) return;
        inspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM)
                .forEach(methodInfo -> {
                    String methodQn = methodInfo.distinguishingName();
                    for (Pair<String, AnnotationExpression> pair : methodPairs) {
                        if (methodAnnotatedWith(methodInfo, pair.v)) {
                            SMapList.add(map, methodQn, pair.k + METHOD_SUFFIX);
                            log(UPLOAD, "Added {} as method", pair.k);
                            break;
                        }
                    }
                    int i = 0;
                    for (ParameterInfo parameterInfo : methodInfo.methodInspection.get(methodQn).getParameters()) {
                        String parameterQn = methodQn + "#" + i;
                        for (Pair<String, AnnotationExpression> pair : parameterPairs) {
                            if (parameterAnnotatedWith(parameterInfo, pair.v)) {
                                SMapList.add(map, parameterQn, pair.k + PARAMETER_SUFFIX);
                                break;
                            }
                        }
                        i++;
                    }
                    if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                        TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
                        if (bestType != null) {
                            String methodsTypeQn = bestType.fullyQualifiedName;
                            for (Pair<String, AnnotationExpression> pair : dynamicTypeAnnotations) {
                                if (methodAnnotatedWith(methodInfo, pair.v)) {
                                    SMapList.add(map, methodQn + " " + methodsTypeQn, pair.k + TYPE_OF_METHOD_SUFFIX);
                                    break;
                                }
                            }
                        }
                    }
                });
        for (FieldInfo fieldInfo : inspection.fields()) {
            String fieldQn = typeQn + ":" + fieldInfo.name;
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();

            for (Pair<String, AnnotationExpression> pair : fieldPairs) {
                if (fieldAnnotatedWith(fieldInfo, pair.v)) {
                    SMapList.add(map, fieldQn, pair.k + FIELD_SUFFIX);
                    break;
                }
            }
            if (bestType != null) {
                String fieldsTypeQn = bestType.fullyQualifiedName;
                for (Pair<String, AnnotationExpression> pair : dynamicTypeAnnotations) {
                    if (fieldAnnotatedWith(fieldInfo, pair.v)) {
                        SMapList.add(map, fieldQn + " " + fieldsTypeQn, pair.k + TYPE_OF_FIELD_SUFFIX);
                        break;
                    }
                }
            }
        }
    }

    public void writeMap(Map<String, String> map) {
        Gson gson = new Gson();
        String jsonString = gson.toJson(map);
        log(UPLOAD, "Json: {}", jsonString);
        writeJson(jsonString);
    }

    private void writeJson(String jsonBody) {
        String url = configuration.createUrlWithProjectName("set");
        HttpPut httpPut = new HttpPut(url);
        try {
            httpPut.setEntity(new StringEntity(jsonBody));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not encode the JSON body");
        }
        log(UPLOAD, "Calling PUT on " + url);
        try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                JsonReader reader = new JsonReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8));
                reader.beginObject();
                while (reader.hasNext()) {
                    String action = reader.nextName();
                    int count = reader.nextInt();
                    log(UPLOAD, "Response: {} = {}", action, count);
                }
            } else {
                LOGGER.warn("PUT on {} returned response code {}", url, statusCode);
            }
        } catch (IOException e) {
            LOGGER.warn("IOException when calling PUT on {}: {}", url, e.getMessage());
        }
    }
}
