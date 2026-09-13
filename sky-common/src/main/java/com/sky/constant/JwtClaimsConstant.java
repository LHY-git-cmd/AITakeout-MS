package com.sky.constant;

/**
 * JWT令牌声明常量类
 * 定义JWT令牌中使用的声明名称常量
 */
public class JwtClaimsConstant {

    /**
     * 员工ID声明键
     */
    public static final String EMP_ID = "empId";

    /**
     * 用户ID声明键
     */
    public static final String USER_ID = "userId";

    /**
     * 手机号声明键
     */
    public static final String PHONE = "phone";

    /**
     * 用户名声明键
     */
    public static final String USERNAME = "username";

    /**
     * 姓名声明键
     */
    public static final String NAME = "name";

    /** 管理端角色快照；最终授权仍以数据库为准。 */
    public static final String ADMIN_ROLE = "adminRole";

}
