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
