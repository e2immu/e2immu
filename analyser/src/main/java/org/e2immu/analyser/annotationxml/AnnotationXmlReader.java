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

package org.e2immu.analyser.annotationxml;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.annotationxml.model.*;
import org.e2immu.analyser.util.Resources;
import org.e2immu.annotation.E2Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.e2immu.analyser.util.Logger.LogTarget.ANNOTATION_XML_READER;
import static org.e2immu.analyser.util.Logger.log;

@E2Immutable
public class AnnotationXmlReader implements AnnotationStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationXmlReader.class);

    public final Map<String, TypeItem> typeItemMap;
    public final int numberOfAnnotations;
    private static final Pattern TYPE = Pattern.compile("\\S+");
    private static final Pattern CONSTRUCTOR = Pattern.compile("(\\S+) ([^\\s()]+\\([^)]*\\))( (\\d+))?");
    private static final Pattern METHOD = Pattern.compile("(\\S+) ([^()]+) ([^\\s()]+\\([^)]*\\))( (\\d+))?");
    private static final Pattern FIELD = Pattern.compile("(\\S+) ([^\\s()]+)");

    public AnnotationXmlReader(Resources classPath) {
        Map<String, TypeItem> typeItemMap = new HashMap<>();

        int countAnnotations = 0;
        for (URL url : classPath.expandURLs("annotations.xml")) {
            try {
                countAnnotations += parse(url, typeItemMap);
            } catch (IOException io) {
                LOGGER.warn("Skipping {}: IOException {}", url, io.getMessage());
            } catch (SAXException e) {
                LOGGER.warn("Skipping {}: SAXException {}", url, e.getMessage());
            } catch (ParserConfigurationException e) {
                LOGGER.warn("Skipping {}: Parser config exception {}", url, e.getMessage());
            }
        }
        this.numberOfAnnotations = countAnnotations;
        this.typeItemMap = ImmutableMap.copyOf(typeItemMap);
        this.typeItemMap.values().forEach(TypeItem::freeze);
    }

    public AnnotationXmlReader(URL annotationXml) throws IOException, ParserConfigurationException, SAXException {
        this(List.of(annotationXml));
    }

    public AnnotationXmlReader(Collection<URL> annotationXmls) throws IOException, ParserConfigurationException, SAXException {
        Map<String, TypeItem> typeItemMap = new HashMap<>();

        int countAnnotations = 0;
        for (URL annotationXml : annotationXmls) {
            countAnnotations += parse(annotationXml, typeItemMap);
        }
        this.numberOfAnnotations = countAnnotations;
        this.typeItemMap = ImmutableMap.copyOf(typeItemMap);
        this.typeItemMap.values().forEach(TypeItem::freeze);
    }

    private static int parse(URL annotationXml, Map<String, TypeItem> typeItemMap) throws ParserConfigurationException, IOException, SAXException {
        log(ANNOTATION_XML_READER, "Parsing {}", annotationXml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(annotationXml.openStream());
        NodeList nodeList = document.getDocumentElement().getChildNodes();

        int countAnnotations = 0;
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("item")) {
                String name = node.getAttributes().getNamedItem("name").getNodeValue();

                HasAnnotations theType;
                Matcher typeMatcher = TYPE.matcher(name);
                if (typeMatcher.matches()) {
                    theType = typeItem(typeItemMap, typeMatcher.group());
                } else {
                    Matcher constructorMatcher = CONSTRUCTOR.matcher(name);
                    if (constructorMatcher.matches()) {
                        TypeItem typeItem = typeItem(typeItemMap, constructorMatcher.group(1));
                        String paramIndex = constructorMatcher.group(4);
                        if (paramIndex != null) {
                            theType = parameterItem(typeItem, constructorMatcher.group(2), null, paramIndex);
                        } else {
                            theType = methodItem(typeItem, constructorMatcher.group(2), null);
                        }
                    } else {
                        Matcher methodMatcher = METHOD.matcher(name);
                        if (methodMatcher.matches()) {
                            TypeItem typeItem = typeItem(typeItemMap, methodMatcher.group(1));
                            String paramIndex = methodMatcher.group(5);
                            if (paramIndex != null) {
                                theType = parameterItem(typeItem, methodMatcher.group(3), methodMatcher.group(2), paramIndex);
                            } else {
                                theType = methodItem(typeItem, methodMatcher.group(3), methodMatcher.group(2));
                            }
                        } else {
                            Matcher fieldMatcher = FIELD.matcher(name);
                            if (fieldMatcher.matches()) {
                                TypeItem typeItem = typeItem(typeItemMap, fieldMatcher.group(1));
                                theType = fieldItem(typeItem, fieldMatcher.group(2));
                            } else {
                                throw new UnsupportedOperationException("? have name that is not matched at all: " + name);
                            }
                        }
                    }
                }

                // now read the annotations
                NodeList annotationNodes = node.getChildNodes();
                for (int j = 0; j < annotationNodes.getLength(); j++) {
                    Node annotationNode = annotationNodes.item(j);
                    if (annotationNode.getNodeType() == Node.ELEMENT_NODE) {
                        Node nameAttribute = annotationNode.getAttributes() == null ? null : annotationNode.getAttributes().getNamedItem("name");
                        if (nameAttribute != null) {
                            String annotationType = nameAttribute.getNodeValue();
                            Annotation.Builder annotationBuilder = new Annotation.Builder(annotationType);

                            countAnnotations++;
                            NodeList valueNodes = annotationNode.getChildNodes();
                            for (int k = 0; k < valueNodes.getLength(); k++) {
                                Node valueNode = valueNodes.item(k);
                                if (valueNode.getNodeType() == Node.ELEMENT_NODE && valueNode.getAttributes() != null) {
                                    Node valNameNode = valueNode.getAttributes().getNamedItem("name");
                                    Node valValNode = valueNode.getAttributes().getNamedItem("val");
                                    if (valValNode != null) {
                                        String valName = valNameNode == null ? null : valNameNode.getNodeValue();
                                        String valValue = valValNode.getNodeValue();
                                        if (valValue != null) {
                                            Value value = new Value(valName, valValue);
                                            annotationBuilder.addValue(value);
                                        }
                                    } else {
                                        LOGGER.warn("Have value tag without value? " + valueNode);
                                    }
                                }
                            }
                            theType.getAnnotations().add(annotationBuilder.build());
                        } else {
                            LOGGER.warn("Attribute 'name' missing?");
                        }
                    }
                }
            }
        }
        return countAnnotations;
    }


    private static ParameterItem parameterItem(TypeItem typeItem, String methodName, String returnType, String parameterIndex) {
        MethodItem methodItem = methodItem(typeItem, methodName, returnType);
        ParameterItem parameterItem = new ParameterItem(Integer.parseInt(parameterIndex));
        methodItem.getParameterItems().add(parameterItem);
        return parameterItem;
    }

    private static MethodItem methodItem(TypeItem typeItem, String methodName, String returnType) {
        MethodItem methodItem = typeItem.getMethodItems().get(methodName);
        if (methodItem == null) {
            methodItem = new MethodItem(methodName, returnType);
            typeItem.getMethodItems().put(methodName, methodItem);
            log(ANNOTATION_XML_READER, "Created method {} returns {}", methodName, returnType);
        }
        return methodItem;
    }

    private static FieldItem fieldItem(TypeItem typeItem, String fieldName) {
        FieldItem fieldItem = typeItem.getFieldItems().get(fieldName);
        if (fieldItem == null) {
            fieldItem = new FieldItem(fieldName);
            typeItem.getFieldItems().put(fieldName, fieldItem);
            log(ANNOTATION_XML_READER, "Created field {}", fieldName);
        }
        return fieldItem;
    }

    private static TypeItem typeItem(Map<String, TypeItem> typeItemMap, String name) {
        TypeItem typeItem = typeItemMap.get(name);
        if (typeItem == null) {
            typeItem = new TypeItem(name);
            typeItemMap.put(name, typeItem);
            log(ANNOTATION_XML_READER, "Created type {}", name);
        }
        return typeItem;
    }

    public Map<String, Integer> summary() {
        Map<String, Integer> annotationCounts = new HashMap<>();
        typeItemMap.values().forEach(typeItem -> {
            typeItem.getAnnotations().forEach(annotation -> increment(annotationCounts, "T " + annotation.name()));

            typeItem.getMethodItems().values().forEach(methodItem -> {
                methodItem.getAnnotations().forEach(annotation -> increment(annotationCounts, "M " + annotation.name()));

                methodItem.getParameterItems().forEach(parameterItem -> {
                    parameterItem.getAnnotations().forEach(annotation -> increment(annotationCounts, "P " + annotation.name()));
                });
            });
            typeItem.getFieldItems().values().forEach(fieldItem -> {
                fieldItem.getAnnotations().forEach(annotation -> increment(annotationCounts, "F " + annotation.name()));
            });
        });
        return annotationCounts;
    }

    private static void increment(Map<String, Integer> map, String key) {
        Integer inMap = map.get(key);
        if (inMap == null) inMap = 1;
        else inMap = inMap + 1;
        map.put(key, inMap);
    }

    @Override
    public TypeItem typeItemsByFQName(String fqTypeName) {
        return typeItemMap.get(fqTypeName);
    }

    @Override
    public Collection<TypeItem> typeItems() {
        return typeItemMap.values();
    }

    @Override
    public int getNumberOfAnnotations() {
        return numberOfAnnotations;
    }
}
