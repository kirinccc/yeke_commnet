package com.hmdp.inerceptors;

import com.hmdp.utils.UserHolder;
import com.hmdp.vo.UserVO;
import org.apache.catalina.Session;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author Kirin_Chen
 * @date 2026/11/10 16:00
 * @一边写一抽方法，别都往main里面塞
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取session
        HttpSession loginSession = request.getSession();
        //2.校验session是否存在
        if(loginSession.getAttribute("user") == null){
            //3.不存在返回 401，前端据此跳转登录页
            response.setStatus(401);
            return false;
        }
        //4.存在放行
        UserVO loginUser = (UserVO) loginSession.getAttribute("user");
        UserHolder.saveUser(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}