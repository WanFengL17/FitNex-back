package com.fitnex.dto;

import com.fitnex.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少6个字符")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;
    private String nickname;
    private User.LoginType loginType = User.LoginType.EMAIL;
    /**
     * 第三方注册信息（可选）
     */
    private String thirdPartyId;
    private String thirdPartyProvider;
}

