package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.PasswordDTO;
import com.example.platform.dto.ProfileDTO;
import com.example.platform.dto.RegisterDTO;
import com.example.platform.entity.User;
import com.example.platform.mapper.UserMapper;
import com.example.platform.security.JwtUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public User register(RegisterDTO dto) {
        if (userMapper.countByUsername(dto.getUsername()) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        if (userMapper.countByEmail(dto.getEmail()) > 0) {
            throw new BusinessException(400, "邮箱已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank() ? dto.getUsername() : dto.getNickname());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return userMapper.findById(user.getId());
    }

    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.findByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generateToken(user));
        data.put("user", user);
        return data;
    }

    public User findById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    public List<User> findAll() {
        return userMapper.findAll();
    }

    public User updateProfile(Long userId, ProfileDTO dto) {
        User user = findById(userId);
        user.setEmail(dto.getEmail() == null ? user.getEmail() : dto.getEmail());
        user.setNickname(dto.getNickname() == null ? user.getNickname() : dto.getNickname());
        user.setAvatar(dto.getAvatar() == null ? user.getAvatar() : dto.getAvatar());
        userMapper.updateProfile(user);
        return findById(userId);
    }

    public void updatePassword(Long userId, PasswordDTO dto) {
        User user = findById(userId);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(dto.getNewPassword()));
    }

    public void updateStatus(Long userId, Integer status) {
        findById(userId);
        userMapper.updateStatus(userId, status);
    }

    public void delete(Long id) {
        userMapper.delete(id);
    }

    public void ensureSeedUser(String username, String password, String email, String role) {
        if (userMapper.countByUsername(username) > 0) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setNickname(username);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
    }
}
