package com.rikkeibank.notification.controller;

import com.rikkeibank.common.dto.ApiResponse;
import com.rikkeibank.common.event.TransactionEvent;
import com.rikkeibank.notification.entity.Notification;
import com.rikkeibank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getAllNotifications() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getAllNotifications()));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<List<Notification>>> getByAccount(@PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationsByAccount(accountNumber)));
    }

    @PostMapping("/process-event")
    public ResponseEntity<ApiResponse<String>> processManualEvent(@RequestBody TransactionEvent event) {
        notificationService.processEvent(event);
        return ResponseEntity.ok(ApiResponse.success("Notification event processed successfully", "PROCESSED"));
    }
}
