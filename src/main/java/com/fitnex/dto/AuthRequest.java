package com.fitnex.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fitnex.entity.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    /**
     * 登录标识，可为用户名/邮箱/手机号
     */
    @NotBlank(message = "登录标识不能为空")
    @JsonAlias({"username", "email", "phone"})
    private String identifier;

    /**
     * 密码（第三方授权时可为空）
     */
    private String password;

    /**
     * 登录类型，默认 EMAIL 兼容旧逻辑
     */
    private User.LoginType loginType = User.LoginType.EMAIL;

    /**
     * 第三方授权标识
     */
    private String thirdPartyId;
    private String thirdPartyProvider;
}

