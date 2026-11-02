package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.assistant.Fixtures;
import com.assistant.config.AssistantProperties.Backend;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static AssistantProperties withPublicBaseUrl(String url) {
        var base = Fixtures.properties();
        return new AssistantProperties(base.session(), base.scan(), base.recognition(), base.enrollment(),
                base.weather(), base.ui(), base.backup(), base.faceAssets(), base.prim(), url, base.kiosk(),
                base.handoff(), base.qr(), base.http(), base.https());
    }

    @Test
    void defaultsAreValid() {
        assertThat(validator.validate(Fixtures.properties())).isEmpty();
    }

    @Test
    void detectorInputSizeMustBeMultipleOf32() {
        var recognition = new AssistantProperties.Recognition(0.55, 0.05, 2, 200, 0.5, 80, 640, Backend.AUTO, false);
        assertThat(validator.validate(recognition)).hasSize(1);
    }

    @Test
    void thresholdOutOfRangeIsRejected() {
        var recognition = new AssistantProperties.Recognition(5.0, 0.05, 2, 224, 0.5, 80, 640, Backend.AUTO, false);
        assertThat(validator.validate(recognition)).isNotEmpty();
    }

    @Test
    void photoCountMustBeBetweenOneAndThirty() {
        var remote = new AssistantProperties.Enrollment.Remote(5, 4, 10, 3);
        assertThat(validator.validate(new AssistantProperties.Enrollment(8, 0, remote))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Enrollment(0, 0, remote))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Enrollment(31, 0, remote))).isNotEmpty();
    }

    @Test
    void negativeMaxProfilesIsRejected() {
        var remote = new AssistantProperties.Enrollment.Remote(5, 4, 10, 3);
        assertThat(validator.validate(new AssistantProperties.Enrollment(8, -1, remote))).isNotEmpty();
    }

    @Test
    void remoteCaptureSettingsAreBounded() {
        assertThat(validator.validate(new AssistantProperties.Enrollment.Remote(0, 4, 10, 3))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Enrollment.Remote(5, 0, 10, 3))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Enrollment.Remote(5, 4, 0, 3))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Enrollment.Remote(5, 4, 10, 0))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Enrollment.Remote(5, 4, 10, 3))).isEmpty();
    }

    @Test
    void qrSizeMustStayInAReadableRange() {
        assertThat(validator.validate(new AssistantProperties.Qr(false, 9.0))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Qr(false, 2.0))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Qr(false, 30.0))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Qr(false, 1.9))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Qr(false, 30.1))).isNotEmpty();
    }

    @Test
    void sessionDurationMustBePositive() {
        assertThat(validator.validate(new AssistantProperties.Session(0, 30, 60))).isNotEmpty();
    }

    @Test
    void publicBaseUrlAcceptsEmptyAndHttpUrlsWithoutTrailingSlash() {
        assertThat(validator.validate(withPublicBaseUrl(""))).isEmpty();
        assertThat(validator.validate(withPublicBaseUrl("http://192.168.1.50:8080"))).isEmpty();
        assertThat(validator.validate(withPublicBaseUrl("https://assistant.example/app"))).isEmpty();
    }

    @Test
    void publicBaseUrlRejectsTrailingSlashAndOtherSchemes() {
        assertThat(validator.validate(withPublicBaseUrl("http://192.168.1.50:8080/"))).isNotEmpty();
        assertThat(validator.validate(withPublicBaseUrl("ftp://192.168.1.50"))).isNotEmpty();
        assertThat(validator.validate(withPublicBaseUrl("192.168.1.50:8080"))).isNotEmpty();
        assertThat(validator.validate(withPublicBaseUrl("http://bad host"))).isNotEmpty();
    }

    @Test
    void kioskPollIntervalsAreBounded() {
        assertThat(validator.validate(new AssistantProperties.Kiosk("", 3000, 1000))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Kiosk("", 100, 1000))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Kiosk("", 3000, 70_000))).isNotEmpty();
    }

    @Test
    void handoffSettingsAreBounded() {
        assertThat(validator.validate(new AssistantProperties.Handoff(120, 30, 10))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Handoff(5, 30, 10))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Handoff(120, 0, 10))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Handoff(120, 30, 2000))).isNotEmpty();
    }

    private static AssistantProperties.Https https(boolean enabled, String path, String password, String hostnames) {
        return new AssistantProperties.Https(enabled, 8443, "/certs", path, "PKCS12", password, hostnames, 825);
    }

    @Test
    void httpPortsAreBounded() {
        assertThat(validator.validate(new AssistantProperties.Http(true, 8080, 8080))).isEmpty();
        assertThat(validator.validate(new AssistantProperties.Http(true, 0, 8080))).isNotEmpty();
        assertThat(validator.validate(new AssistantProperties.Http(true, 8080, 70_000))).isNotEmpty();
    }

    @Test
    void httpsDisabledNeedsNoKeystore() {
        assertThat(validator.validate(https(false, "", "", ""))).isEmpty();
    }

    @Test
    void httpsEnabledNeedsKeystoreAndPassword() {
        assertThat(validator.validate(https(true, "/certs/assistant.p12", "secret", ""))).isEmpty();
        assertThat(validator.validate(https(true, "", "secret", ""))).isNotEmpty();
        assertThat(validator.validate(https(true, "/certs/assistant.p12", "", ""))).isNotEmpty();
    }

    @Test
    void httpsHostnamesAcceptNamesAndAddressesOnly() {
        assertThat(validator.validate(https(false, "", "", "serveur.example,192.168.1.50,[::1]"))).isEmpty();
        assertThat(validator.validate(https(false, "", "", "bad host"))).isNotEmpty();
        assertThat(validator.validate(https(false, "", "", "a;rm -rf /"))).isNotEmpty();
        assertThat(validator.validate(https(false, "", "", "a,,b"))).isNotEmpty();
    }

    @Test
    void httpsValidityMustBePositive() {
        var https = new AssistantProperties.Https(false, 8443, "/certs", "", "PKCS12", "", "", 0);
        assertThat(validator.validate(https)).isNotEmpty();
    }

    @Test
    void httpAndHttpsPortsMustDifferWhenBothEnabled() {
        var base = Fixtures.properties();
        var same = new AssistantProperties(base.session(), base.scan(), base.recognition(), base.enrollment(),
                base.weather(), base.ui(), base.backup(), base.faceAssets(), base.prim(), base.publicBaseUrl(),
                base.kiosk(), base.handoff(), base.qr(), new AssistantProperties.Http(true, 8443, 8443),
                https(true, "/certs/assistant.p12", "secret", ""));
        assertThat(validator.validate(same)).isNotEmpty();
        var different = new AssistantProperties(base.session(), base.scan(), base.recognition(), base.enrollment(),
                base.weather(), base.ui(), base.backup(), base.faceAssets(), base.prim(), base.publicBaseUrl(),
                base.kiosk(), base.handoff(), base.qr(), new AssistantProperties.Http(true, 8080, 8080),
                https(true, "/certs/assistant.p12", "secret", ""));
        assertThat(validator.validate(different)).isEmpty();
    }
}
