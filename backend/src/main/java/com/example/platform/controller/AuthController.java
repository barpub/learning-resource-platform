package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.RegisterDTO;
import com.example.platform.entity.User;
import com.example.platform.service.UserService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success("退出成功");
    }
}
