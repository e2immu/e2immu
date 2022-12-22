package org.e2immu.analyser.output;

public record Keyword(String keyword) implements OutputElement {

    public static final Keyword ABSTRACT = new Keyword("abstract");
    public static final Keyword ASSERT = new Keyword("assert");
    public static final Keyword BREAK = new Keyword("break");
    public static final Keyword CASE = new Keyword("case");
    public static final Keyword CATCH = new Keyword("catch");
    public static final Keyword CLASS = new Keyword("class");
    public static final Keyword CONTINUE = new Keyword("continue");
    public static final Keyword DEFAULT = new Keyword("default");
    public static final Keyword DO = new Keyword("do");
    public static final Keyword ELSE = new Keyword("else");
    public static final Keyword EXTENDS = new Keyword("extends");
    public static final Keyword FINAL = new Keyword("final");
    public static final Keyword FINALLY = new Keyword("finally");
    public static final Keyword FOR = new Keyword("for");
    public static final Keyword GOTO = new Keyword("goto");
    public static final Keyword IF = new Keyword("if");
    public static final Keyword IMPLEMENTS = new Keyword("implements");
    public static final Keyword IMPORT = new Keyword("import");
    public static final Keyword INTERFACE = new Keyword("interface");
    public static final Keyword LENGTH = new Keyword("length");
    public static final Keyword NEW = new Keyword("new");
    public static final Keyword NON_SEALED = new Keyword("non-sealed");
    public static final Keyword NULL = new Keyword("null");
    public static final Keyword PACKAGE = new Keyword("package");
    public static final Keyword PRIVATE = new Keyword("private");
    public static final Keyword PROTECTED = new Keyword("protected");
    public static final Keyword PUBLIC = new Keyword("public");
    public static final Keyword RETURN = new Keyword("return");
    public static final Keyword SEALED = new Keyword("sealed");
    public static final Keyword STATIC = new Keyword("static");
    public static final Keyword SUPER = new Keyword("super");
    public static final Keyword SWITCH = new Keyword("switch");
    public static final Keyword SYNCHRONIZED = new Keyword("synchronized");
    public static final Keyword THROW = new Keyword("throw");
    public static final Keyword THROWS = new Keyword("throws");
    public static final Keyword TRANSIENT = new Keyword("transient");
    public static final Keyword TRY = new Keyword("try");
    public static final Keyword VAR = new Keyword("var");
    public static final Keyword VOLATILE = new Keyword("volatile");
    public static final Keyword WHILE = new Keyword("while");
    public static final Keyword YIELD = new Keyword("try");

    @Override
    public String minimal() {
        return keyword;
    }

    @Override
    public String write(FormattingOptions options) {
        return keyword;
    }

    @Override
    public String generateJavaForDebugging() {
        return ".add(Keyword." + keyword.toUpperCase() + ")";
    }
}
