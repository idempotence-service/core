package ru.itmo.idempotency.sender.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.idempotency.common.web.ApiResponse;
import ru.itmo.idempotency.sender.service.SenderDispatchService;
import ru.itmo.idempotency.sender.service.SenderStateService;

@RestController
@RequestMapping("/api/sender")
@RequiredArgsConstructor
public class SenderController {

    private final SenderDispatchService senderDispatchService;
    private final SenderStateService senderStateService;

    @PostMapping("/send")
    public ApiResponse<String> send(@Valid @RequestBody SenderDtos.SendEventRequest request) {
        String uid = senderDispatchService.send(
                request.integration(),
                request.uid(),
                request.headers(),
                request.payload(),
                request.duplicates()
        );
        return ApiResponse.success(uid);
    }

    @GetMapping("/sent")
    public ApiResponse<?> sentMessages() {
        return ApiResponse.success(senderStateService.sentMessages());
    }

    @GetMapping("/replies")
    public ApiResponse<?> replies() {
        return ApiResponse.success(senderStateService.receivedReplies());
    }

    @DeleteMapping("/state")
    public ApiResponse<String> reset() {
        senderStateService.reset();
        return ApiResponse.success("ok");
    }
}
