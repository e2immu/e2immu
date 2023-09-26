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
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnnotationXmlReader implements AnnotationStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationXmlReader.class);
    public static final String ANNOTATIONS_XML = "annotations.xml";
    public static final String ANNOTATIONS_DOT_XML = "annotations\\.xml";

    public final Map<String, TypeItem> typeItemMap;
    public final int numberOfAnnotations;

    private static final Pattern TYPE = Pattern.compile("(\\p{Alpha}[^\\s<>]+)");
    private static final String METHOD_AND_BRACKETS = "(\\p{Alpha}[^\\s()<>]+\\([^)]*\\))";

    static final Pattern CONSTRUCTOR = Pattern.compile(TYPE + " " + METHOD_AND_BRACKETS);
    static final Pattern METHOD = Pattern.compile(TYPE + " ([^()]+ )?" + METHOD_AND_BRACKETS);
    static final Pattern COMPANION_METHOD = Pattern.compile("(static )?(<([^>]+)> )?(\\p{Alpha}[^()]+ )?" + METHOD_AND_BRACKETS);

    static final Pattern METHOD_WITH_COMPANION = Pattern.compile("(.+) :: (.+)");
    static final Pattern METHOD_WITH_PARAMETER = Pattern.compile("(.+) (\\d+)");

    private static final Pattern FIELD = Pattern.compile(TYPE + " ([^\\s()]+)");

    public AnnotationXmlReader(Resources classPath) {
        this(classPath, new AnnotationXmlConfiguration.Builder().build());
    }

    public AnnotationXmlReader(Resources classPath, AnnotationXmlConfiguration configuration) {
        Map<String, TypeItem> typeItemMap = new HashMap<>();
        int countAnnotations = 0;
        if (configuration.isReadAnnotationXmlPackages()) {
            List<Pattern> restrictToPatterns = computeRestrictionPatterns(configuration.readAnnotationXmlPackages());
            for (URI uri : classPath.expandURLs(ANNOTATIONS_XML)) {
                try {
                    URL url = uri.toURL();
                    if (accept(url, restrictToPatterns)) {
                        countAnnotations += parse(url, typeItemMap);
                    }
                } catch (IOException io) {
                    LOGGER.warn("Skipping {}: IOException {}", uri, io.getMessage());
                } catch (SAXException e) {
                    LOGGER.warn("Skipping {}: SAXException {}", uri, e.getMessage());
                } catch (ParserConfigurationException e) {
                    LOGGER.warn("Skipping {}: Parser config exception {}", uri, e.getMessage());
                }
            }
        }
        this.numberOfAnnotations = countAnnotations;
        this.typeItemMap = Map.copyOf(typeItemMap);
        this.typeItemMap.values().forEach(TypeItem::freeze);
    }

    /*
    restriction pattern: java.util  java.util. (trailing dot meaning all sub-packages)
    input: a file URL with /annotations.xml at the end
     */
    private List<Pattern> computeRestrictionPatterns(List<String> readAnnotationXmlPackages) {
        return readAnnotationXmlPackages.stream()
                .map(s -> {
                    String in = s.endsWith(".")
                            ? s.substring(0, s.length() - 1).replace(".", "/") + "(/.*)?"
                            : s.replace(".", "/");
                    String pattern = ".+" + "/" + in + "/" + ANNOTATIONS_DOT_XML;
                    return Pattern.compile(pattern);
                }).toList();
    }

    private boolean accept(URL url, List<Pattern> restrictToPatterns) {
        if (restrictToPatterns.isEmpty()) return true;
        String file = url.getFile();
        return restrictToPatterns.stream().anyMatch(p -> p.matcher(file).matches());
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
        LOGGER.debug("Parsing {}", annotationXml);
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
                boolean companionStatic = false;
                String companionTypeParametersCsv = null;
                String companionType = null;
                String companionName = null;

                Matcher typeMatcher = TYPE.matcher(name);
                if (typeMatcher.matches()) {
                    theItem = typeItem(typeItemMap, typeMatcher.group());
                } else {
                    Matcher methodWithCompanion = METHOD_WITH_COMPANION.matcher(name);
                    String methodString;
                    String paramIndex = null;
                    if (methodWithCompanion.matches()) {
                        methodString = methodWithCompanion.group(1);
                        Matcher companionMatcher = COMPANION_METHOD.matcher(methodWithCompanion.group(2));
                        if (companionMatcher.matches()) {
                            companionStatic = "static".equals(companionMatcher.group(1));
                            companionTypeParametersCsv = companionMatcher.group(3);
                            companionType = companionMatcher.group(4).trim();
                            companionName = companionMatcher.group(5);
                        }
                    } else {
                        Matcher methodWithParameter = METHOD_WITH_PARAMETER.matcher(name);
                        if (methodWithParameter.matches()) {
                            methodString = methodWithParameter.group(1);
                            paramIndex = methodWithParameter.group(2);
                        } else {
                            methodString = name;
                        }
                    }
                    Matcher constructorMatcher = CONSTRUCTOR.matcher(methodString);
                    if (constructorMatcher.matches()) {
                        TypeItem typeItem = typeItem(typeItemMap, constructorMatcher.group(1));
                        String constructorName = constructorMatcher.group(2);
                        MethodItem methodItem = methodItem(typeItem, constructorName, null);
                        if (paramIndex != null) {
                            theItem = parameterItem(methodItem, paramIndex);
                        } else {
                            theItem = methodItem;
                        }
                    } else {
                        Matcher methodMatcher = METHOD.matcher(methodString);
                        if (methodMatcher.matches()) {
                            TypeItem typeItem = typeItem(typeItemMap, methodMatcher.group(1));
                            String methodType = methodMatcher.group(2);
                            String cleanMethodType = methodType == null ? null : methodType.trim();
                            String methodName = methodMatcher.group(3);
                            MethodItem methodItem = methodItem(typeItem, methodName, cleanMethodType);
                            if (paramIndex != null) {
                                theItem = parameterItem(methodItem, paramIndex);
                            } else {
                                theItem = methodItem;
                            }
                            // companion is done later, because we need to read an additional element
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
                    Node subNode = annotationNodes.item(j);
                    if (subNode.getNodeType() == Node.ELEMENT_NODE &&
                            "annotation".equalsIgnoreCase(subNode.getNodeName())) {
                        Node nameAttribute = subNode.getAttributes().getNamedItem("name");
                        if (nameAttribute != null) {
                            String annotationType = nameAttribute.getNodeValue();
                            Annotation.Builder annotationBuilder = new Annotation.Builder(annotationType);

                            countAnnotations++;
                            NodeList valueNodes = subNode.getChildNodes();
                            for (int k = 0; k < valueNodes.getLength(); k++) {
                                Node valueNode = valueNodes.item(k);
                                if (valueNode.getNodeType() == Node.ELEMENT_NODE) {
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
                    } else if (subNode.getNodeType() == Node.ELEMENT_NODE &&
                            "definition".equalsIgnoreCase(subNode.getNodeName())) {
                        Node paramNamesAttribute = subNode.getAttributes().getNamedItem("paramNames");
                        String paramNamesCsv;
                        if (paramNamesAttribute != null) {
                            paramNamesCsv = paramNamesAttribute.getNodeValue();
                        } else {
                            paramNamesCsv = "";
                        }
                        String function = subNode.getTextContent();
                        if (function == null) {
                            LOGGER.error("No function value in {}", companionName);
                        } else {
                            assert theItem instanceof MethodItem;
                            assert companionName != null;
                            assert companionType != null;
                            MethodItem methodItem = (MethodItem) theItem;
                            addCompanionMethodItem(methodItem,
                                    companionStatic, companionTypeParametersCsv,
                                    companionType, companionName, paramNamesCsv, function);
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
            if (returnType == null) {
                LOGGER.debug("Created constructor {}", methodName);
            } else {
                LOGGER.debug("Created method {} returns {}", methodName, returnType);
            }
        }
        return methodItem;
    }

    private static void addCompanionMethodItem(MethodItem methodItem,
                                               boolean companionStatic,
                                               String companionTypeParametersCsv,
                                               String companionReturnType,
                                               String companionName,
                                               String parameterNamesCsv,
                                               String function) {
        MethodItem companionItem = methodItem.getCompanionMethod(companionName);
        if (companionItem != null) {
            throw new UnsupportedOperationException("?duplicating " + companionName);
        }
        companionItem = new MethodItem(companionStatic, companionTypeParametersCsv,
                companionReturnType, companionName, parameterNamesCsv, function);
        methodItem.putCompanionMethod(companionItem);
        LOGGER.debug("Created companion method {} returns {}", companionName, companionReturnType);
    }

    private static FieldItem fieldItem(TypeItem typeItem, String fieldName) {
        FieldItem fieldItem = typeItem.getFieldItems().get(fieldName);
        if (fieldItem == null) {
            fieldItem = new FieldItem(fieldName);
            typeItem.getFieldItems().put(fieldName, fieldItem);
            LOGGER.debug("Created field {}", fieldName);
        }
        return fieldItem;
    }

    private static TypeItem typeItem(Map<String, TypeItem> typeItemMap, String name) {
        TypeItem typeItem = typeItemMap.get(name);
        if (typeItem == null) {
            typeItem = new TypeItem(name);
            typeItemMap.put(name, typeItem);
            LOGGER.debug("Created type {}", name);
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
