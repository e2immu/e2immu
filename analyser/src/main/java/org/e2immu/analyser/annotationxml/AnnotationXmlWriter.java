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


@UtilityClass
public class AnnotationXmlWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationXmlWriter.class);

    private AnnotationXmlWriter() {
        // nothing here
    }

    public static void write(AnnotationXmlConfiguration configuration, Set<TypeInfo> typesToWrite) throws IOException {
        File base;
        if (configuration.writeAnnotationXmlDir() != null) {
            base = new File(configuration.writeAnnotationXmlDir());
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
        boolean isEmpty = configuration.writeAnnotationXmlPackages().isEmpty();
        typesToWrite.forEach(typeInfo -> {
            boolean accept = isEmpty;
            String packageName = typeInfo.packageName();
            if (packageName != null) {
                for (String prefix : configuration.writeAnnotationXmlPackages()) {
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
            LOGGER.debug("Created {}? {}", directory, created);
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
            typeItems.stream().sorted().forEach(typeItem -> addType(document, root, typeItem));
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

    private static void addType(Document document, Element root, TypeItem typeItem) {
        LOGGER.debug("Type {} has {} annots, {} fields, {} methods",
                typeItem.name, typeItem.getAnnotations().size(),
                typeItem.getFieldItems().size(), typeItem.getMethodItems().size());
        if (!typeItem.getAnnotations().isEmpty()) {
            emit(document, root, typeItem.name, typeItem.getAnnotations(), null, null);
        }
        typeItem.getFieldItems().values().stream().sorted().forEach(fieldItem ->
                addField(document, root, typeItem.name, fieldItem));
        typeItem.getMethodItems().values().stream().sorted().forEach(methodItem ->
                addMethod(document, root, typeItem.name, methodItem));
    }

    private static void addField(Document document, Element root, String typeName, FieldItem fieldItem) {
        if (!fieldItem.getAnnotations().isEmpty()) {
            String fieldName = typeName + " " + fieldItem.name;
            emit(document, root, fieldName, fieldItem.getAnnotations(), null, null);
        }
    }

    private static void addMethod(Document document, Element root, String typeName, MethodItem methodItem) {
        String returnType = methodItem.returnType != null ? (" " + methodItem.returnType) : "";
        String methodName = typeName + returnType + " " + methodItem.name;
        if (!methodItem.getAnnotations().isEmpty()) {
            emit(document, root, methodName, methodItem.getAnnotations(), null, null);
        }
        if (!methodItem.getParameterItems().isEmpty()) {
            methodItem.getParameterItems().stream().sorted().forEach(parameterItem ->
                    addParameter(document, root, methodName, parameterItem));
        }
        for (MethodItem companionItem : methodItem.getCompanionMethods()) {
            // companions don't have annotations; we also make it obvious they belong to a method
            String companionName = (companionItem.isStatic ? "static " : "") +
                    (companionItem.typeParametersCsv.isEmpty() ? "" : "<" + companionItem.typeParametersCsv + "> ") +
                    (companionItem.returnType != null ? companionItem.returnType + " " : "") +
                    companionItem.name;
            emit(document, root, methodName + " :: " + companionName, List.of(),
                    companionItem.companionValue, companionItem.paramNamesCsv);
        }
        assert !methodName.contains("  ");
    }

    private static void addParameter(Document document, Element root, String methodName, ParameterItem parameterItem) {
        if (!parameterItem.getAnnotations().isEmpty()) {
            String parameterName = methodName + " " + parameterItem.index;
            emit(document, root, parameterName, parameterItem.getAnnotations(), null, null);
        }
    }

    private static void emit(Document document,
                             Element root,
                             String itemName,
                             Collection<Annotation> annotations,
                             String companionExpression,
                             String paramNamesCsv) {
        Element item = document.createElement("item");
        item.setAttribute("name", itemName);
        if (companionExpression != null && !companionExpression.isBlank()) {
            Element definitionElement = document.createElement("definition");
            definitionElement.setAttribute("paramNames", paramNamesCsv);
            definitionElement.setTextContent(companionExpression);
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
