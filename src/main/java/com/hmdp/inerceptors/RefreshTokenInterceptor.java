package com.hmdp.inerceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.constants.RedisConstants;
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
 * @date 2026/11/10 19:11
 * @一边写一抽方法，别都往main里面塞
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    //拦截器是new出来的，不受spring管理，所以redis模板要由MvcConfig传进来
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头里的token
        String token = request.getHeader("Authorization");
        //2.如果token不存在，放行
        if(StrUtil.isBlank(token)){
            return true;
        }
        //3.按照token从Redis里面取用户数据
        Map<Object, Object> loginUserMap = stringRedisTemplate.opsForHash().entries(token);
        //4.判断用户是否存在
        if(loginUserMap.isEmpty()){
            return true;
        }
        //5.存在，刷新token过期时间
        stringRedisTemplate.expire(token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //6.将用户信息存放到ThreadLocal
        UserVO userVO = BeanUtil.fillBeanWithMap(loginUserMap, new UserVO(), false);
        UserHolder.saveUser(userVO);
        //7.放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}