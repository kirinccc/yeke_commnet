package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static org.springframework.beans.BeanUtils.copyProperties;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j

public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserMapper userMapper;
    @Override
    public Result<String> sendCode(String phone, HttpSession session) {
        String code ;
        if(RegexUtils.isPhoneInvalid(phone)){
            log.info("手机号格式不正确");
            return Result.error("手机号格式不正确");
        }
        code = RandomUtil.randomNumbers(6);
        //把手机号和验证码存入到session里面，保证一码和一手机号对应
        session.setAttribute("code",code);
        session.setAttribute("phone",phone);

        log.info("手机验证码：{}",code);
        return Result.success(code);
    }


    //登录校验
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号是否符合
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            //2. 手机号不符合返回手机号错误
            return Result.error("手机号格式不正确");
        }
        //3. 校验验证码和手机号是否是否一致
        if(session.getAttribute("code") == null || !session.getAttribute("code").equals(loginForm.getCode())){
            //4. 验证码不一致返回验证码错误
            return Result.error("验证码不正确");
        }
        if(session.getAttribute("phone") == null || !session.getAttribute("phone").equals(loginForm.getPhone())){
            //5.手机号不一致返回手机号错误
            return Result.error("手机号不正确");
        }
        //6. 全部一致根据手机号查询用户表
        User user = userMapper.findByphone(loginForm.getPhone());
        //7. 校验用户是否存在
        if(user == null){
            //8. 用户不存在创建用户，存入数据库
            user = createUser(loginForm);
            userMapper.insert(user);
        }
        //9. 用户存在,放入session,将实体类转成vo类再存入session可以避免敏感数据泄露
        session.setAttribute("user", BeanUtil.copyProperties(user, UserVO.class));
        //10.注册完成之后就要删除session里面的验证码
        session.removeAttribute("code");
        return Result.success();
    }
    //创建新的注册用户
    private User createUser(LoginFormDTO loginForm){
        User user = User.builder()
                .phone(loginForm.getPhone())
                .nickName("nic_"+loginForm.getPhone())
                .build();
        return user;
    }
}
