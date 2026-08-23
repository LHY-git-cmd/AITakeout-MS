package com.sky.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * 封装接口返回的数据结构，包含状态码、提示信息和数据
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    /**
     * 编码：1成功，0和其它数字为失败
     */
    private Integer code;

    /**
     * 错误信息
     */
    private String msg;

    /**
     * 数据
     */
    private T data;

    /**
     * 返回成功结果（无数据）
     *
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        return result;
    }

    /**
     * 返回成功结果（带数据）
     *
     * @param object 数据对象
     * @param <T>    数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 1;
        return result;
    }

    /**
     * 返回失败结果
     *
     * @param msg 错误信息
     * @param <T> 数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = 0;
        return result;
    }

}