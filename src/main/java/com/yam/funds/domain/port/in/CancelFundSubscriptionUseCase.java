package com.yam.funds.domain.port.in;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.IdempotencyConflictException;
import com.yam.funds.domain.exception.OperationInProgressException;
import com.yam.funds.domain.exception.SubscriptionNotFoundException;
import com.yam.funds.domain.port.in.command.CancelFundSubscriptionCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import reactor.core.publisher.Mono;

/** Cancels a client's subscription, returning the linked amount to their balance. */
public interface CancelFundSubscriptionUseCase {

    /**
     * Cancels the client's active subscription to the fund.
     *
     * <p>The full linked amount is returned to the balance — this domain applies no
     * yield or penalty on cancellation. The balance credit and the ledger entry are
     * committed together.
     *
     * @param command the cancellation request, carrying its idempotency key
     * @return the resulting ledger entry, flagged as replayed when the key had already
     *         been used with an identical request
     * @throws ClientNotFoundException       if the client does not exist
     * @throws SubscriptionNotFoundException if the client holds no active subscription
     *                                       to the fund
     * @throws IdempotencyConflictException  if the key was reused with a different request
     * @throws OperationInProgressException  if a request with the same key is still running
     */
    Mono<TransactionReceipt> cancelFundSubscription(CancelFundSubscriptionCommand command);
}
