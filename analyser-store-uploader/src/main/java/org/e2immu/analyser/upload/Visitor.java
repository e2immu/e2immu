/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.upload;

import org.e2immu.analyser.annotationxml.AnnotationXmlWriter;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.visitor.SortedTypeListVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Visitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(Visitor.class);

    private void writeAndUpload(SortedTypeListVisitor.Data data) {
        Set<TypeInfo> typesToWrite = data.sortedTypes().stream()
                .map(SortedType::primaryType).collect(Collectors.toSet());
        if (data.configuration().uploadConfiguration.upload) {
            AnnotationUploader annotationUploader = new AnnotationUploader(data.configuration().uploadConfiguration,
                    data.input().globalTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions());
            Map<String, String> map = annotationUploader.createMap(typesToWrite);
            annotationUploader.writeMap(map);
        }
        if (data.configuration().annotationXmlConfiguration.writeAnnotationXml) {
            try {
                AnnotationXmlWriter.write(data.configuration().annotationXmlConfiguration, typesToWrite);
            } catch (IOException ioe) {
                LOGGER.error("Caught ioe exception writing annotation XMLs");
                throw new RuntimeException(ioe);
            }
        }
    }
}
