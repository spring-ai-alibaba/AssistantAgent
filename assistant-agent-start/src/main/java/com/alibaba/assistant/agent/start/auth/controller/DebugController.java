/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.assistant.agent.start.auth.controller;

import com.alibaba.assistant.agent.start.auth.dto.ApiResponse;
import com.alibaba.assistant.agent.start.auth.entity.User;
import com.alibaba.assistant.agent.start.auth.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试控制器 - 用于测试
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);

    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 检查用户是否存在
     */
    @GetMapping("/check-user/{username}")
    public ApiResponse<Map<String, Object>> checkUser(@PathVariable String username) {
        try {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            User user = userMapper.selectOne(queryWrapper);

            Map<String, Object> result = new HashMap<>();
            if (user == null) {
                result.put("exists", false);
                result.put("message", "用户不存在");
            } else {
                result.put("exists", true);
                result.put("username", user.getUsername());
                result.put("email", user.getEmail());
                result.put("roles", user.getRoles());
                result.put("permissions", user.getPermissions());
                result.put("enabled", user.getEnabled());
                result.put("passwordLength", user.getPassword() != null ? user.getPassword().length() : 0);
                result.put("passwordPrefix", user.getPassword() != null ? user.getPassword().substring(0, 10) + "..." : null);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("检查用户失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 验证密码
     */
    @PostMapping("/verify-password")
    public ApiResponse<Map<String, Object>> verifyPassword(@RequestParam String username, @RequestParam String password) {
        try {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            User user = userMapper.selectOne(queryWrapper);

            Map<String, Object> result = new HashMap<>();
            if (user == null) {
                result.put("valid", false);
                result.put("message", "用户不存在");
                return ApiResponse.success(result);
            }

            boolean matches = passwordEncoder.matches(password, user.getPassword());
            result.put("valid", matches);
            result.put("message", matches ? "密码正确" : "密码错误");
            result.put("username", user.getUsername());

            // 测试加密
            String encodedPassword = passwordEncoder.encode(password);
            result.put("encodedPassword", encodedPassword);
            result.put("encodedPasswordLength", encodedPassword.length());

            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("验证密码失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建或重置管理员账户
     */
    @PostMapping("/reset-admin")
    public ApiResponse<Map<String, Object>> resetAdmin() {
        try {
            // 删除旧的管理员账户
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", "admin");
            User existingUser = userMapper.selectOne(queryWrapper);

            if (existingUser != null) {
                userMapper.deleteById(existingUser.getId());
                logger.info("已删除旧的管理员账户");
            }

            // 创建新的管理员账户
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setEmail("admin@example.com");
            admin.setRoles("ADMIN,USER");
            admin.setPermissions("read,write,delete,execute");
            admin.setEnabled(true);

            userMapper.insert(admin);

            Map<String, Object> result = new HashMap<>();
            result.put("username", "admin");
            result.put("password", "admin");
            result.put("message", "管理员账户已重置");

            logger.info("管理员账户已重置: username=admin, password=admin");
            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("重置管理员账户失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 测试密码加密
     */
    @PostMapping("/test-encrypt")
    public ApiResponse<Map<String, Object>> testEncrypt(@RequestParam String password) {
        try {
            String encoded = passwordEncoder.encode(password);
            boolean matches = passwordEncoder.matches(password, encoded);

            Map<String, Object> result = new HashMap<>();
            result.put("original", password);
            result.put("encoded", encoded);
            result.put("matches", matches);
            result.put("encodedLength", encoded.length());

            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("测试加密失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
