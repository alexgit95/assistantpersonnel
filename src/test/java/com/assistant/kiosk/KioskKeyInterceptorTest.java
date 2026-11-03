package com.assistant.kiosk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.Fixtures;
import com.assistant.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class KioskKeyInterceptorTest {

    static class Probe {
        @KioskOnly
        public void reserved() {
        }

        public void open() {
        }
    }

    @KioskOnly
    static class ReservedProbe {
        public void anything() {
        }
    }

    private static HandlerMethod handler(Object bean, String method) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(method));
    }

    private static MockHttpServletRequest requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (key != null) {
            request.addHeader(KioskKeyInterceptor.HEADER, key);
        }
        return request;
    }

    private static boolean call(KioskKeyInterceptor interceptor, MockHttpServletRequest request, HandlerMethod handler) {
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }

    @Test
    void validKeyIsAccepted() throws Exception {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey("secret"));
        assertThat(call(interceptor, requestWithKey("secret"), handler(new Probe(), "reserved"))).isTrue();
    }

    @Test
    void missingKeyIsForbidden() throws Exception {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey("secret"));
        assertThatThrownBy(() -> call(interceptor, requestWithKey(null), handler(new Probe(), "reserved")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void wrongKeyIsForbidden() throws Exception {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey("secret"));
        assertThatThrownBy(() -> call(interceptor, requestWithKey("other"), handler(new Probe(), "reserved")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> call(interceptor, requestWithKey("secret "), handler(new Probe(), "reserved")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void endpointsWithoutTheAnnotationAreNotChecked() throws Exception {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey("secret"));
        assertThat(call(interceptor, requestWithKey(null), handler(new Probe(), "open"))).isTrue();
    }

    @Test
    void classLevelAnnotationReservesEveryMethod() throws Exception {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey("secret"));
        assertThatThrownBy(() -> call(interceptor, requestWithKey(null), handler(new ReservedProbe(), "anything")))
                .isInstanceOf(ApiException.class);
        assertThat(call(interceptor, requestWithKey("secret"), handler(new ReservedProbe(), "anything"))).isTrue();
    }

    @Test
    void emptyConfiguredKeyDisablesTheCheck() throws Exception {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey(""));
        assertThatCode(() -> call(interceptor, requestWithKey(null), handler(new Probe(), "reserved")))
                .doesNotThrowAnyException();
    }

    @Test
    void nonControllerHandlersAreIgnored() {
        var interceptor = new KioskKeyInterceptor(Fixtures.withKioskKey("secret"));
        assertThat(interceptor.preHandle(requestWithKey(null), new MockHttpServletResponse(), new Object())).isTrue();
    }
}
