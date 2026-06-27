package com.digishield.interception.api;

import com.digishield.interception.api.dto.AccountWatchEntryView;
import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.api.dto.InterventionEventView;
import com.digishield.interception.domain.AccountWatchEntry;
import java.util.List;
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

    /**
     * Lists the current tenant's watchlist, most recently added first.
     */
    List<AccountWatchEntryView> listWatchlist();

    /**
     * Adds (or syncs) a suspicious recipient account into the current tenant's watchlist.
     *
     * @return the persisted entry view (with server-assigned id and added_at)
     */
    AccountWatchEntryView addWatchEntry(AccountWatchEntryView request);

    /**
     * Lists recorded intervention events for the current tenant, newest first.
     *
     * @param page 1-based page number
     * @param size page size
     */
    List<InterventionEventView> listInterventions(int page, int size);
}
