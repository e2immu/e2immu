package org.e2immu.intellij.highlighter;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.e2immu.annotation.*;

import java.util.Map;

public interface Constants {
    static String lc(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }

    String APP_NAME = "E2Immu Highlighter";
    String STORAGE_FILE = "e2immuHighlighter.xml";

    String METHOD = "-m";
    String FIELD = "-f";
    String PARAM = "-p";
    String TYPE = "-t";

    String E2I = "e2i-";

    String ANNOTATION_E2IMMUTABLE_TYPE = lc(E2Immutable.class) + TYPE;
    String ANNOTATION_E2FINAL_TYPE = lc(E2Final.class) + TYPE;
    String ANNOTATION_CONTAINER_TYPE = lc(Container.class) + TYPE;
    String ANNOTATION_NOTMODIFIED_PARAM = lc(NotModified.class) + PARAM;
    String ANNOTATION_NOTMODIFIED_METHOD = lc(NotModified.class) + METHOD;
    String ANNOTATION_NOTMODIFIED_FIELD = lc(NotModified.class) + FIELD;

    String NOT_ANNOTATED = "";
    String NOT_ANNOTATED_FIELD = NOT_ANNOTATED+FIELD;
    String NOT_ANNOTATED_METHOD = NOT_ANNOTATED+METHOD;
    String NOT_ANNOTATED_PARAM = NOT_ANNOTATED+PARAM;
    String NOT_ANNOTATED_TYPE = NOT_ANNOTATED+TYPE;

    String DEFAULTS = "E2Immu highlighter defaults";

    TextAttributesKey TAK_DEFAULT = TextAttributesKey.createTextAttributesKey(DEFAULTS);

    TextAttributesKey TAK_E2IMMUTABLE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E2FINAL = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2FINAL_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_CONTAINER = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_MODIFIED_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOTMODIFIED_METHOD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_MODIFIED_PARAM = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOTMODIFIED_PARAM,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_MODIFIED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOTMODIFIED_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_METHOD = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_METHOD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_PARAM = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_PARAM,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_TYPE = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_TYPE,
            TAK_DEFAULT);

    Map<String, TextAttributesKey> TAK_MAP = new ImmutableMap.Builder<String, TextAttributesKey>()
            .put(ANNOTATION_CONTAINER_TYPE, TAK_CONTAINER)
            .put(ANNOTATION_E2FINAL_TYPE, TAK_E2FINAL)
            .put(ANNOTATION_E2IMMUTABLE_TYPE, TAK_E2IMMUTABLE)
            .put(ANNOTATION_NOTMODIFIED_FIELD, TAK_NOT_MODIFIED_FIELD)
            .put(ANNOTATION_NOTMODIFIED_METHOD, TAK_NOT_MODIFIED_METHOD)
            .put(ANNOTATION_NOTMODIFIED_PARAM, TAK_NOT_MODIFIED_PARAM)
            .put(NOT_ANNOTATED+FIELD, TAK_NOT_ANNOTATED_FIELD)
            .put(NOT_ANNOTATED+METHOD, TAK_NOT_ANNOTATED_METHOD)
            .put(NOT_ANNOTATED+PARAM, TAK_NOT_ANNOTATED_PARAM)
            .put(NOT_ANNOTATED+TYPE, TAK_NOT_ANNOTATED_TYPE)
            .build();

    Map<String, String> HARDCODED_ANNOTATION_MAP = new ImmutableMap.Builder<String, String>()
            .put(E2Immutable.class.getCanonicalName(), lc(E2Immutable.class))
            .put(E2Final.class.getCanonicalName(), lc(E2Final.class))
            .put(Container.class.getCanonicalName(), lc(Container.class))
            .put(NotModified.class.getCanonicalName(), lc(NotModified.class))
            .put(NotNull.class.getCanonicalName(), lc(NotNull.class))
            .put(NullNotAllowed.class.getCanonicalName(), lc(NullNotAllowed.class))
            .put(Independent.class.getCanonicalName(), lc(Independent.class))
            .build();
}
