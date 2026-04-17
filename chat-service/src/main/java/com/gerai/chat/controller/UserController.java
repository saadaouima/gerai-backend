package com.gerai.chat.controller;

import com.gerai.chat.dto.UserDTO;
import com.gerai.chat.service.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class UserController {

    private final KeycloakAdminService keycloakAdminService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getUsers() {
        return ResponseEntity.ok(keycloakAdminService.getAllUsers());
    }
}