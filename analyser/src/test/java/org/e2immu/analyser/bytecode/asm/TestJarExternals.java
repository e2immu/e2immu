package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.e2immu.graph.analyser.PackedInt;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.e2immu.analyser.bytecode.asm.MyClassVisitor.pathToFqn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ASM9;

public class TestJarExternals {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestJarExternals.class);

    @Test
    public void test() throws IOException {
        Resources resources = new Resources();
        int entries = resources.addJarFromClassPath("org/jgrapht/graph");
        assertTrue(entries > 0);
        G.Builder<String> typeGraph = new G.Builder<>(PackedInt::longSum);
        Set<String> own = new HashSet<>();
        resources.visit(new String[]{}, (prefix, uris) -> {
            URI uri = uris.get(0);
            if (uri.toString().endsWith(".class") && !uri.toString().endsWith("/module-info.class")) {
                String fqn = pathToFqn(String.join(".", prefix));
                LOGGER.info("Parsing {}", fqn);
                own.add(fqn);
                Source source = resources.fqnToPath(fqn, ".class");
                byte[] classBytes = resources.loadBytes(source.path());
                if (classBytes != null) {
                    ClassReader classReader = new ClassReader(classBytes);
                    MyClassVisitor myClassVisitor = new MyClassVisitor(typeGraph);
                    classReader.accept(myClassVisitor, 0);
                    LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);
                } else {
                    LOGGER.warn("Skipping {}, no class bytes", uri);
                }
            }
        });
        G<String> g = typeGraph.build();
        for (V<String> v : g.vertices()) {
            if (!own.contains(v.someElement())) {
                LOGGER.info("External: {}", v.someElement());
            }
        }
    }

    static class MyClassVisitor extends ClassVisitor {
        private final G.Builder<String> typeGraph;
        private String fqn;

        public MyClassVisitor(G.Builder<String> typeGraph) {
            super(ASM9);
            this.typeGraph = typeGraph;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            fqn = pathToFqn(name);
            typeGraph.addVertex(fqn);
            LOGGER.debug("Visiting {}", fqn);

            if (signature == null) {
                String parentFqn = superName == null ? null : pathToFqn(superName);
                if (parentFqn != null && notJavaLang(parentFqn)) {
                    typeGraph.mergeEdge(fqn, parentFqn, PackedInt.HIERARCHY.of(1));
                    LOGGER.debug(" ->> {} -> {}, parent", superName, parentFqn);
                }
                if (interfaces != null) {
                    for (String interfaceName : interfaces) {
                        String interfaceFqn = pathToFqn(interfaceName);
                        if (notJavaLang(interfaceFqn)) {
                            typeGraph.mergeEdge(fqn, interfaceFqn, PackedInt.HIERARCHY.of(1));
                            LOGGER.debug(" ->> {} -> {}, interface", interfaceName, interfaceFqn);
                        }
                    }
                }
            } else {
                List<String> types = extract(signature);
                for (String type : types) {
                    typeGraph.mergeEdge(fqn, type, PackedInt.HIERARCHY.of(1));
                }
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    typeGraph.mergeEdge(fqn, type, PackedInt.HIERARCHY.of(1));
                }
            }
            if (exceptions != null) {
                for (String exceptionName : exceptions) {
                    String exceptionFqn = pathToFqn(exceptionName);
                    if (notJavaLang(exceptionFqn)) {
                        typeGraph.mergeEdge(fqn, exceptionFqn, PackedInt.METHOD.of(1));
                        LOGGER.debug(" ->> {} -> {}, exception", exceptionName, exceptionFqn);
                    }
                }
            }
            return new MyMethodVisitor(fqn, typeGraph);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    typeGraph.mergeEdge(fqn, type, PackedInt.FIELD.of(1));
                }
            }
            return new MyFieldVisitor(fqn, typeGraph);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            LOGGER.info("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }
    }

    static class MyFieldVisitor extends FieldVisitor {
        private final String fqn;
        private final G.Builder<String> typeGraph;

        MyFieldVisitor(String fqn, G.Builder<String> typeGraph) {
            super(ASM9);
            this.fqn = fqn;
            this.typeGraph = typeGraph;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            LOGGER.info("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }
    }

    static class MyMethodVisitor extends MethodVisitor {
        private final String fqn;
        private final G.Builder<String> typeGraph;

        MyMethodVisitor(String fqn, G.Builder<String> typeGraph) {
            super(ASM9);
            this.fqn = fqn;
            this.typeGraph = typeGraph;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            LOGGER.info("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (type != null) {
                String typeFqn = extractPlain(type);
                if (typeFqn != null) {
                    LOGGER.debug("Type instruction {} -> {}", type, typeFqn);
                    typeGraph.mergeEdge(fqn, typeFqn, PackedInt.METHOD.of(1));
                } else {
                    List<String> extracted = extract(type);
                    for (String eFqn : extracted) {
                        LOGGER.debug("Type instruction {} -> {}", type, typeFqn);
                        typeGraph.mergeEdge(fqn, eFqn, PackedInt.METHOD.of(1));
                    }
                }
            }
        }

        // parameter types,
        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    typeGraph.mergeEdge(fqn, type, PackedInt.METHOD.of(1));
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

    private static List<String> extract(String descriptor) {
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

    private static final Pattern STD = Pattern.compile("^\\[*L([\\p{Alnum}/$.]+);");

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

    @Test
    public void test2a() {
        assertEquals("[org.apache.commons.pool.impl.CursorableLinkedList.ListIter]",
                extract("Lorg/apache/commons/pool/impl/CursorableLinkedList<TE;>.ListIter;").toString());
    }

    @Test
    public void test2b() {
        Matcher m = STD.matcher("[Ljava/lang/Object;");
        assertTrue(m.matches());
    }

    @Test
    public void test2c() {
        assertEquals("[java.util.List, java.util.Map, java.xx.String, java.yy.Double]",
                extract("Ljava/util/List<Ljava/util/Map<Ljava/xx/String;Ljava/yy/Double;>;>;").toString());
    }

    @Test
    public void test2d() {
        assertEquals("[org.apache.commons.pool.impl.GenericKeyedObjectPool.Latch]", extract("Lorg/apache/commons/pool/impl/GenericKeyedObjectPool<TK;TV;>.Latch<TLK;TLV;>;").toString());
    }
}
