package com.pet.platform;

import com.pet.platform.common.UserContext;
import com.pet.platform.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 双端 token 隔离用例。
 *
 * <p>两个拦截器分别只认 type=admin / type=user 的 token，
 * 所以「拿小程序 token 调管理端接口」必须被拒。
 * 这类越权在只验签不验 type 的实现里是真实漏洞，用测试把它钉死。
 */
@AutoConfigureMockMvc
class DualEndAuthTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken() {
        return jwtUtil.createToken(1L, UserContext.ADMIN);
    }

    private String userToken() {
        return jwtUtil.createToken(1L, UserContext.USER);
    }

    @Test
    @DisplayName("小程序 token 调管理端接口 → 401，不能靠改请求头越权")
    void userTokenCannotCallAdminApi() throws Exception {
        mockMvc.perform(get("/admin/appointment/page").header("token", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("管理端 token 调小程序接口 → 401，隔离是双向的")
    void adminTokenCannotCallUserApi() throws Exception {
        mockMvc.perform(get("/user/pet/list").header("authentication", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("不带 token → 401；带对的 token → code=1")
    void missingTokenIsRejectedAndValidTokenPasses() throws Exception {
        mockMvc.perform(get("/admin/appointment/page"))
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/admin/appointment/page").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(1));

        mockMvc.perform(get("/user/pet/list").header("authentication", userToken()))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("Authorization: Bearer 头同样被管理端接受")
    void bearerHeaderIsAlsoAccepted() throws Exception {
        mockMvc.perform(get("/admin/appointment/page").header("Authorization", "Bearer " + adminToken()))
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("伪造签名 / 乱写的 token → 401")
    void tamperedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/admin/appointment/page").header("token", adminToken() + "x"))
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/admin/appointment/page").header("token", "not-a-jwt"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
