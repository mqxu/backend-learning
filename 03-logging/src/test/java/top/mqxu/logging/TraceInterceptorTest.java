package top.mqxu.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.mqxu.logging.common.TraceInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

class TraceInterceptorTest {

    private final TraceInterceptor interceptor = new TraceInterceptor();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void 没有上游编号时自动生成() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        String traceId = MDC.get("traceId");
        assertThat(traceId).isNotBlank().hasSize(16);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
    }

    @Test
    void 上游带了编号就沿用() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "upstream-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("traceId")).isEqualTo("upstream-0001");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("upstream-0001");
    }

    @Test
    void 请求结束后清理MDC() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        assertThat(MDC.get("traceId")).isNotNull();

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(MDC.get("traceId")).isNull();
    }
}
