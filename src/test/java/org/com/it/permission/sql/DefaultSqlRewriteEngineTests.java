package org.com.it.permission.sql;

import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.exception.UnsupportedPermissionSqlException;
import org.com.it.permission.model.PermissionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SQL 行级权限改写核心测试。
 */
class DefaultSqlRewriteEngineTests {

    private final SqlRewriteEngine rewriteEngine = new DefaultSqlRewriteEngine(
            new SqlTableExtractor(),
            new PermissionResourceMatcher(),
            new PermissionFilterBuilder()
    );

    @Test
    void shouldRewriteSingleTableSelectWhenTableInResourceObject() {
        SqlRewriteResult result = rewriteEngine.rewrite(
                "SELECT * FROM security_log",
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isTrue();
        assertThat(normalize(result.getSql()))
                .isEqualTo("SELECT * FROM security_log WHERE tenant_id = ? AND dept_id IN (?, ?)");
        assertThat(result.getAdditionalParameters())
                .extracting(PermissionSqlParameter::getName)
                .containsExactly("__dp_tenant_id", "__dp_dept_id_0", "__dp_dept_id_1");
        assertThat(result.getAdditionalParameters())
                .extracting(PermissionSqlParameter::getValue)
                .containsExactly("ddd111", "d001", "d002");
    }

    @Test
    void shouldAppendFilterAfterExistingWhere() {
        SqlRewriteResult result = rewriteEngine.rewrite(
                "SELECT * FROM security_log WHERE status = ?",
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isTrue();
        assertThat(normalize(result.getSql()))
                .isEqualTo("SELECT * FROM security_log WHERE (status = ?) AND tenant_id = ? AND dept_id IN (?, ?)");
    }

    @Test
    void shouldKeepOrExpressionPrecedenceWhenAppendingFilter() {
        SqlRewriteResult result = rewriteEngine.rewrite(
                "SELECT * FROM security_log WHERE status = ? OR level = ?",
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isTrue();
        assertThat(normalize(result.getSql()))
                .isEqualTo("SELECT * FROM security_log WHERE (status = ? OR level = ?) AND tenant_id = ? AND dept_id IN (?, ?)");
    }

    @Test
    void shouldRewriteSingleTableCount() {
        SqlRewriteResult result = rewriteEngine.rewrite(
                "SELECT count(*) FROM security_log",
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isTrue();
        assertThat(normalize(result.getSql()))
                .isEqualTo("SELECT count(*) FROM security_log WHERE tenant_id = ? AND dept_id IN (?, ?)");
    }

    @Test
    void shouldSkipWhenTableNotInResourceObject() {
        SqlRewriteResult result = rewriteEngine.rewrite(
                "SELECT * FROM dict_area",
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isFalse();
        assertThat(result.getSql()).isEqualTo("SELECT * FROM dict_area");
        assertThat(result.getAdditionalParameters()).isEmpty();
    }

    @Test
    void shouldSkipAndWarnWhenSqlParseFailed() {
        String invalidSql = "SELECT * FROM security_log WHERE AND";

        SqlRewriteResult result = rewriteEngine.rewrite(
                invalidSql,
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isFalse();
        assertThat(result.getSql()).isEqualTo(invalidSql);
    }

    @Test
    void shouldRejectJoinWhenControlledTableIsInvolved() {
        assertThatThrownBy(() -> rewriteEngine.rewrite(
                "SELECT * FROM security_log s JOIN asset a ON s.asset_id = a.id",
                permissionContext("security_log"),
                "QUERY"
        )).isInstanceOf(UnsupportedPermissionSqlException.class)
                .hasMessageContaining("JOIN");
    }

    @Test
    void shouldSkipJoinWhenControlledTableIsNotInvolved() {
        SqlRewriteResult result = rewriteEngine.rewrite(
                "SELECT * FROM dict_area d JOIN dict_type t ON d.type_id = t.id",
                permissionContext("security_log"),
                "QUERY"
        );

        assertThat(result.isRewritten()).isFalse();
    }

    @Test
    void shouldRejectUpdateWhenControlledTableIsInvolved() {
        assertThatThrownBy(() -> rewriteEngine.rewrite(
                "UPDATE security_log SET status = ? WHERE id = ?",
                permissionContext("security_log"),
                "QUERY"
        )).isInstanceOf(UnsupportedPermissionSqlException.class)
                .hasMessageContaining("UPDATE");
    }

    @Test
    void shouldRejectWhenTenantIdMissing() {
        PermissionContext context = permissionContext("security_log");
        context.setTenantId(null);

        assertThatThrownBy(() -> rewriteEngine.rewrite(
                "SELECT * FROM security_log",
                context,
                "QUERY"
        )).isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldRejectWhenDeptScopeMissing() {
        PermissionContext context = permissionContext("security_log");
        context.setDeptScope(List.of());

        assertThatThrownBy(() -> rewriteEngine.rewrite(
                "SELECT * FROM security_log",
                context,
                "QUERY"
        )).isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("dept_scope");
    }

    private PermissionContext permissionContext(String resourceId) {
        PermissionContext context = new PermissionContext();
        context.setTenantId("ddd111");
        context.setUserId("dfew123");
        context.setDeptScope(List.of("d001", "d002"));
        context.getResourceObject().put("doris", List.of(resourceId));
        return context;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
