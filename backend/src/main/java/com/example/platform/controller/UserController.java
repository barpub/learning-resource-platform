package com.example.platform.controller;

import com.example.platform.common.BusinessException;
import com.example.platform.common.Result;
import com.example.platform.dto.PasswordDTO;
import com.example.platform.dto.ProfileDTO;
import com.example.platform.dto.UserDashboardDTO;
import com.example.platform.entity.User;
import com.example.platform.security.CurrentUser;
import com.example.platform.service.UserDashboardService;
import com.example.platform.service.UserService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserDashboardService userDashboardService;

    public UserController(UserService userService, UserDashboardService userDashboardService) {
        this.userService = userService;
        this.userDashboardService = userDashboardService;
    }

    @GetMapping("/profile")
    public Result<User> profile() {
        Long userId = requireUser();
        return Result.success(userService.findById(userId));
    }

    @GetMapping("/me/dashboard")
    public Result<UserDashboardDTO> dashboard() {
        return Result.success(userDashboardService.dashboard(requireUser()));
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@Valid @RequestBody ProfileDTO dto) {
        Long userId = requireUser();
        return Result.success(userService.updateProfile(userId, dto));
    }

    @PutMapping("/password")
    public Result<String> updatePassword(@Valid @RequestBody PasswordDTO dto) {
        userService.updatePassword(requireUser(), dto);
        return Result.success("密码修改成功");
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> list() {
        return Result.success(userService.findAll());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.success("状态已更新");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功");
    }

    private Long requireUser() {
        Long userId = CurrentUser.id();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
