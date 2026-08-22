package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class WebUserLoginDTO implements Serializable {

    private String phone;
    private String code;
}
