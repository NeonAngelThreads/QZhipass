package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.request.CreateAccountRequest;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.utils.PasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/account")
public class AccountController {

    @Autowired
    private UserService userService;

    /**
     * 创建账户
     * POST /api/v1/account/create
     *
     * 验证流程：
     * 1. 必填字段校验
     * 2. 密码规格校验（大小写字母+特殊字符+数字，长度>=8）
     * 3. 密码二次验证
     * 4. 手机号唯一性校验（已存在NORMAL/FROZEN → "该用户已创建"）
     * 5. 已注销用户二次注册（部门相同则保留历史数据）
     * 6. 创建成功 → "创建用户成功"
     */
    @PostMapping("/create")
    public ResponseEntity<?> createAccount(@RequestBody CreateAccountRequest request) {
        try {
            // 1. 必填字段校验
            String validationError = validateRequiredFields(request);
            if (validationError != null) {
                return ResponseEntity.badRequest()
                        .body(new ResponseBody(false, validationError));
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

            // 4. 创建账户（内部处理手机号唯一性+已注销恢复逻辑）
            User user = userService.createAccount(
                    request.getName(),
                    request.getDepartment(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getWechat(),
                    request.getPassword()
            );

            // 5. 构建响应
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", user.getId());
            data.put("name", user.getName());
            data.put("phone", user.getPhone());
            data.put("department", user.getDepartment());
            data.put("status", user.getStatus().name());
            data.put("restored", user.getRestored());

            String message = user.getRestored()
                    ? "二次注册成功，历史数据已恢复"
                    : "创建用户成功";

            data.put("message", message);
            log.info("账户创建成功: phone={}, name={}, restored={}",
                    user.getPhone(), user.getName(), user.getRestored());

            return ResponseEntity.ok(data);

        } catch (IllegalArgumentException e) {
            // 业务异常：如"该用户已创建"
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, e.getMessage()));
        } catch (Exception e) {
            log.error("创建账户失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ResponseBody(false, "创建账户失败: " + e.getMessage()));
        }
    }

    /**
     * 手机号+密码登录
     * POST /api/v1/account/login
     *
     * 验证：注销/冻结状态用户无法登录
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");

        if (phone == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, "手机号或密码不能为空"));
        }

        try {
            User user = userService.loginByPassword(phone, password);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(new ResponseBody(false, "手机号或密码错误"));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", user.getId());
            data.put("name", user.getName());
            data.put("phone", user.getPhone());
            data.put("department", user.getDepartment());
            data.put("status", user.getStatus().name());
            data.put("message", "登录成功");

            return ResponseEntity.ok(data);

        } catch (IllegalStateException e) {
            // 账户已注销或已冻结
            return ResponseEntity.status(403)
                    .body(new ResponseBody(false, e.getMessage()));
        } catch (Exception e) {
            log.error("登录失败", e);
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, "登录失败: " + e.getMessage()));
        }
    }

    /**
     * 校验用户状态
     * GET /api/v1/account/status?phone=xxx
     *
     * 返回用户状态：NORMAL(正常可用) / FROZEN(冻结不可用) / CANCELLED(注销不可用)
     */
    @GetMapping("/status")
    public ResponseEntity<?> checkStatus(@RequestParam String phone) {
        try {
            boolean active = userService.isUserActive(phone);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phone", phone);
            data.put("canUse", active);
            data.put("message", active ? "正常状态，可使用功能" : "账户不可用");
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 管理员注销账户
     * PUT /api/v1/account/cancel?phone=xxx
     */
    @PutMapping("/cancel")
    public ResponseEntity<?> cancelAccount(@RequestParam String phone) {
        try {
            userService.cancelAccount(phone);
            return ResponseEntity.ok(new ResponseBody(true, "账户已注销"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, e.getMessage()));
        }
    }

    /**
     * 管理员冻结账户
     * PUT /api/v1/account/freeze?phone=xxx
     */
    @PutMapping("/freeze")
    public ResponseEntity<?> freezeAccount(@RequestParam String phone) {
        try {
            userService.freezeAccount(phone);
            return ResponseEntity.ok(new ResponseBody(true, "账户已冻结"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ResponseBody(false, e.getMessage()));
        }
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
