package com.hmdp.inerceptors;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.UserVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Kirin_Chen
 * @date 2026/11/10 16:00
 * @一边写一抽方法，别都往main里面塞
 */
public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    //拦截器是new出来的，不受spring管理，所以redis模板要由MvcConfig传进来
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token（前端登录后保存，每次请求带上）
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            //2.没有token，返回 401，前端据此跳转登录页
            response.setStatus(401);
            return false;
        }
        //3.用token去redis中取用户信息（hash结构，field是属性名）
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userMap.isEmpty()) {
            //4.取不到说明token已过期或伪造，同样返回 401
            response.setStatus(401);
            return false;
        }
        //5.把hash转回UserVO，放进ThreadLocal供业务层使用
        UserVO loginUser = BeanUtil.fillBeanWithMap(userMap, new UserVO(), false);
        UserHolder.saveUser(loginUser);
        //6.有访问就给token续期，避免用户操作中途被踢下线
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        //7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
