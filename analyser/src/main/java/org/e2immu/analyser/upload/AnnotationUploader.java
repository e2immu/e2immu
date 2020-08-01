/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.upload;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SMapList;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.UPLOAD;
import static org.e2immu.analyser.util.Logger.log;

// cannot be E2Immu... fields get modified
@Container
@E1Immutable
public class AnnotationUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationUploader.class);

    private final UploadConfiguration configuration;
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private final List<Pair<String, AnnotationExpression>> typePairs;
    private final List<Pair<String, AnnotationExpression>> methodPairs;
    private final List<Pair<String, AnnotationExpression>> fieldPairs;
    private final List<Pair<String, AnnotationExpression>> parameterPairs;

    private static String lc(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }

    public AnnotationUploader(UploadConfiguration configuration, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.configuration = configuration;

        typePairs = List.of(
                new Pair<>(lc(E2Immutable.class), e2ImmuAnnotationExpressions.e2Immutable.get()),
                new Pair<>(lc(E2Container.class), e2ImmuAnnotationExpressions.e2Container.get()),
                new Pair<>(lc(Container.class), e2ImmuAnnotationExpressions.container.get())
        );
        methodPairs = List.of(
                new Pair<>(lc(NotModified.class), e2ImmuAnnotationExpressions.notModified.get()),
                new Pair<>(lc(Modified.class), e2ImmuAnnotationExpressions.notModified.get()),
                new Pair<>(lc(Independent.class), e2ImmuAnnotationExpressions.independent.get())
        );
        fieldPairs = List.of(
                new Pair<>(lc(NotModified.class), e2ImmuAnnotationExpressions.notModified.get()),
                new Pair<>(lc(Modified.class), e2ImmuAnnotationExpressions.modified.get()),
                new Pair<>(lc(Final.class), e2ImmuAnnotationExpressions.effectivelyFinal.get()),
                new Pair<>(lc(Variable.class), e2ImmuAnnotationExpressions.variableField.get()),
                new Pair<>(lc(E2Immutable.class), e2ImmuAnnotationExpressions.e2Immutable.get()),
                new Pair<>(lc(E2Container.class), e2ImmuAnnotationExpressions.e2Container.get())
        );
        parameterPairs = List.of(
                new Pair<>(lc(NotModified.class), e2ImmuAnnotationExpressions.notModified.get()),
                new Pair<>(lc(Modified.class), e2ImmuAnnotationExpressions.modified.get())
        );
    }

    public void add(List<SortedType> sortedTypes) {
        LOGGER.info("Uploading annotations of {} types", sortedTypes.size());
        Map<String, List<String>> map = new HashMap<>();
        for (SortedType sortedType : sortedTypes) {
            add(map, sortedType);
        }
        Map<String, String> csvMap = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
        writeMap(csvMap);
    }

    private void add(Map<String, List<String>> map, SortedType sortedType) {
        String typeQn = sortedType.typeInfo.fullyQualifiedName;
        for (Pair<String, AnnotationExpression> pair : typePairs) {
            if (sortedType.typeInfo.annotatedWith(pair.v) == Boolean.TRUE) {
                SMapList.add(map, typeQn, pair.k);
            }
        }
        sortedType.typeInfo.typeInspection.get().constructorAndMethodStream().forEach(methodInfo -> {
            String methodQn = methodInfo.distinguishingName();
            for (Pair<String, AnnotationExpression> pair : methodPairs) {
                if (methodInfo.annotatedWith(pair.v) == Boolean.TRUE) {
                    SMapList.add(map, methodQn, pair.k);
                }
            }
            int i = 0;
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                String parameterQn = methodQn + "#" + i;
                for (Pair<String, AnnotationExpression> pair : parameterPairs) {
                    if (parameterInfo.annotatedWith(pair.v) == Boolean.TRUE) {
                        SMapList.add(map, parameterQn, pair.k);
                    }
                }
                i++;
            }
        });
        for (FieldInfo fieldInfo : sortedType.typeInfo.typeInspection.get().fields) {
            String fieldQn = typeQn + ":" + fieldInfo.name;
            for (Pair<String, AnnotationExpression> pair : fieldPairs) {
                if (fieldInfo.annotatedWith(pair.v) == Boolean.TRUE) {
                    SMapList.add(map, fieldQn, pair.k);
                }
            }
        }
    }

    private void writeMap(Map<String, String> map) {
        Gson gson = new Gson();
        String jsonString = gson.toJson(map);
        writeJson(jsonString);
        log(UPLOAD, "Json: {}", jsonString);
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
                JsonReader reader = new JsonReader(new InputStreamReader(entity.getContent(), Charsets.UTF_8));
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
