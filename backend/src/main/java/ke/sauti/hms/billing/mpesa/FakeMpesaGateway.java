package ke.sauti.hms.billing.mpesa;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A stand-in for development and tests, used until Daraja keys are set. By default every prompt
 * succeeds for the amount asked. Tests can script the next answer or a refused prompt.
 */
@Component
@ConditionalOnProperty(name = "sauti.mpesa.provider", havingValue = "fake", matchIfMissing = true)
public class FakeMpesaGateway implements MpesaGateway {

    private final AtomicInteger pushes = new AtomicInteger();
    private final Map<String, BigDecimal> amounts = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable QueryResult> scripted = new AtomicReference<>();
    private final AtomicReference<@Nullable RuntimeException> pushFailure = new AtomicReference<>();

    @Override
    public String stkPush(String phone, BigDecimal amountKes, String reference) {
        pushes.incrementAndGet();
        RuntimeException failure = pushFailure.get();
        if (failure != null) {
            throw failure;
        }
        String id = "ws_CO_FAKE_" + UUID.randomUUID();
        amounts.put(id, amountKes);
        return id;
    }

    @Override
    public QueryResult query(String checkoutRequestId) {
        QueryResult answer = scripted.get();
        return answer != null
                ? answer
                : new QueryResult(
                        Outcome.SUCCESS,
                        amounts.get(checkoutRequestId),
                        "FAKE" + checkoutRequestId.substring(checkoutRequestId.length() - 8));
    }

    /** Makes every later query give this answer; null goes back to success. */
    public void answerWith(@Nullable QueryResult answer) {
        scripted.set(answer);
    }

    /** Makes every later prompt throw this; null goes back to working. */
    public void failPushWith(@Nullable RuntimeException failure) {
        pushFailure.set(failure);
    }

    public int pushes() {
        return pushes.get();
    }
}
