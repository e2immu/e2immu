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

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParameterizedTypeFactory {
    @Nullable
    public static ParameterizedType fromDoNotComplain(TypeContext context, Type type) {
        return from(context, type, ParameterizedType.WildCard.NONE, false, null, false);
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type) {
        return from(context, type, ParameterizedType.WildCard.NONE, false, null, true);
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type, boolean varargs, DollarResolver dollarResolver) {
        return from(context, type, ParameterizedType.WildCard.NONE, varargs, dollarResolver, true);
    }

    private static ParameterizedType from(TypeContext context,
                                          Type type,
                                          ParameterizedType.WildCard wildCard,
                                          boolean varargs,
                                          DollarResolver dollarResolver,
                                          boolean complain) {
        Type baseType = type;
        int arrays = 0;
        if (type.isArrayType()) {
            ArrayType arrayType = (ArrayType) type;
            arrays = arrayType.getArrayLevel();
            baseType = arrayType.getComponentType();
            while (baseType.isArrayType()) {
                baseType = baseType.asArrayType().getComponentType();
            }
        }
        if (varargs) arrays++; // if we have a varargs type Object..., we store a parameterized type Object[]
        if (baseType.isPrimitiveType()) {
            return new ParameterizedType(context.getPrimitives().primitiveByName(baseType.asString()), arrays);
        }
        if (type instanceof WildcardType wildcardType) {
            if (wildcardType.getExtendedType().isPresent()) {
                // ? extends T
                return from(context, wildcardType.getExtendedType().get(), ParameterizedType.WildCard.EXTENDS, false, dollarResolver, complain);
            }
            if (wildcardType.getSuperType().isPresent()) {
                // ? super T
                return from(context, wildcardType.getSuperType().get(), ParameterizedType.WildCard.SUPER, false, dollarResolver, complain);
            }
            return ParameterizedType.WILDCARD_PARAMETERIZED_TYPE; // <?>
        }
        if ("void".equals(baseType.asString())) return context.getPrimitives().voidParameterizedType();

        String name;
        List<ParameterizedType> parameters = new ArrayList<>();
        if (baseType instanceof ClassOrInterfaceType cit) {
            name = cit.getName().getIdentifier();
            if (cit.getTypeArguments().isPresent()) {
                for (Type typeArgument : cit.getTypeArguments().get()) {
                    ParameterizedType subPt = from(context, typeArgument, ParameterizedType.WildCard.NONE, false,
                            null, true);
                    parameters.add(subPt);
                }
            }
            if (cit.getScope().isPresent()) {
                // first, check for a FQN
                Type scopeType = cit.getScope().get();
                String fqn = scopeType.asString() + "." + name;
                TypeInfo typeInfo = context.getFullyQualified(fqn, false);
                if (typeInfo != null) {
                    return parameters.isEmpty()
                            ? new ParameterizedType(typeInfo, arrays)
                            : new ParameterizedType(typeInfo, parameters);
                }
                ParameterizedType scopePt = from(context, scopeType, ParameterizedType.WildCard.NONE, false,
                        null, complain);
                // name probably is a subtype in scopePt... (Map.Entry)
                if (scopePt != null && scopePt.typeInfo != null) {
                    return findSubType(context, arrays, name, parameters, scopePt, complain);
                }
            }
        } else {
            name = baseType.asString();
        }
        NamedType namedType = dollarResolver == null ? null : dollarResolver.apply(name);
        if (namedType == null) namedType = context.get(name, false);
        if (namedType instanceof TypeInfo typeInfo) {
            if (parameters.isEmpty()) {
                return new ParameterizedType(typeInfo, arrays);
            }
            // Set<T>... == Set<T>[]
            return new ParameterizedType(typeInfo, arrays, wildCard, parameters);
        }
        if (namedType instanceof TypeParameter typeParameter) {
            if (!parameters.isEmpty()) {
                throw new UnsupportedOperationException("??");
            }
            return new ParameterizedType(typeParameter, arrays, wildCard);
        }
        if (complain) {
            throw new UnsupportedOperationException("Unknown type: " + name + " at line "
                    + baseType.getBegin() + " of " + baseType.getClass());
        }
        return null;
    }


    private static ParameterizedType findSubType(TypeContext typeContext,
                                                 int arrays,
                                                 String name,
                                                 List<ParameterizedType> parameters,
                                                 ParameterizedType scopePt,
                                                 boolean complain) {
        TypeInspection scopeInspection = typeContext.getTypeInspection(scopePt.typeInfo);
        if (scopeInspection != null) {
            Optional<TypeInfo> subType = scopeInspection.subTypes().stream().filter(st -> st.simpleName.equals(name)).findFirst();
            if (subType.isPresent()) {
                return new ParameterizedType(subType.get(), parameters, arrays);
            }

            ParameterizedType parent = scopeInspection.parentClass();
            if (parent != null && !parent.isJavaLangObject()) {
                ParameterizedType res = findSubType(typeContext, arrays, name, parameters, parent, false);
                if (res != null) return res;
            }
            for (ParameterizedType implementedInterface : scopeInspection.interfacesImplemented()) {
                ParameterizedType res = findSubType(typeContext, arrays, name, parameters, implementedInterface, false);
                if (res != null) return res;
            }
            if (complain) {
                throw new UnsupportedOperationException("Cannot find " + name + " in " + scopePt);
            }
            return null;
        }
        // we're going to assume that we're creating a subtype
        TypeInfo subType = typeContext.typeMap().getOrCreateByteCode(scopePt.typeInfo.fullyQualifiedName, name);
        return new ParameterizedType(subType, parameters, arrays);
    }
}
