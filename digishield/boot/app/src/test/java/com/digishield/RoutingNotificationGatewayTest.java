package com.digishield;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RoutingNotificationGateway}: each channel goes to its own
 * gateway, and anything without an enabled gateway is a no-op (logged, not sent).
 */
class RoutingNotificationGatewayTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<SesEmailNotificationGateway> emailProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SnsSmsNotificationGateway> smsProvider = mock(ObjectProvider.class);
    private final SesEmailNotificationGateway ses = mock(SesEmailNotificationGateway.class);
    private final SnsSmsNotificationGateway sns = mock(SnsSmsNotificationGateway.class);

    private final RoutingNotificationGateway routing =
            new RoutingNotificationGateway(emailProvider, smsProvider);

    @Test
    void email_routesToSesWhenAvailable() {
        when(emailProvider.getIfAvailable()).thenReturn(ses);

        routing.deliver("EMAIL", "user@example.com", "S", "B");

        verify(ses).deliver("EMAIL", "user@example.com", "S", "B");
        verifyNoInteractions(sns);
    }

    @Test
    void sms_routesToSnsWhenAvailable() {
        when(smsProvider.getIfAvailable()).thenReturn(sns);

        routing.deliver("SMS", "+84901234561", "S", "B");

        verify(sns).deliver("SMS", "+84901234561", "S", "B");
        verifyNoInteractions(ses);
    }

    @Test
    void email_isNoOpWhenSesDisabled() {
        when(emailProvider.getIfAvailable()).thenReturn(null);

        routing.deliver("EMAIL", "user@example.com", "S", "B");

        verify(emailProvider).getIfAvailable();
        verifyNoInteractions(ses, sns);
    }

    @Test
    void unknownChannel_isNoOp() {
        routing.deliver("PUSH", "token", "S", "B");

        verifyNoInteractions(emailProvider, smsProvider, ses, sns);
    }
}
