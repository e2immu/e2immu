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
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.SMapList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public AnnotationUploader(UploadConfiguration configuration) {
        this.configuration = configuration;
    }

    public Map<String, String> createMap(Collection<TypeInfo> types, Stream<Message> messageStream) {
        LOGGER.info("Uploading annotations of {} types", types.size());
        Set<TypeInfo> referredTo = new HashSet<>();
        Map<String, List<String>> map = new HashMap<>();
        for (TypeInfo type : types) {
            map.putAll(add(type));
            LOGGER.debug("Adding annotations of {}", type.fullyQualifiedName);
            type.typesReferenced().stream().map(Map.Entry::getKey).forEach(referredTo::add);
        }
        referredTo.removeAll(types);

        LOGGER.debug("Adding annotations of {} types referred to", referredTo.size());
        for (TypeInfo type : referredTo) {
            LOGGER.debug("Adding annotations of {}", type.fullyQualifiedName);
            map.putAll(add(type));
        }

        messageStream.filter(message -> message.message().severity == Message.Severity.ERROR)
                .filter(message -> ((LocationImpl) message.location()).statementIndexInMethod == null) // only type, field, method errors
                .forEach(message -> SMapList.add(map, fqn(((LocationImpl) message.location()).info),
                        "error" + suffix(((LocationImpl) message.location()).info)));

        LOGGER.debug("Writing {} annotations", map.size());
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
    }

    private static String fqn(WithInspectionAndAnalysis info) {
        if (info instanceof TypeInfo t) return t.fullyQualifiedName;
        if (info instanceof FieldInfo f) return f.owner.fullyQualifiedName + ":" + f.name;
        if (info instanceof ParameterInfo p) return p.owner.fullyQualifiedName + "#" + p.index;
        if (info instanceof MethodInfo m) return m.fullyQualifiedName;
        throw new UnsupportedOperationException("Have " + info.getClass());
    }

    private static String suffix(WithInspectionAndAnalysis info) {
        if (info instanceof TypeInfo) return TYPE_SUFFIX;
        if (info instanceof FieldInfo) return FIELD_SUFFIX;
        if (info instanceof ParameterInfo) return PARAMETER_SUFFIX;
        if (info instanceof MethodInfo) return METHOD_SUFFIX;
        throw new UnsupportedOperationException("Have " + info.getClass());
    }

    private Map<String, List<String>> add(TypeInfo type) {
        if (!configuration.accept(type.packageName())) {
            LOGGER.debug("Rejecting type {} because of upload package configuration", type.fullyQualifiedName);
            return Map.of();
        }
        Map<String, List<String>> map = new HashMap<>(annotations(type, type.fullyQualifiedName, TYPE_SUFFIX));

        TypeInspection inspection = type.typeInspection.getOrDefaultNull();
        if (inspection == null) return map;
        inspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM)
                .forEach(methodInfo -> {
                    map.putAll(annotations(methodInfo, methodInfo.fullyQualifiedName, METHOD_SUFFIX));
                    for (ParameterInfo parameterInfo : methodInfo.methodInspection
                            .get(methodInfo.fullyQualifiedName).getParameters()) {
                        String parameterQn = fqn(parameterInfo);
                        map.putAll(annotations(parameterInfo, parameterQn, PARAMETER_SUFFIX));
                    }
                    if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                        TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
                        if (bestType != null) {
                            map.putAll(annotations(bestType, bestType.fullyQualifiedName, TYPE_OF_METHOD_SUFFIX));
                        }
                    }
                });
        for (FieldInfo fieldInfo : inspection.fields()) {
            String fieldQn = fqn(fieldInfo);
            map.putAll(annotations(fieldInfo, fieldQn, FIELD_SUFFIX));
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();
            if (bestType != null) {
                map.putAll(annotations(bestType, bestType.fullyQualifiedName, TYPE_OF_FIELD_SUFFIX));
            }
        }
        return map;
    }

    private Map<String, List<String>> annotations(WithInspectionAndAnalysis type, String qualifiedName, String suffix) {
        Analysis analysis = type.hasBeenAnalysed() ? type.getAnalysis() : null;
        Set<AnnotationExpression> annotations = new HashSet<>();
        if (analysis != null) {
            analysis.getAnnotationStream()
                    .filter(e -> e.getValue().isPresent())
                    .map(Map.Entry::getKey)
                    .forEach(annotations::add);
        }
        if (type.hasBeenInspected()) {
            type.getInspection().getAnnotations().stream()
                    .filter(ae -> !annotations.contains(ae) && ae.e2ImmuAnnotationParameters() != null
                            && !ae.e2ImmuAnnotationParameters().absent())
                    .forEach(annotations::add);
        }
        List<String> annotationStrings = new ArrayList<>(annotations.stream()
                .map(ae -> ae.typeInfo().simpleName.toLowerCase() + suffix).
                sorted().toList()); // we might be adding to the list
        return Map.of(qualifiedName, annotationStrings);
    }

    public void writeMap(Map<String, String> map) {
        Gson gson = new Gson();
        String jsonString = gson.toJson(map);
        LOGGER.debug("Json: {}", jsonString);
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
        LOGGER.debug("Calling PUT on " + url);
        try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                JsonReader reader = new JsonReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8));
                reader.beginObject();
                while (reader.hasNext()) {
                    String action = reader.nextName();
                    int count = reader.nextInt();
                    LOGGER.debug("Response: {} = {}", action, count);
                }
            } else {
                LOGGER.warn("PUT on {} returned response code {}", url, statusCode);
            }
        } catch (IOException e) {
            LOGGER.warn("IOException when calling PUT on {}: {}", url, e.getMessage());
        }
    }
}
