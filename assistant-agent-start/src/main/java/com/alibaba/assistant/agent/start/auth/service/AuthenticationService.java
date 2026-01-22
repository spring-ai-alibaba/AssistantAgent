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

package com.alibaba.assistant.agent.start.auth.service;

import com.alibaba.assistant.agent.start.auth.dto.LoginRequest;
import com.alibaba.assistant.agent.start.auth.dto.LoginResponse;
import com.alibaba.assistant.agent.start.auth.entity.User;
import com.alibaba.assistant.agent.start.auth.mapper.UserMapper;
import com.alibaba.assistant.agent.start.auth.security.JwtTokenProvider;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 认证服务
 */
@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        logger.info("AuthenticationService#login - 用户登录请求: username={}", request.getUsername());

        // 查询用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", request.getUsername());
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            logger.warn("AuthenticationService#login - 用户不存在: username={}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("AuthenticationService#login - 密码错误: username={}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // 检查用户是否启用
        if (!user.getEnabled()) {
            logger.warn("AuthenticationService#login - 用户已被禁用: username={}", request.getUsername());
            throw new RuntimeException("用户已被禁用");
        }

        // 解析角色和权限
        List<String> roles = parseList(user.getRoles());
        List<String> permissions = parseList(user.getPermissions());

        // 生成 Token
        String token = jwtTokenProvider.generateToken(user.getUsername(), roles, permissions);

        logger.info("AuthenticationService#login - 用户登录成功: username={}", request.getUsername());

        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(jwtTokenProvider.getExpiration());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                roles,
                permissions
        );
        response.setUser(userInfo);

        return response;
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return jwtTokenProvider.getUsernameFromToken(token);
    }

    /**
     * 初始化默认管理员账户
     */
    public void initDefaultAdmin() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", "admin");
        User existingUser = userMapper.selectOne(queryWrapper);

        if (existingUser == null) {
            logger.info("AuthenticationService#initDefaultAdmin - 创建默认管理员账户");

            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setEmail("admin@example.com");
            admin.setRoles("ADMIN,USER");
            admin.setPermissions("read,write,delete,execute");
            admin.setEnabled(true);

            userMapper.insert(admin);
            logger.info("AuthenticationService#initDefaultAdmin - 默认管理员账户创建成功: username=admin, password=admin");
        } else {
            logger.debug("AuthenticationService#initDefaultAdmin - 管理员账户已存在");
        }
    }

    /**
     * 解析逗号分隔的字符串为列表
     */
    private List<String> parseList(String str) {
        if (str == null || str.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.asList(str.split(","));
    }
}
