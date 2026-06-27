package com.digishield.interception.api;

import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.domain.AccountWatchEntry;
import java.util.Optional;

/**
 * Public API of the Interception module.
 */
public interface InterceptionService {

    /**
     * Evaluates a transaction and returns an intervention decision.
     */
    InterventionDecision evaluate(EvaluateRequest request);

    /**
     * Checks whether an identifier (account/phone/wallet) is in the tenant's watchlist.
     */
    Optional<AccountWatchEntry> checkAccount(String value);
}
