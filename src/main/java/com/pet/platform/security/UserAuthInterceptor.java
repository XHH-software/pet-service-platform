package com.pet.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.platform.common.Result;
import com.pet.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 小程序端拦截器：请求头字段为 authentication。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("authentication");
        if (token == null || token.isEmpty()) {
            token = request.getHeader("token");
        }
        if (token == null || token.isEmpty()) {
            return reject(response, "请先登录");
        }
        try {
            UserContext.LoginUser loginUser = jwtUtil.parse(token);
            if (!UserContext.USER.equals(loginUser.getType())) {
                return reject(response, "登录状态无效");
            }
            if (tokenService.isBlacklisted(token)) {
                return reject(response, "登录已过期，请重新登录");
            }
            UserContext.set(loginUser.getId(), UserContext.USER);
            return true;
        } catch (Exception e) {
            log.debug("小程序端 token 校验失败: {}", e.getMessage());
            return reject(response, "登录已过期，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.unauthorized(msg)));
        return false;
    }
}
