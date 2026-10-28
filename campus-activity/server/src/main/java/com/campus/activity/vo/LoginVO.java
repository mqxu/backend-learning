package com.campus.activity.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;
    private boolean needBind;
    private UserVO user;

    public LoginVO(String token, boolean needBind, UserVO user) {
        this.token = token;
        this.needBind = needBind;
        this.user = user;
    }
}
