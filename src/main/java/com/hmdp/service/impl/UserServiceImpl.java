package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private StringRedisTemplate stringRedisTemplate;
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
        //把手机号和验证码存入到redis，保证一码和一手机号对应
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
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
        //3. 从redis取出该手机号对应的验证码（一个手机号对应一个码）
        String codeKey = RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone();
        String cacheCode = stringRedisTemplate.opsForValue().get(codeKey);
        //4. 校验验证码和手机号是否一致
        if(cacheCode == null || !cacheCode.equals(loginForm.getCode())){
            //5. 验证码不一致返回验证码错误
            return Result.error("验证码不正确");
        }
        //6. 全部一致根据手机号查询用户表
        User user = userMapper.findByphone(loginForm.getPhone());
        //7. 校验用户是否存在
        if(user == null){
            //8. 用户不存在创建用户，存入数据库
            user = createUser(loginForm);
            userMapper.insert(user);
        }
        //9. 用户存在,放入redis,将实体类转成vo类再存入redis可以避免敏感数据泄露
        UserVO loginUserVO = new UserVO();
        BeanUtils.copyProperties(user,loginUserVO);
        //9.1 生成token，作为redis中的key，同时返回给前端保存
        String token = UUID.randomUUID().toString().replace("-", "");
        //9.2 StringRedisTemplate只能写String，所以把字段值统一转成字符串
        Map<String, Object> userMap = new HashMap<>(4);
        BeanUtil.beanToMap(loginUserVO, userMap,
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        //9.3 用hash结构存用户信息：key=login:token:{token}，field=属性名
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //9.4 设置有效期，用户每次访问时由拦截器续期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        //10.注册完成之后就要删除验证码，保证一码只能用一次
        stringRedisTemplate.delete(codeKey);
        //11.把token返回给前端，前端保存后每次请求放在请求头 authorization 中
        return Result.success(token);
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
