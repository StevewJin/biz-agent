package fun.jinwei.ai.biz.service.datasource;

import fun.jinwei.ai.biz.extension.api.ExtensionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 只读 SQL 执行器的安全约束与参数绑定回归测试。
 */
class ReadOnlySqlExecutorTest {

    private ReadOnlySqlExecutor executor() {
        // 校验在获取连接之前完成，这里不需要真实数据源
        return new ReadOnlySqlExecutor(null, "ds_test", 100, 5);
    }

    @Test
    void rejectsDmlAndDdl() {
        assertThrows(ExtensionException.class,
                () -> executor().query("update ba_users set username = 'x'", Map.of()));
        assertThrows(ExtensionException.class,
                () -> executor().query("delete from ba_users", Map.of()));
        assertThrows(ExtensionException.class,
                () -> executor().query("drop table ba_users", Map.of()));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(ExtensionException.class,
                () -> executor().query("select 1; select 2", Map.of()));
    }

    @Test
    void rejectsBlankSql() {
        assertThrows(ExtensionException.class, () -> executor().query("  ", Map.of()));
    }

    @Test
    void bindsNamedParametersInOrder() {
        List<Object> ordered = new ArrayList<>();
        String sql = ReadOnlySqlExecutor.bindNamedParams(
                "select * from t where a = #{a} and b = :b", Map.of("a", 1, "b", "x"), ordered);
        assertEquals("select * from t where a = ? and b = ?", sql);
        assertEquals(List.of(1, "x"), ordered);
    }

    @Test
    void ignoresColonInsideLiteralAndCast() {
        List<Object> ordered = new ArrayList<>();
        String sql = ReadOnlySqlExecutor.bindNamedParams(
                "select '12:30'::time as t from x", Map.of(), ordered);
        assertEquals("select '12:30'::time as t from x", sql);
        assertEquals(0, ordered.size());
    }

    @Test
    void missingNamedParameterBindsNull() {
        List<Object> ordered = new ArrayList<>();
        ReadOnlySqlExecutor.bindNamedParams("select #{missing}", Map.of(), ordered);
        assertEquals(1, ordered.size());
        assertEquals(null, ordered.get(0));
    }
}
