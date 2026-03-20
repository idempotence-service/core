package ru.itmo.idempotency.receiver.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.idempotency.common.web.ApiResponse;
import ru.itmo.idempotency.receiver.service.ReceiverReplyService;
import ru.itmo.idempotency.receiver.service.ReceiverStateService;

@RestController
@RequestMapping("/api/receiver")
@RequiredArgsConstructor
public class ReceiverController {

    private final ReceiverStateService receiverStateService;
    private final ReceiverReplyService receiverReplyService;

    @GetMapping("/events")
    public ApiResponse<?> events() {
        return ApiResponse.success(receiverStateService.messages());
    }

    @PostMapping("/mode")
    public ApiResponse<String> mode(@Valid @RequestBody ReceiverDtos.ModeRequest request) {
        receiverStateService.setMode(request.integration(), request.mode());
        return ApiResponse.success("ok");
    }

    @PostMapping("/reply")
    public ApiResponse<String> reply(@Valid @RequestBody ReceiverDtos.ManualReplyRequest request) {
        receiverReplyService.sendReply(
                request.integration(),
                request.globalKey(),
                request.result(),
                request.needResend(),
                request.resultDescription()
        );
        return ApiResponse.success("ok");
    }

    @DeleteMapping("/state")
    public ApiResponse<String> reset() {
        receiverStateService.reset();
        return ApiResponse.success("ok");
    }
}
