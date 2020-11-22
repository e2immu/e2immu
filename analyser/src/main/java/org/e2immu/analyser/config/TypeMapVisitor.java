package org.e2immu.analyser.config;

import org.e2immu.analyser.parser.TypeMap;

public interface TypeMapVisitor {
    void visit(TypeMap typeMap);
}
