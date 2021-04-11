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

package org.e2immu.analyser.annotatedapi;

import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.FieldModifier;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.StringUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;

/*
Given a number of types, compose one annotated API "file" per package, in the style of the JavaUtil, JavaLang classes.
The file is generated in the form of a TypeInfo object, which can be output.

The general structure is

public class NameOfPackageWithoutDots {
    final static String PACKAGE_NAME = "name.of.package.without.dots";

    // the $ means that we'll relocate towards PACKAGE_NAME

    public static class/public interface Type1Name$ {
        public methods or constructors
            methods return { null } or the correct primary type
    }
    ...
    public static class/public interface Type2Name$ {

        public class InnerType { // doesn't need a $ anymore

        }
    }
 }

- The purpose of this class is to generate an AnnotatedAPI file for others to start editing.
  This can be run on byte-code inspected Java, meaning the JavaParser needn't used, so we can do Java 16 already.

- Only public methods, types and fields will be shown.

 */
public record Composer(TypeMap typeMap, String destinationPackage) {

    public Collection<TypeInfo> compose(Collection<TypeInfo> primaryTypes) {
        Map<String, TypeInspectionImpl.Builder> buildersPerPackage = new HashMap<>();
        for (TypeInfo primaryType : primaryTypes) {
            assert primaryType.isPrimaryType();
            String packageName = primaryType.packageName();
            TypeInspectionImpl.Builder builder = buildersPerPackage.computeIfAbsent(packageName,
                    this::newBuilder);
            appendType(primaryType, builder);
        }
        return buildersPerPackage.values().stream()
                .map(builder -> builder.build().typeInfo)
                .toList();
    }

    private void appendType(TypeInfo primaryType, TypeInspectionImpl.Builder builder) {
    }

    private TypeInspectionImpl.Builder newBuilder(String packageName) {
        String camelCasePackageName = convertToCamelCase(packageName);
        TypeInfo typeInfo = new TypeInfo(destinationPackage, camelCasePackageName);
        TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND);
        FieldInfo packageField = new FieldInfo(typeMap.getPrimitives().stringParameterizedType,
                "PACKAGE_NAME", typeInfo);
        FieldInspectionImpl.Builder packageFieldInspectionBuilder = new FieldInspectionImpl.Builder();
        packageFieldInspectionBuilder
                .addModifier(FieldModifier.FINAL).addModifier(FieldModifier.STATIC).addModifier(FieldModifier.PUBLIC)
                .setInspectedInitialiserExpression(new StringConstant(typeMap.getPrimitives(), packageName));
        packageField.fieldInspection.set(packageFieldInspectionBuilder.build());
        builder.addField(packageField);
        return builder;
    }

    static String convertToCamelCase(String packageName) {
        String[] components = packageName.split("\\.");
        return Arrays.stream(components).map(StringUtil::capitalise).collect(Collectors.joining());
    }
}
