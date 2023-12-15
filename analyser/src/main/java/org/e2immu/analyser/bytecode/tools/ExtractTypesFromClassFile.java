package org.e2immu.analyser.bytecode.tools;

import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.e2immu.graph.G;
import org.e2immu.graph.analyser.PackedInt;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.e2immu.analyser.bytecode.asm.MyClassVisitor.pathToFqn;
import static org.objectweb.asm.Opcodes.ASM9;

public class ExtractTypesFromClassFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractTypesFromClassFile.class);

    private final G.Builder<String> externalTypeGraphBuilder = new G.Builder<>(PackedInt::longSum);
    private final Map<String, List<String>> jarsOfType = new HashMap<>();
    private final Map<String, Set<String>> typesInJar = new HashMap<>();

    // called at the end
    public G<String> build() {
        return externalTypeGraphBuilder.build();
    }

    // can be called multiple times
    public void go(Resources resources) throws IOException {
        G.Builder<String> typeGraphBuilder = new G.Builder<>(PackedInt::longSum);
        resources.visit(new String[]{}, (prefix, uris) -> {
            URI uri = uris.get(0);
            if (uri.toString().endsWith(".class") && !uri.toString().endsWith("/module-info.class")) {
                String fqn = pathToFqn(String.join(".", prefix));
                String jarName = extractJarName(uri.toString());
                LOGGER.info("Parsing {} in jar {}", fqn, jarName);

                Source source = resources.fqnToPath(fqn, ".class");
                byte[] classBytes = resources.loadBytes(source.path());
                if (classBytes != null) {
                    ClassReader classReader = new ClassReader(classBytes);
                    MyClassVisitor myClassVisitor = new MyClassVisitor(jarName, new GraphAction() {
                        @Override
                        public void typeInJar(String type, String jar) {
                            addTypeToJar(type, jar);
                        }

                        @Override
                        public void typeDependsOnType(String from, String to, long value) {
                            typeGraphBuilder.mergeEdge(from, to, value);
                        }
                    });
                    classReader.accept(myClassVisitor, 0);
                    LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);
                } else {
                    LOGGER.warn("Skipping {}, no class bytes", uri);
                }
            }
        });
        // copy to external; we should already know what to exclude
        for (Map.Entry<String, Map<String, Long>> edge : typeGraphBuilder.edges()) {
            // all 'from' types should be registered
            String from = edge.getKey();
            List<String> jars = jarsOfType.get(from);
            if (jars != null) {
                String jarOfFrom = jars.get(0);
                for (Map.Entry<String, Long> e2 : edge.getValue().entrySet()) {
                    String to = e2.getKey();
                    List<String> jarsOfTo = jarsOfType.get(to);
                    if (jarsOfTo == null || !jarOfFrom.equals(jarsOfTo.get(0))) {
                        externalTypeGraphBuilder.mergeEdge(from, to, e2.getValue());
                    }
                }
            }
        }
    }

    private void addTypeToJar(String fqn, String jarName) {
        jarsOfType.computeIfAbsent(fqn, m -> new ArrayList<>()).add(jarName);
        typesInJar.computeIfAbsent(jarName, m -> new HashSet<>()).add(fqn);
    }

    interface GraphAction {
        void typeInJar(String type, String jar);

        void typeDependsOnType(String from, String to, long value);
    }

    static class MyClassVisitor extends ClassVisitor {
        private final String jarName;
        private final GraphAction graphAction;
        private String fqn;

        public MyClassVisitor(String jarName, GraphAction graphAction) {
            super(ASM9);
            this.graphAction = graphAction;
            this.jarName = jarName;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            fqn = pathToFqn(name);
            graphAction.typeInJar(fqn, jarName);
            // typeGraph.addVertex(fqn);
            LOGGER.debug("Visiting {} in jar {}", fqn, jarName);

            if (signature == null) {
                String parentFqn = superName == null ? null : pathToFqn(superName);
                if (parentFqn != null && notJavaLang(parentFqn)) {
                    graphAction.typeDependsOnType(fqn, parentFqn, PackedInt.HIERARCHY.of(1));
                    LOGGER.debug(" ->> {} -> {}, parent", superName, parentFqn);
                }
                if (interfaces != null) {
                    for (String interfaceName : interfaces) {
                        String interfaceFqn = pathToFqn(interfaceName);
                        if (notJavaLang(interfaceFqn)) {
                            graphAction.typeDependsOnType(fqn, interfaceFqn, PackedInt.HIERARCHY.of(1));
                            LOGGER.debug(" ->> {} -> {}, interface", interfaceName, interfaceFqn);
                        }
                    }
                }
            } else {
                List<String> types = extract(signature);
                for (String type : types) {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.HIERARCHY.of(1));
                }
            }
        }

        @Override
        public MethodVisitor visitMethod(int access,
                                         String name,
                                         String descriptor,
                                         String signature,
                                         String[] exceptions) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.HIERARCHY.of(1));
                }
            }
            if (exceptions != null) {
                for (String exceptionName : exceptions) {
                    String exceptionFqn = pathToFqn(exceptionName);
                    if (notJavaLang(exceptionFqn)) {
                        graphAction.typeDependsOnType(fqn, exceptionFqn, PackedInt.METHOD.of(1));
                        LOGGER.debug(" ->> {} -> {}, exception", exceptionName, exceptionFqn);
                    }
                }
            }
            return new MyMethodVisitor(fqn, graphAction);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.FIELD.of(1));
                }
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            LOGGER.debug("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
            if(descriptor != null) {
                List<String> types = extract(descriptor);
                for (String type : types) {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.FIELD.of(1));
                }
            }
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }
    }

    static class MyMethodVisitor extends MethodVisitor {
        private final String fqn;
        private final GraphAction graphAction;

        MyMethodVisitor(String fqn, GraphAction graphAction) {
            super(ASM9);
            this.fqn = fqn;
            this.graphAction = graphAction;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            LOGGER.debug("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
            if(descriptor != null) {
                List<String> types = extract(descriptor);
                for (String type : types) {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.FIELD.of(1));
                }
            }
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (type != null) {
                String typeFqn = extractPlain(type);
                if (typeFqn != null) {
                    LOGGER.debug("Type instruction {} -> {}", type, typeFqn);
                    graphAction.typeDependsOnType(fqn, typeFqn, PackedInt.METHOD.of(1));
                } else {
                    List<String> extracted = extract(type);
                    for (String eFqn : extracted) {
                        LOGGER.debug("Type instruction {} -> {}", type, eFqn);
                        graphAction.typeDependsOnType(fqn, eFqn, PackedInt.METHOD.of(1));
                    }
                }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (descriptor != null) {
                List<String> fieldTypes = extract(descriptor);
                fieldTypes.forEach(type -> {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.EXPRESSION.of(1));
                    LOGGER.debug("Field instruction {} -> {}", descriptor, type);
                });
            }
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            if (descriptor != null) {
                List<String> fieldTypes = extract(descriptor);
                fieldTypes.forEach(type -> {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.EXPRESSION.of(1));
                    LOGGER.debug("Multi-array {} -> {}", descriptor, type);
                });
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            int open = descriptor.indexOf('(');
            int close = descriptor.indexOf(')');
            String returnType = descriptor.substring(close + 1);
            List<String> returnTypes = extract(returnType);
            String parameters = descriptor.substring(open + 1, close);
            List<String> parameterTypes = extract(parameters);
            Stream.concat(returnTypes.stream(), parameterTypes.stream()).forEach(type -> {
                graphAction.typeDependsOnType(fqn, type, PackedInt.EXPRESSION.of(1));
                LOGGER.debug("Method call type {} -> {}", returnType, type);
            });
        }

        // parameter types,
        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    graphAction.typeDependsOnType(fqn, type, PackedInt.METHOD.of(1));
                    LOGGER.debug("Local {} {} -> {}", name, signature, type);
                }
            }
        }
    }

    private static final Pattern PLAIN = Pattern.compile("[\\p{Alnum}/$]+");

    private static String extractPlain(String type) {
        Matcher plain = PLAIN.matcher(type);
        if (plain.matches()) {
            String fqn = pathToFqn(type);
            if (notJavaLang(fqn)) {
                return fqn;
            }
        }
        return null;
    }

    static List<String> extract(String descriptor) {
        int lessThan = descriptor.indexOf('<');
        if (lessThan >= 0) {
            int correspondingGt = correspondingGt(descriptor, lessThan + 1);
            List<String> inside = extract(descriptor.substring(lessThan + 1, correspondingGt));
            String without = descriptor.substring(0, lessThan) + descriptor.substring(correspondingGt + 1);
            List<String> listWithout = extract(without);
            return Stream.concat(listWithout.stream(), inside.stream()).toList();
        }
        return extractWithoutGenerics(descriptor);
    }

    private static int correspondingGt(String descriptor, int start) {
        int countOpen = 0;
        for (int current = start; current < descriptor.length(); current++) {
            char c = descriptor.charAt(current);
            if ('<' == c) countOpen++;
            if ('>' == c) {
                if (countOpen == 0) return current;
                countOpen--;
            }
        }
        throw new UnsupportedOperationException();
    }

    static final Pattern STD = Pattern.compile("^\\[*L([\\p{Alnum}/$.]+);");

    private static List<String> extractWithoutGenerics(String descriptor) {
        Matcher std = STD.matcher(descriptor);
        if (std.find()) {
            String fqn = std.group(1).replaceAll("[/$]", ".");
            Stream<String> stream = notJavaLang(fqn) ? Stream.of(fqn) : Stream.of();
            if (std.end() != descriptor.length()) {
                String rest = descriptor.substring(std.end());
                List<String> recurse = extractWithoutGenerics(rest);
                return Stream.concat(stream, recurse.stream()).toList();
            }
            return stream.toList();
        }
        return List.of();
    }

    private static boolean notJavaLang(String parentFqName) {
        return !parentFqName.startsWith("java.lang.");
    }

    private static final Pattern JAR = Pattern.compile("/([^/]+)!/");

    private static String extractJarName(String path) {
        Matcher m = JAR.matcher(path);
        return m.find() ? m.group(1) : null;
    }
}
