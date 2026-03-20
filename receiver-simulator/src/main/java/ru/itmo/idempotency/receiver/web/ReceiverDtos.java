package ru.itmo.idempotency.receiver.web;

import jakarta.validation.constraints.NotBlank;
import ru.itmo.idempotency.receiver.service.ReceiverMode;

public final class ReceiverDtos {

    private ReceiverDtos() {
    }

    public record ModeRequest(@NotBlank String integration, ReceiverMode mode) {
    }

    public record ManualReplyRequest(@NotBlank String integration,
                                     @NotBlank String globalKey,
                                     @NotBlank String result,
                                     boolean needResend,
                                     String resultDescription) {
    }
}
