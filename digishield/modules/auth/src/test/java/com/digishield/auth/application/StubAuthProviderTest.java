package com.digishield.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.digishield.auth.api.MfaSetupView;
import com.digishield.auth.api.TokenPair;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the dev {@link StubAuthProvider}.
 */
class StubAuthProviderTest {

    private final StubAuthProvider provider = new StubAuthProvider();

    @Test
    void login_returnsStaticDevTokens() {
        TokenPair tokens = provider.login("user@digishield.vn", "irrelevant");
        assertThat(tokens.accessToken()).isEqualTo("dev-access-token");
        assertThat(tokens.refreshToken()).isEqualTo("dev-refresh-token");
        assertThat(tokens.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void mfaVerify_returnsEightRecoveryCodes() {
        assertThat(provider.mfaVerify("000000")).hasSize(8);
    }

    @Test
    void mfaSetup_buildsAnOtpauthUrlForTheAccount() {
        MfaSetupView view = provider.mfaSetup("minh@coquan.gov.vn");
        assertThat(view.secret()).isNotBlank();
        assertThat(view.otpauthUrl()).startsWith("otpauth://totp/").contains("secret=").contains("issuer=DigiShield");
        assertThat(view.qrSvg()).contains("<svg");
    }
}
