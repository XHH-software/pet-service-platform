package com.pet.platform.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.UserContext;
import com.pet.platform.config.WxProperties;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.mapper.StaffMapper;
import com.pet.platform.security.JwtUtil;
import com.pet.platform.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffMapper staffMapper;
    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final WxProperties wxProperties;
    private final ObjectMapper objectMapper;

    public Map<String, Object> adminLogin(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException("请输入账号和密码");
        }
        Staff staff = staffMapper.selectOne(Wrappers.<Staff>lambdaQuery()
                .and(w -> w.eq(Staff::getUsername, username).or().eq(Staff::getPhone, username))
                .last("limit 1"));
        if (staff == null || !passwordEncoder.matches(password, staff.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (staff.getStatus() == null || staff.getStatus() != 1) {
            throw new BusinessException("账号已停用，请联系店长");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", staff.getId());
        result.put("name", staff.getName());
        result.put("token", jwtUtil.createToken(staff.getId(), UserContext.ADMIN));
        return result;
    }

    public void adminLogout(String token) {
        if (StringUtils.hasText(token)) {
            tokenService.blacklist(token);
        }
    }

    public void changePassword(Long staffId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException("请输入原密码和新密码");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException("新密码长度为 6-20 位");
        }
        Staff staff = staffMapper.selectById(staffId);
        if (staff == null) {
            throw new BusinessException("员工不存在");
        }
        if (!passwordEncoder.matches(oldPassword, staff.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        Staff update = new Staff();
        update.setId(staffId);
        update.setPassword(passwordEncoder.encode(newPassword));
        staffMapper.updateById(update);
    }

    public Map<String, Object> userLogin(String code) {
        String openid = resolveOpenid(code);
        Customer customer = customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getOpenid, openid).last("limit 1"));
        if (customer == null) {
            customer = new Customer();
            customer.setOpenid(openid);
            customer.setNickname("微信用户" + openid.substring(Math.max(0, openid.length() - 4)));
            customer.setAvatar("");
            customerMapper.insert(customer);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", customer.getId());
        result.put("nickname", customer.getNickname());
        result.put("avatar", customer.getAvatar());
        result.put("phone", customer.getPhone());
        result.put("token", jwtUtil.createToken(customer.getId(), UserContext.USER));
        return result;
    }

    public Customer userInfo(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("用户不存在");
        }
        return customer;
    }

    @AutoFill(AutoFill.Type.UPDATE)
    public void updateUserInfo(Long customerId, Customer input) {
        Customer update = new Customer();
        update.setId(customerId);
        update.setNickname(input.getNickname());
        update.setAvatar(input.getAvatar());
        update.setPhone(input.getPhone());
        customerMapper.updateById(update);
    }

    /**
     * 开发环境走 mock：固定 openid，便于 H5 / 微信开发者工具复用同一个演示账号。
     * 生产环境配置 app.wx.mock=false 并填写 appId/secret。
     */
    private String resolveOpenid(String code) {
        if (wxProperties.isMock()) {
            return "mock_openid_dev";
        }
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("微信登录失败：缺少 code");
        }
        if (!StringUtils.hasText(wxProperties.getAppId()) || !StringUtils.hasText(wxProperties.getAppSecret())) {
            throw new BusinessException("微信登录未配置，请设置 app.wx.app-id / app-secret");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wxProperties.getAppId()
                + "&secret=" + wxProperties.getAppSecret()
                + "&js_code=" + code + "&grant_type=authorization_code";
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(5000);
            String body = new RestTemplate(factory).getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body);
            if (!node.hasNonNull("openid")) {
                throw new BusinessException("微信登录失败：" + node.path("errmsg").asText("未知错误"));
            }
            return node.get("openid").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 code2Session 失败", e);
            throw new BusinessException("微信登录失败，请稍后重试");
        }
    }
}
