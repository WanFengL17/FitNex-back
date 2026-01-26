package com.fitnex.dto;

import com.fitnex.entity.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ThirdPartyAuthRequest {
    @NotBlank(message = "第三方ID不能为空")
    private String thirdPartyId;

    @NotBlank(message = "第三方平台不能为空")
    private String thirdPartyProvider;

    private String email;
    private String phone;
    private String nickname;

    private User.LoginType loginType = User.LoginType.WECHAT;
}









