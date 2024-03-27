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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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

    private static String sorted(DijkstraShortestPath.Connection cc) {
        if (cc instanceof HiddenContentSelector cci) {
            return cci.set().stream().sorted().map(Object::toString).collect(Collectors.joining(", "));
        }
        throw new UnsupportedOperationException();
    }

    @Test
    public void test() {
        // String
        HiddenContent hcString = HiddenContent.from(typeContext.getPrimitives().stringParameterizedType());
        assertEquals("<>", hcString.toString());
        assertEquals("<>", hcString.selectAll().toString());

        // List<E>
        TypeInfo list = typeContext.getFullyQualified(List.class);
        ParameterizedType listE = list.asParameterizedType(typeContext);
        HiddenContent hcList = HiddenContent.from(listE);
        assertEquals("<0>", hcList.toString());

        // List<String>
        ParameterizedType listString = new ParameterizedType(list,
                List.of(typeContext.getPrimitives().stringParameterizedType()));
        HiddenContent hcListString = HiddenContent.from(listString);
        assertEquals("<*0>", hcListString.toString());
        assertEquals("<>", hcListString.selectAll().toString());

        // Map<K,V>
        TypeInfo map = typeContext.getFullyQualified(Map.class);
        HiddenContent hcMap = HiddenContent.from(map.asParameterizedType(typeContext));
        assertEquals("<0,1>", hcMap.toString());
        assertEquals("0, 1", sorted(hcMap.selectAll()));

        // Map<K,K>
        TypeParameter tp0 = typeContext.getTypeInspection(map).typeParameters().get(0);
        ParameterizedType pt0 = new ParameterizedType(tp0, 0, ParameterizedType.WildCard.NONE);
        ParameterizedType mapKK = new ParameterizedType(map, List.of(pt0, pt0));
        HiddenContent hcMapKK = HiddenContent.from(mapKK);
        assertEquals("<0,0>", hcMapKK.toString());

        assertEquals("<0>", hcMapKK.selectAll().toString());

        // K
        HiddenContent hcK = HiddenContent.from(pt0);
        assertEquals("<>", hcK.toString());

        // Map<K, List<E>>
        ParameterizedType mapKListE = new ParameterizedType(map, List.of(pt0, listE));
        HiddenContent hcMapKListE = HiddenContent.from(mapKListE);
        assertEquals("<0,*1-1>", hcMapKListE.toString());
        assertEquals("0, 1", sorted(hcMapKListE.selectAll()));

        // Map<K, List<K>>
        ParameterizedType listK = new ParameterizedType(list, List.of(pt0));
        ParameterizedType mapKListK = new ParameterizedType(map, List.of(pt0, listK));
        HiddenContent hcMapKListK = HiddenContent.from(mapKListK);
        assertEquals("<0,*1-0>", hcMapKListK.toString());

        // be careful, using 2x the formal asParameterizedType() re-uses E, which may be counterintuitive.
        // Map<List<E>, List<E>>
        ParameterizedType mapListEListE = new ParameterizedType(map, List.of(listE, listE));
        HiddenContent hcMapListEListE = HiddenContent.from(mapListEListE);
        assertEquals("<*0-0,*1-0>", hcMapListEListE.toString());
        assertEquals("0", sorted(hcMapListEListE.selectAll()));

        // Map<List<E>, List<E>>
        ParameterizedType mapListStringListString = new ParameterizedType(map, List.of(listString, listString));
        HiddenContent hcMapListStringListString = HiddenContent.from(mapListStringListString);
        assertEquals("<*0-*0,*1-*0>", hcMapListStringListString.toString());
        assertEquals("", sorted(hcMapListStringListString.selectAll()));
    }
}
