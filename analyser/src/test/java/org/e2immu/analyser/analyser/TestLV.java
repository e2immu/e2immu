package org.e2immu.analyser.analyser;

import org.e2immu.analyser.config.AnnotationXmlConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeParameter;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.graph.op.DijkstraShortestPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestLV {

    protected static TypeContext typeContext;
    protected static AnalyserContext analyserContext;
    private static EvaluationResult context;

    // NO annotated APIs, but an XML file
    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath("src/test/resources/org/e2immu/analyser/model/expression")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi");
        AnnotationXmlConfiguration annotationXmlConfiguration = new AnnotationXmlConfiguration.Builder()
                .addAnnotationXmlReadPackages("java.util")
                .build();
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotationXmConfiguration(annotationXmlConfiguration)
                .addDebugLogTargets(LogTarget.ANALYSIS, LogTarget.MODEL)
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.preload("java.util");
        Parser.RunResult rr = parser.run();
        typeContext = parser.getTypeContext();
        analyserContext = rr.analyserContext();
    }

    @Test
    public void test() {
        // <> as in T
        LV.HiddenContent hc = LV.wholeType(null);
        assertEquals("<>", hc.toString());

        // <0> as in E of List<E>, this with a single type parameter T
        LV.HiddenContent hc0 = LV.typeParameter(null, 0);
        assertEquals("<0>", hc0.toString());

        LV lva = LV.createHC(hc, hc0);
        assertEquals("<>-4-<0>", lva.toString());
        // going from T -> List<T>, we move from <> to <0>

        // <1> as in V of Map<K, V>
        LV.HiddenContent hc1 = LV.typeParameter(null, 1);
        assertEquals("<1>", hc1.toString());

        // <0,1> as in Map<K,V>
        LV.HiddenContent hc0_1 = LV.typeParameters(null, List.of(0), null, List.of(1));
        assertEquals("<0,1>", hc0_1.toString());


        // <0-0,0-1> as in Set<Map.Entry<K, V>>
        LV.HiddenContent hc00_01 = LV.typeParameters(null, List.of(0, 0), null, List.of(0, 1));
        assertEquals("<0-0,0-1>", hc00_01.toString());
    }

    private static String sorted(DijkstraShortestPath.CurrentConnection cc) {
        if (cc instanceof LV.CurrentConnectionImpl cci) {
            return cci.set().stream().sorted().map(Object::toString).collect(Collectors.joining(", "));
        }
        throw new UnsupportedOperationException();
    }

    @Test
    public void test2() {
        // String
        LV.HiddenContent hcString = LV.from(typeContext.getPrimitives().stringParameterizedType());
        assertEquals("<>", hcString.toString());
        assertEquals("[]", hcString.apply(LV.CS_ALL).toString());

        // List<E>
        TypeInfo list = typeContext.getFullyQualified(List.class);
        ParameterizedType listE = list.asParameterizedType(typeContext);
        LV.HiddenContent hcList = LV.from(listE);
        assertEquals("<0>", hcList.toString());

        DijkstraShortestPath.ConnectionSelector cs0 = new LV.ConnectionSelectorImpl(Set.of(0));
        assertEquals("[0]", hcList.apply(cs0).toString());

        // List<String>
        ParameterizedType listString = new ParameterizedType(list,
                List.of(typeContext.getPrimitives().stringParameterizedType()));
        LV.HiddenContent hcListString = LV.from(listString);
        assertEquals("<*0>", hcListString.toString());
        assertEquals("[]", hcListString.apply(cs0).toString());

        // Map<K,V>
        TypeInfo map = typeContext.getFullyQualified(Map.class);
        LV.HiddenContent hcMap = LV.from(map.asParameterizedType(typeContext));
        assertEquals("<0,1>", hcMap.toString());

        DijkstraShortestPath.ConnectionSelector cs01 = new LV.ConnectionSelectorImpl(Set.of(0, 1));
        assertEquals("0, 1", sorted(hcMap.apply(cs01)));
        assertEquals("0, 1", sorted(hcMap.apply(LV.CS_ALL)));

        // Map<K,K>
        TypeParameter tp0 = typeContext.getTypeInspection(map).typeParameters().get(0);
        ParameterizedType pt0 = new ParameterizedType(tp0, 0, ParameterizedType.WildCard.NONE);
        ParameterizedType mapKK = new ParameterizedType(map, List.of(pt0, pt0));
        LV.HiddenContent hcMapKK = LV.from(mapKK);
        assertEquals("<0,0>", hcMapKK.toString());

        assertEquals("[0]", hcMapKK.apply(cs0).toString());
        assertEquals("[0]", hcMapKK.apply(cs01).toString());

        // K
        LV.HiddenContent hcK = LV.from(pt0);
        assertEquals("<>", hcK.toString());

        // Map<K, List<E>>
        ParameterizedType mapKListE = new ParameterizedType(map, List.of(pt0, listE));
        LV.HiddenContent hcMapKListE = LV.from(mapKListE);
        assertEquals("<0,*1-1>", hcMapKListE.toString());

        DijkstraShortestPath.ConnectionSelector cs1 = new LV.ConnectionSelectorImpl(Set.of(1));
        LV.HiddenContent one = LV.typeParameter(null, 1);
        assertEquals("1", sorted(hcMapKListE.apply(cs1)));

        // Map<K, List<K>>
        ParameterizedType listK = new ParameterizedType(list, List.of(pt0));
        ParameterizedType mapKListK = new ParameterizedType(map, List.of(pt0, listK));
        LV.HiddenContent hcMapKListK = LV.from(mapKListK);
        assertEquals("<0,*1-0>", hcMapKListK.toString());

        // be careful, using 2x the formal asParameterizedType() re-uses E, which may be counterintuitive.
        // Map<List<E>, List<E>>
        ParameterizedType mapListEListE = new ParameterizedType(map, List.of(listE, listE));
        LV.HiddenContent hcMapListEListE = LV.from(mapListEListE);
        assertEquals("<*0-0,*1-0>", hcMapListEListE.toString());
        assertEquals("0", sorted(hcMapListEListE.apply(cs01)));

        // Map<List<E>, List<E>>
        ParameterizedType mapListStringListString = new ParameterizedType(map, List.of(listString, listString));
        LV.HiddenContent hcMapListStringListString = LV.from(mapListStringListString);
        assertEquals("<*0-*0,*1-*0>", hcMapListStringListString.toString());
        assertEquals("", sorted(hcMapListStringListString.apply(cs1)));
    }
}
