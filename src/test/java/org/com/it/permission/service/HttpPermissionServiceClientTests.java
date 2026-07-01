package org.com.it.permission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SDK 内置 HTTP 权限服务客户端测试。
 *
 * <p>这里通过假的 HttpClient 拦截请求，不绑定本地端口。这样既能验证 SDK 会自己发 POST 请求，
 * 也能避免测试环境因为禁止监听端口而失败。</p>
 */
class HttpPermissionServiceClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPostIdentityAndDeserializePermissionContext() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, """
                {
                  "user_id": "u001",
                  "tenant_id": "t001",
                  "dept_scope": ["d001", "d002"],
                  "asset_group_scope": ["ag001", "ag002"],
                  "data_level_scope": ["public", "internal"],
                  "resource_object": {
                    "doris": ["security_log", "alert_event"],
                    "postgre_sql": ["asset", "report"]
                  },
                  "field_policies": [
                    {
                      "field_name": "phone",
                      "permission_type": "MASK",
                      "mask_regex": "PHONE",
                      "scene": ["DETAIL"]
                    }
                  ],
                  "export_policies": [
                    {
                      "resource_type": "doris",
                      "resource_id": "security_log",
                      "allowed": true,
                      "is_allowed": true,
                      "max_rows": 5000,
                      "need_approval": false
                    }
                  ],
                  "deny_all": false,
                  "version": 12,
                  "expires_at": "2026-07-01T10:00:00"
                }
                """);
        HttpPermissionServiceClient client = client(httpClient);

        PermissionContext context = client.queryContext(identity());

        HttpRequest request = httpClient.capturedRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString())
                .isEqualTo("http://localhost:8081/data-auth-center/data-permissions/context");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(request.headers().firstValue("Accept")).contains("application/json");

        JsonNode body = objectMapper.readTree(httpClient.capturedRequestBody());
        assertThat(body.get("tenant_id").asText()).isEqualTo("t001");
        assertThat(body.get("user_id").asText()).isEqualTo("u001");
        assertThat(body.get("dept_id").asText()).isEqualTo("d001");
        assertThat(body.get("role_id").asText()).isEqualTo("r001");
        assertThat(body.has("client_app")).isFalse();

        assertThat(context.getUserId()).isEqualTo("u001");
        assertThat(context.getTenantId()).isEqualTo("t001");
        assertThat(context.getDeptScope()).containsExactly("d001", "d002");
        assertThat(context.getResourceObject()).containsKey("doris");
        assertThat(context.getFieldPolicies()).hasSize(1);
        assertThat(context.getExportPolicies()).hasSize(1);
        assertThat(context.isDenyAll()).isFalse();
        assertThat(context.getVersion()).isEqualTo(12);
        assertThat(context.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-07-01T10:00:00"));
    }

    @Test
    void shouldDeserializeWrappedDataResponse() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, """
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "user_id": "u001",
                    "tenant_id": "t001",
                    "deny_all": false,
                    "expires_at": "2026-06-29T18:00:00"
                  }
                }
                """);
        HttpPermissionServiceClient client = client(httpClient);

        PermissionContext context = client.queryContext(identity());

        assertThat(context.getUserId()).isEqualTo("u001");
        assertThat(context.getTenantId()).isEqualTo("t001");
        assertThat(context.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-06-29T18:00:00"));
    }

    @Test
    void shouldDenyWhenPermissionServiceReturnsError() {
        CapturingHttpClient httpClient = new CapturingHttpClient(500, """
                {"message": "permission context is unavailable"}
                """);
        HttpPermissionServiceClient client = client(httpClient);

        assertThatThrownBy(() -> client.queryContext(identity()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Permission service returned status 500");
    }

    @Test
    void shouldDenyWhenRequiredRequestFieldMissing() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, "{}");
        HttpPermissionServiceClient client = client(httpClient);
        PermissionIdentity identity = PermissionIdentity.builder()
                .tenantId("t001")
                .userId("u001")
                .deptId("d001")
                .build();

        assertThatThrownBy(() -> client.queryContext(identity))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("role_id");
        assertThat(httpClient.capturedRequest()).isNull();
    }

    private HttpPermissionServiceClient client(HttpClient httpClient) {
        return new HttpPermissionServiceClient(
                URI.create("http://localhost:8081/data-auth-center/data-permissions/context"),
                httpClient,
                objectMapper,
                Duration.ofSeconds(3)
        );
    }

    private PermissionIdentity identity() {
        return PermissionIdentity.builder()
                .tenantId("t001")
                .userId("u001")
                .deptId("d001")
                .roleId("r001")
                .clientApp("log-service")
                .build();
    }

    /**
     * 测试专用 HttpClient。
     *
     * <p>它不会真的发网络请求，只记录 SDK 生成的 HttpRequest，然后返回测试指定的响应。</p>
     */
    static class CapturingHttpClient extends HttpClient {

        private final int statusCode;

        private final String responseBody;

        private final AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();

        private final AtomicReference<String> capturedRequestBody = new AtomicReference<>();

        CapturingHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            capturedRequest.set(request);
            capturedRequestBody.set(readBody(request));
            @SuppressWarnings("unchecked")
            T typedBody = (T) responseBody;
            return new SimpleHttpResponse<>(statusCode, typedBody, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException("WebSocket is not used in this test");
        }

        HttpRequest capturedRequest() {
            return capturedRequest.get();
        }

        String capturedRequestBody() {
            return capturedRequestBody.get();
        }

        private String readBody(HttpRequest request) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            HttpRequest.BodyPublisher publisher = request.bodyPublisher()
                    .orElseThrow(() -> new IllegalStateException("Request body is missing"));
            publisher.subscribe(new Flow.Subscriber<>() {

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    outputStream.writeBytes(bytes);
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new IllegalStateException("Failed to read request body", throwable);
                }

                @Override
                public void onComplete() {
                    // BodyPublisher 是同步的 ofString，这里不需要额外处理。
                }
            });
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    static class SimpleHttpResponse<T> implements HttpResponse<T> {

        private final int statusCode;

        private final T body;

        private final HttpRequest request;

        SimpleHttpResponse(int statusCode, T body, HttpRequest request) {
            this.statusCode = statusCode;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
