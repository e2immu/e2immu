package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.e2immu.graph.analyser.PackedInt;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.e2immu.analyser.bytecode.asm.MyClassVisitor.pathToFqn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ASM9;

public class TestJarExternals {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestJarExternals.class);

    @Test
    public void test() throws IOException {
        Resources resources = new Resources();
        int entries = resources.addJarFromClassPath("org/apache/commons/pool");
        assertTrue(entries > 0);
        G.Builder<String> typeGraph = new G.Builder<>(PackedInt::longSum);
        Set<String> own = new HashSet<>();
        resources.visit(new String[]{}, (prefix, uris) -> {
            URI uri = uris.get(0);
            if (uri.toString().endsWith(".class")) {
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
            // TODO type parameters -> parse signature, descriptor
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // TODO parameter types, type parameters -> parse signature, descriptor
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
        public void visitTypeInsn(int opcode, String type) {
            if (type != null) {
                String typeFqn = pathToFqn(type); // TODO parse for [], type parameters, etc.
                LOGGER.debug("Type instruction {} -> {}", type, typeFqn);
                typeGraph.mergeEdge(fqn, typeFqn, PackedInt.METHOD.of(1));
            }
        }

        // parameter types,
        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            if (signature != null) {
                List<String> types = extract(signature);
                for (String type : types) {
                    typeGraph.mergeEdge(fqn, type, PackedInt.METHOD.of(1));
                    LOGGER.debug("Local {} {} -> {}", name, signature, type);
                }
            }
        }
    }

    private static final Pattern STD = Pattern.compile("\\[*L([\\p{Alnum}/$]+);");

    private static List<String> extract(String descriptor) {
        Matcher std = STD.matcher(descriptor);
        List<String> list = new ArrayList<>();
        while (std.find()) {
            String fqn = std.group(1).replaceAll("[/$]", ".");
            if(notJavaLang(fqn)) {
                list.add(fqn);
            }
        }
        return list;
    }

    private static boolean notJavaLang(String parentFqName) {
        return !parentFqName.startsWith("java.lang.");
    }

    @Test
    public void test2() {
        assertEquals("[org.apache.commons.pool.impl.CursorableLinkedList.ListIter]",
                extract("Lorg/apache/commons/pool/impl/CursorableLinkedList<TE;>.ListIter;").toString());
    }
}
