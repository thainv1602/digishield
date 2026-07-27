package com.digishield;

import com.digishield.auth.api.AuthService;
import com.digishield.simulation.api.ParticipantDirectory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Wires the simulation module's {@link ParticipantDirectory} SPI to the auth
 * module, so a campaign's results table can name the people it was sent to.
 * Lives in the boot app to keep simulation decoupled from auth (mirrors
 * {@link AuthWorkforce}).
 */
@Component
class AuthParticipants implements ParticipantDirectory {

    private final AuthService authService;

    AuthParticipants(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public List<Participant> participants() {
        return authService.listUsers().stream()
                .map(u -> new Participant(u.id(), u.name(), u.department()))
                .toList();
    }
}
