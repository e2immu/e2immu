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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.annotationxml.model.FieldItem;
import org.e2immu.analyser.annotationxml.model.MethodItem;
import org.e2immu.analyser.annotationxml.model.TypeItem;
import org.e2immu.analyser.bytecode.ExpressionFactory;
import org.e2immu.analyser.bytecode.JetBrainsAnnotationTranslator;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.Source;
import org.e2immu.analyser.util.StringUtil;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.InspectionState.*;
import static org.objectweb.asm.Opcodes.ASM9;

public class MyClassVisitor extends ClassVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyClassVisitor.class);
    private static final Pattern ILLEGAL_IN_FQN = Pattern.compile("[/;$]");
    private final TypeContext typeContext;
    private final LocalTypeMap localTypeMap;
    private final AnnotationStore annotationStore;
    private final JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator;
    private final Source pathAndURI;
    private TypeInfo currentType;
    private String currentTypePath;
    private boolean currentTypeIsInterface;
    private TypeInspection.Builder typeInspectionBuilder;

    public MyClassVisitor(LocalTypeMap localTypeMap,
                          AnnotationStore annotationStore,
                          TypeContext typeContext,
                          Source pathAndURI) {
        super(ASM9);
        this.typeContext = typeContext;
        this.localTypeMap = localTypeMap;
        this.pathAndURI = pathAndURI;
        this.annotationStore = annotationStore;
        jetBrainsAnnotationTranslator = annotationStore != null
                ? new JetBrainsAnnotationTranslator(typeContext.getPrimitives(),
                typeContext.typeMap.getE2ImmuAnnotationExpressions()) : null;
    }

    // return true when child = parent + $ + somethingWithoutDollars
    static boolean isDirectChildOf(String child, String parent) {
        if (!child.startsWith(parent)) return false;
        int dollar = parent.length();
        if (child.length() <= dollar + 1) return false;
        int otherDollar = child.indexOf('$', dollar + 1);
        return otherDollar < 0;
    }

    public static String pathToFqn(String path) {
        return StringUtil.stripDotClass(path).replaceAll("[/$]", ".");
    }

    private static TypeNature typeNatureFromOpCode(int opCode) {
        if ((opCode & Opcodes.ACC_ANNOTATION) != 0) return TypeNature.ANNOTATION;
        if ((opCode & Opcodes.ACC_ENUM) != 0) return TypeNature.ENUM;
        if ((opCode & Opcodes.ACC_INTERFACE) != 0) return TypeNature.INTERFACE;
        if ((opCode & Opcodes.ACC_RECORD) != 0) return TypeNature.RECORD;
        return TypeNature.CLASS;
    }

    private String makeMethodSignature(String name, TypeInfo typeInfo, List<ParameterizedType> types) {
        String methodName = "<init>".equals(name) ? typeInfo.simpleName : name;
        return methodName + "(" +
                types.stream().map(pt -> pt.detailedString(localTypeMap)).collect(Collectors.joining(", ")) +
                ")";
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        LOGGER.debug("Visit {} {} {} {} {} {}", version, access, name, signature, superName, interfaces);
        String fqName = pathToFqn(name);
        assert Input.acceptFQN(fqName);
        TypeMap.InspectionAndState situation = localTypeMap.typeInspectionSituation(fqName);
        assert situation != null && situation.state() == STARTING_BYTECODE;

        typeInspectionBuilder = (TypeInspectionImpl.Builder) situation.typeInspection();
        currentType = typeInspectionBuilder.typeInfo();
        assert currentType != null;
        currentTypePath = name;

        // may be overwritten, but this is the default UNLESS it's JLO itself
        if (!currentType.isJavaLangObject()) {
            typeInspectionBuilder.noParent(typeContext.getPrimitives());
        }

        TypeNature currentTypeNature = typeNatureFromOpCode(access);
        typeInspectionBuilder.setTypeNature(currentTypeNature);
        currentTypeIsInterface = currentTypeNature == TypeNature.INTERFACE;

        checkTypeFlags(access, typeInspectionBuilder);
        if (currentTypeNature == TypeNature.CLASS) {
            if ((access & Opcodes.ACC_ABSTRACT) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.ABSTRACT);
            if ((access & Opcodes.ACC_FINAL) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.FINAL);
        }
        typeInspectionBuilder.computeAccess(localTypeMap);

        String parentFqName = superName == null ? null : pathToFqn(superName);
        if (parentFqName != null && !Input.acceptFQN(parentFqName)) {
            return;
        }
        if (signature == null) {
            if (superName != null) {
                TypeInfo typeInfo = mustFindTypeInfo(parentFqName, superName);
                if (typeInfo == null) {
                    LOGGER.debug("Stop inspection of {}, parent type {} unknown",
                            currentType.fullyQualifiedName, parentFqName);
                    errorStateForType(parentFqName);
                    return;
                }
                typeInspectionBuilder.setParentClass(typeInfo.asParameterizedType(localTypeMap));
            } else {
                LOGGER.debug("No parent name for {}", fqName);
            }
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    String fqn = pathToFqn(interfaceName);
                    if (Input.acceptFQN(fqn)) {
                        TypeInfo typeInfo = mustFindTypeInfo(fqn, interfaceName);
                        if (typeInfo == null) {
                            LOGGER.debug("Stop inspection of {}, interface type {} unknown",
                                    currentType.fullyQualifiedName, fqn);
                            errorStateForType(fqn);
                            return;
                        }
                        typeInspectionBuilder.addInterfaceImplemented(typeInfo.asParameterizedType(localTypeMap));
                    } // else: ignore!
                }
            }
        } else {
            try {
                int pos = 0;
                if (signature.charAt(0) == '<') {
                    ParseGenerics parseGenerics = new ParseGenerics(typeContext, currentType, typeInspectionBuilder,
                            localTypeMap, true);
                    pos = parseGenerics.parseTypeGenerics(signature) + 1;
                }
                {
                    ParameterizedTypeFactory.Result res = ParameterizedTypeFactory.from(typeContext,
                            localTypeMap, true, signature.substring(pos));
                    if (res == null) {
                        LOGGER.debug("Stop inspection of {}, parent type unknown",
                                currentType.fullyQualifiedName);
                        errorStateForType(parentFqName);
                        return;
                    }
                    typeInspectionBuilder.setParentClass(res.parameterizedType);
                    pos += res.nextPos;
                }
                if (interfaces != null) {
                    for (int i = 0; i < interfaces.length; i++) {
                        ParameterizedTypeFactory.Result interFaceRes = ParameterizedTypeFactory.from(typeContext,
                                localTypeMap, true, signature.substring(pos));
                        if (interFaceRes == null) {
                            LOGGER.debug("Stop inspection of {}, interface type unknown",
                                    currentType.fullyQualifiedName);
                            errorStateForType(parentFqName);
                            return;
                        }
                        if (typeContext.getPrimitives().objectTypeInfo() != interFaceRes.parameterizedType.typeInfo) {
                            typeInspectionBuilder.addInterfaceImplemented(interFaceRes.parameterizedType);
                        }
                        pos += interFaceRes.nextPos;
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.error("Caught exception while parsing signature " + signature);
                throw e;
            }
        }

        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(fqName);
            if (typeItem != null && !typeItem.getAnnotations().isEmpty()) {
                jetBrainsAnnotationTranslator.mapAnnotations(typeItem.getAnnotations(), typeInspectionBuilder);
            }
        }
    }

    /**
     * Both parameters are two versions of the same type reference
     *
     * @param fqn  dot-separated
     * @param path / and $ separated
     * @return the type
     */
    private TypeInfo mustFindTypeInfo(String fqn, String path) {
        if (path.equals(currentTypePath)) {
            return currentType;
        }
        return localTypeMap.getOrCreate(fqn, true).typeInfo();
    }

    private TypeInfo getOrCreateTypeInfo(String fqn, String path) {
        if (!Input.acceptFQN(fqn)) return null;
        Matcher m = ILLEGAL_IN_FQN.matcher(fqn);
        if (m.find()) throw new UnsupportedOperationException("Illegal FQN: " + fqn + "; path is " + path);
        // this causes really heavy recursions: return mustFindTypeInfo(fqn, path);
        Source newPath = localTypeMap.fqnToPath(fqn);
        if (newPath == null) {
            LOGGER.debug("Ignoring type {}", fqn);
            return null;
        }
        return localTypeMap.inspectFromPath(newPath, typeContext, false).typeInfo();
    }


    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (currentType == null) return null;
        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        LOGGER.debug("Field {} {} desc='{}' sig='{}' {} synthetic? {}", access,
                name, descriptor, signature, value, synthetic);
        if (synthetic) return null;

        ParameterizedTypeFactory.Result from = ParameterizedTypeFactory.from(typeContext, localTypeMap, false,
                signature != null ? signature : descriptor);
        if (from == null) return null; // jdk
        ParameterizedType type = from.parameterizedType;

        FieldInfo fieldInfo = new FieldInfo(Identifier.generate("asm field"), type, name, currentType);
        FieldInspection.Builder fieldInspectionBuilder = new FieldInspectionImpl.Builder(fieldInfo);
        localTypeMap.registerFieldInspection(fieldInfo, fieldInspectionBuilder);

        if ((access & Opcodes.ACC_STATIC) != 0) fieldInspectionBuilder.addModifier(FieldModifier.STATIC);
        if ((access & Opcodes.ACC_PUBLIC) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PUBLIC);
        if ((access & Opcodes.ACC_PRIVATE) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PROTECTED);
        if ((access & Opcodes.ACC_FINAL) != 0) fieldInspectionBuilder.addModifier(FieldModifier.FINAL);
        if ((access & Opcodes.ACC_VOLATILE) != 0) fieldInspectionBuilder.addModifier(FieldModifier.VOLATILE);
        if ((access & Opcodes.ACC_ENUM) != 0) fieldInspectionBuilder.setSynthetic(true); // what we use synthetic for

        if (value != null) {
            Expression expression = ExpressionFactory.from(localTypeMap, Identifier.constant(value), value);
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                fieldInspectionBuilder.setInspectedInitialiserExpression(expression);
            }
        }

        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(currentType.fullyQualifiedName);
            if (typeItem != null) {
                FieldItem fieldItem = typeItem.getFieldItems().get(name);
                if (fieldItem != null && !fieldItem.getAnnotations().isEmpty()) {
                    jetBrainsAnnotationTranslator.mapAnnotations(fieldItem.getAnnotations(), fieldInspectionBuilder);
                }
            }
        }

        return new MyFieldVisitor(typeContext, fieldInfo, localTypeMap, fieldInspectionBuilder, typeInspectionBuilder);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (currentType == null) return null;

        if (name.startsWith("lambda$") || name.equals("<clinit>")) {
            return null;
        }

        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        LOGGER.debug("Method {} {} desc='{}' sig='{}' {} synthetic? {}", access, name,
                descriptor, signature, Arrays.toString(exceptions), synthetic);
        if (synthetic) return null;

        MethodInspectionImpl.Builder methodInspectionBuilder;
        if ("<init>".equals(name)) {
            methodInspectionBuilder = new MethodInspectionImpl.Builder(currentType, MethodInfo.MethodType.CONSTRUCTOR);
        } else {
            MethodInfo.MethodType methodType = extractMethodType(access);
            methodInspectionBuilder = new MethodInspectionImpl.Builder(currentType, name, methodType);
        }
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !currentTypeIsInterface) {
            methodInspectionBuilder.addModifier(MethodModifier.PUBLIC);
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) methodInspectionBuilder.addModifier(MethodModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) methodInspectionBuilder.addModifier(MethodModifier.PROTECTED);
        if ((access & Opcodes.ACC_FINAL) != 0) methodInspectionBuilder.addModifier(MethodModifier.FINAL);

        boolean lastParameterIsVarargs = (access & Opcodes.ACC_VARARGS) != 0;

        TypeContext methodContext = new TypeContext(typeContext);
        ParseGenerics parseGenerics = new ParseGenerics(methodContext, currentType, typeInspectionBuilder, localTypeMap,
                false);

        String signatureOrDescription = signature != null ? signature : descriptor;
        if (signatureOrDescription.startsWith("<")) {
            int end = parseGenerics.parseMethodGenerics(signatureOrDescription, methodInspectionBuilder, methodContext);
            if (end < 0) {
                // error state
                errorStateForType(signatureOrDescription);
                return null; // dropping the method, and the type!
            }
            signatureOrDescription = signatureOrDescription.substring(end + 1); // 1 to get rid of >
        }
        List<ParameterizedType> types = parseGenerics.parseParameterTypesOfMethod(methodContext, signatureOrDescription);
        if (types == null) {
            return null; // jdk
        }
        methodInspectionBuilder.setReturnType(types.get(types.size() - 1));

        MethodItem methodItem = null;
        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(currentType.fullyQualifiedName);
            if (typeItem != null) {
                String methodSignature = makeMethodSignature(name, currentType, types.subList(0, types.size() - 1));
                methodItem = typeItem.getMethodItems().get(methodSignature);
                if (methodItem != null && !methodItem.getAnnotations().isEmpty()) {
                    jetBrainsAnnotationTranslator.mapAnnotations(methodItem.getAnnotations(), methodInspectionBuilder);
                    for (MethodItem companionMethod : methodItem.getCompanionMethods()) {
                        CreateCompanionMethod.add(currentType, methodInspectionBuilder, companionMethod);
                    }
                }
            }
        }

        return new MyMethodVisitor(methodContext, localTypeMap, methodInspectionBuilder, typeInspectionBuilder,
                types, lastParameterIsVarargs, methodItem, jetBrainsAnnotationTranslator);
    }

    private MethodInfo.MethodType extractMethodType(int access) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        if (isStatic) {
            return MethodInfo.MethodType.STATIC_METHOD;
        }
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        if (isAbstract) {
            return MethodInfo.MethodType.ABSTRACT_METHOD;
        }
        if (currentTypeIsInterface) {
            return MethodInfo.MethodType.DEFAULT_METHOD;
        }
        return MethodInfo.MethodType.METHOD;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (currentType == null) return;

        LOGGER.debug("Visit inner class {} {} {} {}", name, outerName, innerName, access);
        if (name.equals(currentTypePath)) {
            checkTypeFlags(access, typeInspectionBuilder);
        } else if (innerName != null && outerName != null) {
            String fqnOuter = pathToFqn(outerName);
            boolean stepDown = currentTypePath.equals(outerName);
            boolean stepSide = currentType.packageNameOrEnclosingType.isRight() &&
                    currentType.packageNameOrEnclosingType.getRight().fullyQualifiedName.equals(fqnOuter);
            // step down
            if (stepSide || stepDown) {
                String fqn = fqnOuter + "." + innerName;

                LOGGER.debug("Processing sub-type {} of/in {}, step side? {} step down? {}", fqn,
                        currentType.fullyQualifiedName, stepSide, stepDown);

                TypeMap.InspectionAndState situation = localTypeMap.typeInspectionSituation(fqn);
                TypeInspection subTypeInspection;
                TypeInfo subTypeInMap;
                boolean byteCodeInspectionStarted;
                if (situation == null) {
                    subTypeInMap = new TypeInfo(stepDown ? currentType
                            : currentType.packageNameOrEnclosingType.getRight(), innerName);
                    subTypeInspection = localTypeMap.getOrCreate(subTypeInMap);
                    byteCodeInspectionStarted = false;
                } else {
                    subTypeInspection = situation.typeInspection();
                    subTypeInMap = subTypeInspection.typeInfo();
                    byteCodeInspectionStarted = situation.state().ge(STARTING_BYTECODE);
                }
                if (!byteCodeInspectionStarted) {
                    checkTypeFlags(access, (TypeInspection.Builder) subTypeInspection);
                    Source newPath = new Source(name + ".class", pathAndURI.uri());
                    TypeInfo subType = localTypeMap.inspectFromPath(newPath, typeContext, true).typeInfo();

                    if (subType != null) {
                        if (stepDown) {
                            typeInspectionBuilder.addSubType(subType);
                        }
                    } else {
                        errorStateForType(name);
                    }
                } else {
                    if (stepDown) {
                        typeInspectionBuilder.addSubType(subTypeInMap);
                    }
                }

            } //else? potentially add: String fqn = pathToFqn(name); localTypeMap.getOrCreate(fqn, true);
        }
    }

    private void checkTypeFlags(int access, TypeInspection.Builder typeInspectionBuilder) {
        if ((access & Opcodes.ACC_STATIC) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.STATIC);
        if ((access & Opcodes.ACC_PRIVATE) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PROTECTED);
        if ((access & Opcodes.ACC_PUBLIC) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PUBLIC);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (currentType == null) return null;

        LOGGER.debug("Have class annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(typeContext, localTypeMap, descriptor, typeInspectionBuilder);
    }

    // not overriding visitOuterClass

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        if (currentType == null) return null;

        LOGGER.debug("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitEnd() {
        if (currentType != null) {
            try {
                LOGGER.debug("Visit end of class " + currentType.fullyQualifiedName);
                if (typeInspectionBuilder == null)
                    throw new UnsupportedOperationException("? was expecting a type inspection builder");
                typeInspectionBuilder.setFunctionalInterface(functionalInterface());
                currentType = null;
                typeInspectionBuilder = null;
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception bytecode inspecting type {}", currentType.fullyQualifiedName);
                throw rte;
            }
        }
    }

    private boolean functionalInterface() {
        return typeInspectionBuilder.typeNature() == TypeNature.INTERFACE &&
                typeInspectionBuilder.computeIsFunctionalInterface(localTypeMap);
    }

    private void errorStateForType(String pathCausingFailure) {
        if (currentType == null || currentType.typeInspection.isSet()) throw new UnsupportedOperationException();
        String message = "Unable to inspect " + currentType.fullyQualifiedName + ": Cannot load " + pathCausingFailure;
        throw new RuntimeException(message);
    }

    public TypeInspection.Builder getInspectedType() {
        return typeInspectionBuilder;
    }
}
