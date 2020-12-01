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

import org.e2immu.analyser.annotationxml.model.*;
import org.e2immu.analyser.config.AnnotationXmlConfiguration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.SMapList;
import org.e2immu.annotation.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.ANNOTATION_XML_WRITER;
import static org.e2immu.analyser.util.Logger.log;

@UtilityClass
public class AnnotationXmlWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationXmlWriter.class);

    private AnnotationXmlWriter() {
        // nothing here
    }

    public static void write(AnnotationXmlConfiguration configuration, Set<TypeInfo> typesToWrite) throws IOException {
        File base;
        if (configuration.writeAnnotationXmlDir != null) {
            base = new File(configuration.writeAnnotationXmlDir);
            if (!base.isDirectory()) {
                LOGGER.info("Creating directory {}", base);
                if (!base.mkdirs()) {
                    throw new IOException("Somehow failed to create " + base.getAbsolutePath());
                }
            }
        } else {
            base = new File(System.getProperty("user.dir"));
        }
        Map<String, List<TypeItem>> typeItemsPerPackage = new HashMap<>();
        boolean isEmpty = configuration.writeAnnotationXmlPackages.isEmpty();
        typesToWrite.forEach(typeInfo -> {
            boolean accept = isEmpty;
            String packageName = typeInfo.packageName();
            if (packageName != null) {
                for (String prefix : configuration.writeAnnotationXmlPackages) {
                    if (prefix.endsWith(".")) {
                        accept = packageName.startsWith(prefix.substring(0, prefix.length() - 1));
                    } else {
                        accept = prefix.equals(packageName);
                    }
                    if (accept) break;
                }
                if (accept) SMapList.add(typeItemsPerPackage, packageName, new TypeItem(typeInfo));
            }
        });
        writeAllPackages(base, typeItemsPerPackage);
    }

    private static void writeAllPackages(File base, Map<String, List<TypeItem>> typeItemsPerPackage) throws IOException {
        for (Map.Entry<String, List<TypeItem>> entry : typeItemsPerPackage.entrySet()) {
            String[] splitOfPackage = entry.getKey().split("\\.");
            File directory = new File(base, String.join("/", splitOfPackage));
            boolean created = directory.mkdirs();
            log(ANNOTATION_XML_WRITER, "Created {}? {}", directory, created);
            File outputFile = new File(directory, "annotations.xml");
            writeSinglePackage(outputFile, entry.getValue());
        }
    }

    public static void writeSinglePackage(File outputFile, Collection<TypeItem> typeItems) throws IOException {
        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document document = documentBuilder.newDocument();
            Element root = document.createElement("root");
            document.appendChild(root);
            typeItems.stream().sorted().forEach(typeItem -> add(document, root, typeItem));
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource domSource = new DOMSource(document);
            StreamResult streamResult = new StreamResult(outputFile);
            transformer.transform(domSource, streamResult);
        } catch (ParserConfigurationException | TransformerException e) {
            throw new IOException("Problems with XML writing: " + e.getMessage());
        }
    }

    private static void add(Document document, Element root, TypeItem typeItem) {
        if (!typeItem.getAnnotations().isEmpty()) {
            add(document, root, typeItem.name, typeItem.getAnnotations(), null);
        }
        typeItem.getFieldItems().values().stream().sorted().forEach(fieldItem ->
                add(document, root, typeItem.name, fieldItem));
        typeItem.getMethodItems().values().stream().sorted().forEach(methodItem ->
                add(document, root, typeItem.name, methodItem));
    }

    private static void add(Document document, Element root, String typeName, FieldItem fieldItem) {
        if (!fieldItem.getAnnotations().isEmpty()) {
            String fieldName = typeName + " " + fieldItem.name;
            add(document, root, fieldName, fieldItem.getAnnotations(), null);
        }
    }

    private static void add(Document document, Element root, String typeName, MethodItem methodItem) {
        String methodName = typeName + (methodItem.returnType != null ? " " + methodItem.returnType : "") + " " + methodItem.name;
        if (!methodItem.getAnnotations().isEmpty()) {
            // companions don't have annotations
            add(document, root, methodName, methodItem.getAnnotations(), null);
        }
        if (!methodItem.getParameterItems().isEmpty()) {
            methodItem.getParameterItems().stream().sorted().forEach(parameterItem -> add(document, root, methodName, parameterItem));
        }
        for (MethodItem companionItem : methodItem.getCompanionMethods()) {
            add(document, root, companionItem.name, List.of(), companionItem.companionValue);
        }
    }

    private static void add(Document document, Element root, String methodName, ParameterItem parameterItem) {
        if (!parameterItem.getAnnotations().isEmpty()) {
            String parameterName = methodName + " " + parameterItem.index;
            add(document, root, parameterName, parameterItem.getAnnotations(), null);
        }
    }

    private static void add(Document document, Element root, String itemName, Collection<Annotation> annotations, String companionValue) {
        Element item = document.createElement("item");
        item.setAttribute("name", itemName);
        if (companionValue != null && !companionValue.isBlank()) {
            Element definitionElement = document.createElement("definition");
            definitionElement.setTextContent(companionValue);
            item.appendChild(definitionElement);
        }
        root.appendChild(item);
        for (Annotation annotation : annotations) {
            Element annotationElement = document.createElement("annotation");
            annotationElement.setAttribute("name", annotation.name());
            item.appendChild(annotationElement);
            for (Value value : annotation.values()) {
                Element val = document.createElement("val");
                if (value.name != null) val.setAttribute("name", value.name);
                val.setAttribute("val", value.val);
                annotationElement.appendChild(val);
            }
        }
    }
}
