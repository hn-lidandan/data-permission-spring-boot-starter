package org.com.it.permission.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SDK 内置的权限服务 HTTP 客户端。
 *
 * <p>业务服务不需要自己写调用权限服务的代码，只需要配置 {@code data-permission.service-url}。
 * 请求进入业务服务后，SDK 会根据当前身份向权限服务发起 POST 请求，获取打平后的权限上下文。</p>
 */
public class HttpPermissionServiceClient implements PermissionServiceClient {

    private static final int HTTP_OK = 200;

    private final URI endpoint;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final Duration requestTimeout;

    public HttpPermissionServiceClient(DataPermissionProperties properties) {
        this(
                URI.create(properties.getServiceUrl().trim()),
                createHttpClient(properties),
                createObjectMapper(),
                requestTimeout(properties)
        );
    }

    HttpPermissionServiceClient(URI endpoint,
                                HttpClient httpClient,
                                ObjectMapper objectMapper,
                                Duration requestTimeout) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private static HttpClient createHttpClient(DataPermissionProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getClient().getConnectTimeoutMillis()))
                .build();
    }

    private static Duration requestTimeout(DataPermissionProperties properties) {
        return Duration.ofMillis(properties.getClient().getRequestTimeoutMillis());
    }

    @Override
    public PermissionContext queryContext(PermissionIdentity identity) {
        try {
            PermissionContextQueryRequest requestBody = PermissionContextQueryRequest.from(identity);
            String json = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PermissionDeniedException("Permission service request was interrupted", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new PermissionDeniedException("Permission service request failed", ex);
        }
    }

    private PermissionContext handleResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() == HTTP_OK) {
            if (!StringUtils.hasText(response.body())) {
                throw new PermissionDeniedException("Permission service returned empty context");
            }
            return readPermissionContext(response.body());
        }

        // 权限服务没有返回可用上下文时，SDK 默认拒绝，避免权限服务异常时误放行数据。
        throw new PermissionDeniedException("Permission service returned status " + response.statusCode());
    }

    private PermissionContext readPermissionContext(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        // 兼容两种返回：一种是直接返回上下文对象；另一种是统一响应包在 data 字段里。
        JsonNode contextNode = root.has("data") && root.get("data").isObject() ? root.get("data") : root;
        return objectMapper.treeToValue(contextNode, PermissionContext.class);
    }

    /**
     * 查询权限上下文的请求体。
     *
     * <p>字段名保持权限服务接口约定的 snake_case，避免 SDK 和权限服务之间再做额外字段转换。</p>
     */
    static class PermissionContextQueryRequest {

        @JsonProperty("tenant_id")
        private final String tenantId;

        @JsonProperty("user_id")
        private final String userId;

        @JsonProperty("dept_id")
        private final String deptId;

        @JsonProperty("role_id")
        private final String roleId;

        private PermissionContextQueryRequest(String tenantId,
                                              String userId,
                                              String deptId,
                                              String roleId) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.deptId = deptId;
            this.roleId = roleId;
        }

        static PermissionContextQueryRequest from(PermissionIdentity identity) {
            requireText(identity.getTenantId(), "tenant_id");
            requireText(identity.getUserId(), "user_id");
            requireText(identity.getDeptId(), "dept_id");
            requireText(identity.getRoleId(), "role_id");
            return new PermissionContextQueryRequest(
                    identity.getTenantId(),
                    identity.getUserId(),
                    identity.getDeptId(),
                    identity.getRoleId()
            );
        }

        private static void requireText(String value, String fieldName) {
            if (!StringUtils.hasText(value)) {
                throw new PermissionDeniedException("Missing required permission context request field: " + fieldName);
            }
        }
    }
}
