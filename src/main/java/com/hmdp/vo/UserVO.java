package com.hmdp.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Kirin_Chen
 * @date 2026/11/10 15:49
 * @一边写一抽方法，别都往main里面塞
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {
    private Long id;
    private String nickName;
    private String icon;
}