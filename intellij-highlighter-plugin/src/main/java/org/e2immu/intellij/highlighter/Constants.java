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

package org.e2immu.intellij.highlighter;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.e2immu.annotation.*;

import java.util.Map;

import static org.e2immu.intellij.highlighter.ElementType.*;

public interface Constants {
    static String lc(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }

    String APP_NAME = "E2Immu Highlighter";
    String STORAGE_FILE = "e2immuHighlighter.xml";


    String E2I = "e2i-";
    String NOT_ANNOTATED = "";

    // TYPE (from most important to least important)
    String ANNOTATION_E2CONTAINER_TYPE = lc(E2Container.class) + TYPE;
    String ANNOTATION_E2IMMUTABLE_TYPE = lc(E2Immutable.class) + TYPE;
    String ANNOTATION_E1CONTAINER_TYPE = lc(E1Container.class) + TYPE;
    String ANNOTATION_E1IMMUTABLE_TYPE = lc(E1Immutable.class) + TYPE;
    String ANNOTATION_CONTAINER_TYPE = lc(Container.class) + TYPE;
    String ANNOTATION_MUTABLE_MODIFIES_ARGUMENTS_TYPE = lc(MutableModifiesArguments.class) + TYPE;
    String NOT_ANNOTATED_TYPE = NOT_ANNOTATED + TYPE;

    // for the type of fields and methods
    String ANNOTATION_BEFORE_MARK_TYPE = lc(BeforeMark.class) + TYPE;

    // METHOD

    String ANNOTATION_INDEPENDENT_METHOD = lc(Independent.class) + METHOD; // implies @NotModified on methods, @Modified on constructors
    String ANNOTATION_DEPENDENT_METHOD = lc(Dependent.class) + METHOD; // implies @NotModified on methods, @Modified on constructors
    String ANNOTATION_NOT_MODIFIED_METHOD = lc(NotModified.class) + METHOD; // not for constructors
    String ANNOTATION_MODIFIED_METHOD = lc(Modified.class) + METHOD; // not for constructors
    String NOT_ANNOTATED_METHOD = NOT_ANNOTATED + METHOD;

    // dynamic type annotations, transferred to the field's type; NO SEPARATE COLORS!
    String ANNOTATION_E2IMMUTABLE_METHOD = lc(E2Immutable.class) + TYPE_OF_METHOD;
    String ANNOTATION_E2CONTAINER_METHOD = lc(E2Container.class) + TYPE_OF_METHOD;
    String ANNOTATION_E1IMMUTABLE_METHOD = lc(E1Immutable.class) + TYPE_OF_METHOD;
    String ANNOTATION_E1CONTAINER_METHOD = lc(E1Container.class) + TYPE_OF_METHOD;
    String ANNOTATION_BEFORE_MARK_METHOD = lc(BeforeMark.class) + TYPE_OF_METHOD;

    // FIELD

    String ANNOTATION_VARIABLE_FIELD = lc(Variable.class) + FIELD;
    String ANNOTATION_MODIFIED_FIELD = lc(Modified.class) + FIELD; // implies @Final
    String ANNOTATION_NOT_MODIFIED_FIELD = lc(NotModified.class) + FIELD; // implies @Final
    String ANNOTATION_FINAL_FIELD = lc(Final.class) + FIELD; // only for primitives

    // dynamic type annotations, transferred to the field's type; NO SEPARATE COLORS!
    String ANNOTATION_E2IMMUTABLE_FIELD = lc(E2Immutable.class) + TYPE_OF_FIELD;
    String ANNOTATION_E2CONTAINER_FIELD = lc(E2Container.class) + TYPE_OF_FIELD;
    String ANNOTATION_E1IMMUTABLE_FIELD = lc(E1Immutable.class) + TYPE_OF_FIELD;
    String ANNOTATION_E1CONTAINER_FIELD = lc(E1Container.class) + TYPE_OF_FIELD;
    String ANNOTATION_BEFORE_MARK_FIELD = lc(BeforeMark.class) + TYPE_OF_FIELD;

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
    TextAttributesKey TAK_E1IMMUTABLE_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E1IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E1CONTAINER_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E1CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_CONTAINER_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_MUTABLE_MODIFIES_ARGUMENTS_TYPE = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_MUTABLE_MODIFIES_ARGUMENTS_TYPE,
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
    TextAttributesKey TAK_DEPENDENT_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_DEPENDENT_METHOD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_METHOD = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_METHOD,
            TAK_DEFAULT);

    TextAttributesKey TAK_E2IMMUTABLE_TYPE_OF_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E2CONTAINER_TYPE_OF_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E1IMMUTABLE_TYPE_OF_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E1IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E1CONTAINER_TYPE_OF_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E1CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_BEFORE_MARK_TYPE_OF_METHOD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_BEFORE_MARK_TYPE,
            TAK_DEFAULT);

    // FIELD

    TextAttributesKey TAK_VARIABLE_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_VARIABLE_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_MODIFIED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_NOT_MODIFIED_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_MODIFIED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_MODIFIED_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_FINAL_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_FINAL_FIELD,
            TAK_DEFAULT);
    TextAttributesKey TAK_NOT_ANNOTATED_FIELD = TextAttributesKey.createTextAttributesKey(E2I + NOT_ANNOTATED_FIELD,
            TAK_DEFAULT);

    TextAttributesKey TAK_E2IMMUTABLE_TYPE_OF_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E2CONTAINER_TYPE_OF_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E2CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E1IMMUTABLE_TYPE_OF_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E1IMMUTABLE_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_E1CONTAINER_TYPE_OF_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_E1CONTAINER_TYPE,
            TAK_DEFAULT);
    TextAttributesKey TAK_BEFORE_MARK_TYPE_OF_FIELD = TextAttributesKey.createTextAttributesKey(E2I + ANNOTATION_BEFORE_MARK_TYPE,
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
            .put(ANNOTATION_E1CONTAINER_TYPE, TAK_E1CONTAINER_TYPE)
            .put(ANNOTATION_E1IMMUTABLE_TYPE, TAK_E1IMMUTABLE_TYPE)
            .put(ANNOTATION_MUTABLE_MODIFIES_ARGUMENTS_TYPE, TAK_MUTABLE_MODIFIES_ARGUMENTS_TYPE)
            .put(NOT_ANNOTATED_TYPE, TAK_NOT_ANNOTATED_TYPE)

            .put(ANNOTATION_NOT_MODIFIED_METHOD, TAK_NOT_MODIFIED_METHOD)
            .put(ANNOTATION_MODIFIED_METHOD, TAK_MODIFIED_METHOD)
            .put(ANNOTATION_INDEPENDENT_METHOD, TAK_INDEPENDENT_METHOD)
            .put(ANNOTATION_DEPENDENT_METHOD, TAK_DEPENDENT_METHOD)
            .put(NOT_ANNOTATED_METHOD, TAK_NOT_ANNOTATED_METHOD)
            .put(ANNOTATION_E2CONTAINER_METHOD, TAK_E2CONTAINER_TYPE_OF_METHOD)
            .put(ANNOTATION_E1CONTAINER_METHOD, TAK_E1CONTAINER_TYPE_OF_METHOD)
            .put(ANNOTATION_E2IMMUTABLE_METHOD, TAK_E2IMMUTABLE_TYPE_OF_METHOD)
            .put(ANNOTATION_E1IMMUTABLE_METHOD, TAK_E1IMMUTABLE_TYPE_OF_METHOD)
            .put(ANNOTATION_BEFORE_MARK_METHOD, TAK_BEFORE_MARK_TYPE_OF_METHOD)

            .put(ANNOTATION_NOT_MODIFIED_FIELD, TAK_NOT_MODIFIED_FIELD)
            .put(ANNOTATION_MODIFIED_FIELD, TAK_MODIFIED_FIELD)
            .put(ANNOTATION_FINAL_FIELD, TAK_FINAL_FIELD)
            .put(ANNOTATION_VARIABLE_FIELD, TAK_VARIABLE_FIELD)
            .put(NOT_ANNOTATED_FIELD, TAK_NOT_ANNOTATED_FIELD)
            .put(ANNOTATION_E2CONTAINER_FIELD, TAK_E2CONTAINER_TYPE_OF_FIELD)
            .put(ANNOTATION_E1CONTAINER_FIELD, TAK_E1CONTAINER_TYPE_OF_FIELD)
            .put(ANNOTATION_E2IMMUTABLE_FIELD, TAK_E2IMMUTABLE_TYPE_OF_FIELD)
            .put(ANNOTATION_E1IMMUTABLE_FIELD, TAK_E1IMMUTABLE_TYPE_OF_FIELD)
            .put(ANNOTATION_BEFORE_MARK_FIELD, TAK_BEFORE_MARK_TYPE_OF_FIELD)

            .put(ANNOTATION_NOT_MODIFIED_PARAM, TAK_NOT_MODIFIED_PARAM)
            .put(ANNOTATION_MODIFIED_PARAM, TAK_MODIFIED_PARAM)
            .put(NOT_ANNOTATED_PARAM, TAK_NOT_ANNOTATED_PARAM)
            .build();

    /**
     * Contains hard-coded elements: canonical type name goes in, base name goes comes as a result.
     * See AnnotationStore.
     */
    Map<String, String> HARDCODED_ANNOTATION_MAP = new ImmutableMap.Builder<String, String>()
            .put(E2Immutable.class.getCanonicalName(), lc(E2Immutable.class))
            .put(E2Container.class.getCanonicalName(), lc(E2Container.class))
            .put(E1Immutable.class.getCanonicalName(), lc(E1Immutable.class))
            .put(E1Container.class.getCanonicalName(), lc(E1Container.class))
            .put(Container.class.getCanonicalName(), lc(Container.class))
            .put(NotModified.class.getCanonicalName(), lc(NotModified.class))
            .put(Modified.class.getCanonicalName(), lc(Modified.class))
            .put(Variable.class.getCanonicalName(), lc(Variable.class))
            .put(Independent.class.getCanonicalName(), lc(Independent.class))
            .put(Dependent.class.getCanonicalName(), lc(Dependent.class))
            .put(MutableModifiesArguments.class.getCanonicalName(), lc(MutableModifiesArguments.class))
            .put(BeforeMark.class.getCanonicalName(), lc(BeforeMark.class))
            .put(Final.class.getCanonicalName(), lc(Final.class))
            .build();
}
