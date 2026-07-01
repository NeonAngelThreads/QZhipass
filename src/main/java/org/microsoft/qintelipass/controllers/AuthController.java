package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.request.CreateAccountRequest;
import org.microsoft.qintelipass.request.LoginRequest;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.TokenService;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.utils.PasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/portal")
public class AuthController {

    @Autowired
    private LoginStrategyFactory factory;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    /**
     * 登录（支持多种登录策略）
     * POST /api/v1/portal/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest formData) {
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.getParams();
        ILoginStrategy strategy = factory.getStrategy(loginType);
        log.info("User response: {}", formData);
        ResponseBody response = strategy.authenticate(params);
        log.info("Authenticator response: {}", response);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }  

    /**
     * 注册账户
     * POST /api/v1/portal/register
     *
     * 请求体：CreateAccountRequest（姓名、部门、邮箱、手机、微信、密码、确认密码）
     * 返回值：access_token + user 实体（不含密码）
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CreateAccountRequest request) {
        try {
            // 1. 必填字段校验
            String fieldError = validateRequiredFields(request);
            if (fieldError != null) {
                return ResponseEntity.badRequest()
                        .body(new ResponseBody(false, fieldError));
            }

            // 2. 密码规格校验
            String passwordError = PasswordValidator.validate(request.getPassword());
            if (passwordError != null) {
                return ResponseEntity.badRequest()
                        .body(new ResponseBody(false, passwordError));
            }

            // 3. 密码二次验证
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest()
                        .body(new ResponseBody(false, "两次输入的密码不一致，请重新输入"));
            }

            // 4. 创建账户
            User user = userService.createAccount(
                    request.getName(),
                    request.getDepartment(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getWechat(),
                    request.getPassword()
            );

            // 5. 生成 access_token
            String accessToken = tokenService.generateToken(user.getId());

            // 6. 构建响应（不返回密码）
            Map<String, Object> userData = buildUserData(user);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("access_token", accessToken);
            data.put("user", userData);
            data.put("message", user.getRestored()
                    ? "二次注册成功，历史数据已恢复"
                    : "创建用户成功");

            log.info("注册成功: phone={}, restored={}", user.getPhone(), user.getRestored());
            return ResponseEntity.ok(data);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, e.getMessage()));
        } catch (Exception e) {
            log.error("注册失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ResponseBody(false, "注册失败: " + e.getMessage()));
        }
    }

    /**
     * 构建安全的用户数据（不含密码）
     */
    private Map<String, Object> buildUserData(User user) {
        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("phone", user.getPhone());
        userData.put("department", user.getDepartment());
        userData.put("email", user.getEmail());
        userData.put("wechat", user.getWechat());
        userData.put("status", user.getStatus().name());
        userData.put("createdAt", user.getCreatedAt());
        userData.put("restored", user.getRestored());
        return userData;
    }

    /**
     * 校验必填字段
     */
    private String validateRequiredFields(CreateAccountRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return "姓名不能为空";
        }
        if (request.getDepartment() == null || request.getDepartment().trim().isEmpty()) {
            return "所在部门不能为空";
        }
        if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            return "手机号码不能为空";
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return "密码不能为空";
        }
        if (request.getConfirmPassword() == null || request.getConfirmPassword().isEmpty()) {
            return "确认密码不能为空";
        }
        return null;
    }
}
