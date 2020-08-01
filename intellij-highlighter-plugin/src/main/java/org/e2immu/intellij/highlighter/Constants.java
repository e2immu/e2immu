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
    String NOT_ANNOTATED = "";

    // TYPE
    String ANNOTATION_E2IMMUTABLE_TYPE = lc(E2Immutable.class) + TYPE;
    String ANNOTATION_E2CONTAINER_TYPE = lc(E2Container.class) + TYPE;
    String ANNOTATION_CONTAINER_TYPE = lc(Container.class) + TYPE;
    String NOT_ANNOTATED_TYPE = NOT_ANNOTATED + TYPE;

    // METHOD

    String ANNOTATION_NOT_MODIFIED_METHOD = lc(NotModified.class) + METHOD;
    String ANNOTATION_MODIFIED_METHOD = lc(Modified.class) + METHOD;
    String ANNOTATION_INDEPENDENT_METHOD = lc(Independent.class) + METHOD;
    String NOT_ANNOTATED_METHOD = NOT_ANNOTATED + METHOD;

    // FIELD

    String ANNOTATION_NOT_MODIFIED_FIELD = lc(NotModified.class) + FIELD;
    String ANNOTATION_MODIFIED_FIELD = lc(Modified.class) + FIELD;
    String ANNOTATION_FINAL_FIELD = lc(Final.class) + FIELD;
    String ANNOTATION_VARIABLE_FIELD = lc(Variable.class) + FIELD;
    String ANNOTATION_E2IMMUTABLE_FIELD = lc(E2Immutable.class) + FIELD;
    String ANNOTATION_E2CONTAINER_FIELD = lc(E2Container.class) + FIELD;
    String NOT_ANNOTATED_FIELD = NOT_ANNOTATED + FIELD;

    // PARAMETER

    String ANNOTATION_MODIFIED_PARAM = lc(Modified.class) + PARAM;
    String ANNOTATION_NOT_MODIFIED_PARAM = lc(NotModified.class) + PARAM;
    String NOT_ANNOTATED_PARAM = NOT_ANNOTATED + PARAM;


    String DEFAULTS = "E2Immu highlighter defaults";

    TextAttributesKey TAK_DEFAULT = TextAttributesKey.createTextAttributesKey(DEFAULTS);

    // TYPE

    TextAttributesKey TAK_E2IMMUTABLE_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E2CONTAINER_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_CONTAINER_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_TYPE = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_TYPE,
            TAK_DEFAULT);

    // METHOD


    TextAttributesKey TAK_NOT_MODIFIED_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOT_MODIFIED_METHOD,
            TAK_DEFAULT);
    TextAttributesKey TAK_MODIFIED_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_MODIFIED_METHOD,
            TAK_DEFAULT);
    TextAttributesKey TAK_INDEPENDENT_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_INDEPENDENT_METHOD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_METHOD = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_METHOD,
            TAK_DEFAULT);

    // FIELD

    TextAttributesKey TAK_E2IMMUTABLE_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2IMMUTABLE_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_E2CONTAINER_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2CONTAINER_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_FINAL_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_FINAL_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_VARIABLE_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_VARIABLE_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_MODIFIED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOT_MODIFIED_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_MODIFIED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_MODIFIED_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_FIELD,
            TAK_DEFAULT);

    // PARAMETER

    TextAttributesKey TAK_NOT_MODIFIED_PARAM = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOT_MODIFIED_PARAM,
            TAK_DEFAULT);
    TextAttributesKey TAK_MODIFIED_PARAM = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_MODIFIED_PARAM,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_PARAM = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_PARAM,
            TAK_DEFAULT);


    Map<String, TextAttributesKey> TAK_MAP = new ImmutableMap.Builder<String, TextAttributesKey>()
            .put(ANNOTATION_CONTAINER_TYPE, TAK_CONTAINER_TYPE)
            .put(ANNOTATION_E2CONTAINER_TYPE, TAK_E2CONTAINER_TYPE)
            .put(ANNOTATION_E2IMMUTABLE_TYPE, TAK_E2IMMUTABLE_TYPE)
            .put(NOT_ANNOTATED_TYPE, TAK_NOT_ANNOTATED_TYPE)

            .put(ANNOTATION_NOT_MODIFIED_METHOD, TAK_NOT_MODIFIED_METHOD)
            .put(ANNOTATION_MODIFIED_METHOD, TAK_MODIFIED_METHOD)
            .put(ANNOTATION_INDEPENDENT_METHOD, TAK_INDEPENDENT_METHOD)
            .put(NOT_ANNOTATED_METHOD, TAK_NOT_ANNOTATED_METHOD)

            .put(ANNOTATION_E2CONTAINER_FIELD, TAK_E2CONTAINER_FIELD)
            .put(ANNOTATION_E2IMMUTABLE_FIELD, TAK_E2IMMUTABLE_FIELD)
            .put(ANNOTATION_NOT_MODIFIED_FIELD, TAK_NOT_MODIFIED_FIELD)
            .put(ANNOTATION_MODIFIED_FIELD, TAK_MODIFIED_FIELD)
            .put(ANNOTATION_VARIABLE_FIELD, TAK_VARIABLE_FIELD)
            .put(ANNOTATION_FINAL_FIELD, TAK_FINAL_FIELD)
            .put(NOT_ANNOTATED_FIELD, TAK_NOT_ANNOTATED_FIELD)

            .put(ANNOTATION_NOT_MODIFIED_PARAM, TAK_NOT_MODIFIED_PARAM)
            .put(ANNOTATION_MODIFIED_PARAM, TAK_MODIFIED_PARAM)
            .put(NOT_ANNOTATED_PARAM, TAK_NOT_ANNOTATED_PARAM)
            .build();

    Map<String, String> HARDCODED_ANNOTATION_MAP = new ImmutableMap.Builder<String, String>()
            .put(E2Immutable.class.getCanonicalName(), lc(E2Immutable.class))
            .put(E2Container.class.getCanonicalName(), lc(E2Container.class))
            .put(Container.class.getCanonicalName(), lc(Container.class))
            .put(NotModified.class.getCanonicalName(), lc(NotModified.class))
            .put(Modified.class.getCanonicalName(), lc(Modified.class))
            .put(Final.class.getCanonicalName(), lc(Final.class))
            .put(Variable.class.getCanonicalName(), lc(Variable.class))
            .put(Independent.class.getCanonicalName(), lc(Independent.class))
            .build();
}
