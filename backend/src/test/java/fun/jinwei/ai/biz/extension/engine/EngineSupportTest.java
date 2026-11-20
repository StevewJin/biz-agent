package fun.jinwei.ai.biz.extension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 必填参数校验回归：数组/对象类型不能被 asText() 误判为空。
 */
class EngineSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema() throws Exception {
        return mapper.readTree("""
                {"type":"object","required":["labels","datasets","title"]}
                """);
    }

    @Test
    void arrayAndObjectRequiredFieldsAreAccepted() throws Exception {
        JsonNode params = mapper.readTree("""
                {"labels":["A","B"],"datasets":[{"label":"x","data":[1,2]}],"title":"t"}
                """);
        EngineSupport.assertRequired(schema(), params);
    }

    @Test
    void emptyArrayIsRejected() throws Exception {
        JsonNode params = mapper.readTree("""
                {"labels":[],"datasets":[{"label":"x","data":[1]}],"title":"t"}
                """);
        ExtensionException ex = assertThrows(ExtensionException.class,
                () -> EngineSupport.assertRequired(schema(), params));
        assertEquals(true, ex.getMessage().contains("labels"));
    }

    @Test
    void blankTextIsRejected() throws Exception {
        JsonNode params = mapper.readTree("""
                {"labels":["A"],"datasets":[{"label":"x","data":[1]}],"title":"   "}
                """);
        ExtensionException ex = assertThrows(ExtensionException.class,
                () -> EngineSupport.assertRequired(schema(), params));
        assertEquals(true, ex.getMessage().contains("title"));
    }

    @Test
    void toParamsFlattensNestedObjects() throws Exception {
        JsonNode params = mapper.readTree("""
                {"datasource":"ds","params":{"region":"east"},"n":3}
                """);
        Map<String, Object> result = EngineSupport.toParams(params);
        assertEquals("ds", result.get("datasource"));
        assertEquals(3, result.get("n"));
        assertEquals(Map.of("region", "east"), result.get("params"));
    }

    @Test
    void formatRowsRendersMarkdownTable() {
        // 用 LinkedHashMap 固定列顺序，避免 Map.of 的不确定迭代顺序导致断言语义不稳定
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("a", 1);
        row.put("b", 2);
        String text = EngineSupport.formatRows(List.of(row), 10);
        assertEquals(true, text.contains("| a | b |"));
        assertEquals(true, text.contains("| 1 | 2 |"));
    }
}
