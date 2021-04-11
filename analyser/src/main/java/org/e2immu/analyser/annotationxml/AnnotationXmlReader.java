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

package org.e2immu.analyser.annotationxml;

import org.e2immu.analyser.annotationxml.model.*;
import org.e2immu.analyser.config.AnnotationXmlConfiguration;
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
import java.util.*;
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
    private static final Pattern METHOD = Pattern.compile("(\\S+) ([^()]+) ([^\\s()]+\\([^)]*\\))\s?(\\d+|:: (([^()]+) ([^\\s()]+\\([^)]*\\))))?");
    private static final Pattern FIELD = Pattern.compile("(\\S+) ([^\\s()]+)");

    public AnnotationXmlReader(Resources classPath) {
        this(classPath, new AnnotationXmlConfiguration.Builder().build());
    }

    public AnnotationXmlReader(Resources classPath, AnnotationXmlConfiguration configuration) {
        Map<String, TypeItem> typeItemMap = new HashMap<>();

        int countAnnotations = 0;
        if (configuration.isReadAnnotationXmlPackages()) {
            for (URL url : classPath.expandURLs("annotations.xml")) {
                try {
                    if (accept(url, configuration.readAnnotationXmlPackages)) {
                        countAnnotations += parse(url, typeItemMap);
                    }
                } catch (IOException io) {
                    LOGGER.warn("Skipping {}: IOException {}", url, io.getMessage());
                } catch (SAXException e) {
                    LOGGER.warn("Skipping {}: SAXException {}", url, e.getMessage());
                } catch (ParserConfigurationException e) {
                    LOGGER.warn("Skipping {}: Parser config exception {}", url, e.getMessage());
                }
            }
        }
        this.numberOfAnnotations = countAnnotations;
        this.typeItemMap = Map.copyOf(typeItemMap);
        this.typeItemMap.values().forEach(TypeItem::freeze);
    }

    private boolean accept(URL url, List<String> restrictToPackages) {
        if (restrictToPackages.isEmpty()) return true;
        // FIXME
        return false;
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
        this.typeItemMap = Map.copyOf(typeItemMap);
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

                HasAnnotations theItem;
                Matcher typeMatcher = TYPE.matcher(name);
                if (typeMatcher.matches()) {
                    theItem = typeItem(typeItemMap, typeMatcher.group());
                } else {
                    Matcher constructorMatcher = CONSTRUCTOR.matcher(name);
                    if (constructorMatcher.matches()) {
                        TypeItem typeItem = typeItem(typeItemMap, constructorMatcher.group(1));
                        String constructorName = constructorMatcher.group(2);
                        MethodItem methodItem = methodItem(typeItem, constructorName, null);
                        String paramIndex = constructorMatcher.group(4);
                        if (paramIndex != null) {
                            theItem = parameterItem(methodItem, paramIndex);
                        } else {
                            theItem = methodItem;
                        }
                    } else {
                        Matcher methodMatcher = METHOD.matcher(name);
                        if (methodMatcher.matches()) {
                            TypeItem typeItem = typeItem(typeItemMap, methodMatcher.group(1));
                            String methodType = methodMatcher.group(2);
                            String methodName = methodMatcher.group(3);
                            MethodItem methodItem = methodItem(typeItem, methodName, methodType);
                            String companion = methodMatcher.group(5);
                            if (companion != null) {
                                String companionType = methodMatcher.group(6);
                                String companionName = methodMatcher.group(7);
                                theItem = companionMethodItem(methodItem, companionName, companionType);
                            } else {
                                String paramIndex = methodMatcher.group(4);
                                if (paramIndex != null) {
                                    theItem = parameterItem(methodItem, paramIndex);
                                } else {
                                    theItem = methodItem;
                                }
                            }
                        } else {
                            Matcher fieldMatcher = FIELD.matcher(name);
                            if (fieldMatcher.matches()) {
                                TypeItem typeItem = typeItem(typeItemMap, fieldMatcher.group(1));
                                theItem = fieldItem(typeItem, fieldMatcher.group(2));
                            } else {
                                throw new NoSuchElementException("Have name that is not matched at all: " + name);
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
                            theItem.getAnnotations().add(annotationBuilder.build());
                        } else {
                            LOGGER.warn("Attribute 'name' missing?");
                        }
                    }
                }
            }
        }
        return countAnnotations;
    }


    private static ParameterItem parameterItem(MethodItem methodItem, String parameterIndex) {
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


    private static MethodItem companionMethodItem(MethodItem methodItem, String companionName, String companionReturnType) {
        MethodItem companionItem = methodItem.getCompanionMethod(companionName);
        if (companionItem == null) {
            companionItem = new MethodItem(companionName, companionReturnType);
            methodItem.putCompanionMethod(companionItem);
            log(ANNOTATION_XML_READER, "Created companion method {} returns {}", companionName, companionReturnType);
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

                methodItem.getParameterItems().forEach(parameterItem ->
                        parameterItem.getAnnotations().forEach(annotation ->
                                increment(annotationCounts, "P " + annotation.name())));
            });
            typeItem.getFieldItems().values().forEach(fieldItem ->
                    fieldItem.getAnnotations().forEach(annotation -> increment(annotationCounts, "F " + annotation.name())));
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
    public int getNumberOfAnnotations() {
        return numberOfAnnotations;
    }
}
