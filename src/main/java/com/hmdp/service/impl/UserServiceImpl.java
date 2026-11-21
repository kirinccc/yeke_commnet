package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.constants.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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

    /*
    * 根据id查询用户基本信息（他人主页）
    * */
    @Override
    public Result<UserVO> queryUserById(Long userId) {
        //根据id查询用户
        User user = userMapper.selectById(userId);
        if(user == null){
            log.error("用户不存在");
            return Result.error("用户不存在");
        }
        //脱敏：只返回id、昵称、头像给前端
        UserVO userVO = new UserVO(user.getId(), user.getNickName(), user.getIcon());
        return Result.success(userVO);
    }


    /*
    * 使用bitmap实现签到功能
    * */
    @Override
    public Result checkIn() {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //格式化获取当前时间的年月
        String yearAndMonth = now.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        //获取当前时间的日
        int dayOfMonth = now.getDayOfMonth();
        //拼接用户id和年月得到bitmap的key
        String key = RedisConstants.USER_CHECK_IN_KEY + userId + yearAndMonth;
        //使用bitmap的setbit方法设置当前日期为1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1, true);
        return Result.success();
    }


    /*
    * 判断当天签到状态
    * */
    @Override
    public Result checkInStatus() {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //格式化获取当前时间的年月
        String yearAndMonth = now.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        //获取当前时间的日
        int dayOfMonth = now.getDayOfMonth();
        //拼接用户id和年月得到bitmap的key
        String key = RedisConstants.USER_CHECK_IN_KEY + userId + yearAndMonth;
        //使用bitmap的getbit方法获取当前日期是否为1
        Boolean isCheckIn = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth-1);
        //如果当前日期为1，则返回true，否则返回false
        if(isCheckIn){
            return Result.success(true);
        }
        return Result.success(false);
    }

    /*
    * 统计当月截止当天的连续签到天数
    * */
    @Override
    public Result checkInCount() {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //格式化获取当前时间的年月
        String yearAndMonth = now.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        //获取当前时间的日
        int dayOfMonth = now.getDayOfMonth();
        //拼接用户id和年月得到bitmap的key
        String key = RedisConstants.USER_CHECK_IN_KEY + userId + yearAndMonth;
        //使用bitmap的bitField方法获取当前日期到1的连续天数
        List<Long> result = stringRedisTemplate
                .opsForValue().bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        //对结果集合判空
        if(result==null||result.isEmpty()){
            return Result.success(0);
        }
        //结果集合中的第一个元素判空
        Long days = result.get(0);
        if(days==null||days==0){
            return Result.success(0);
        }
        //对取出来的结果对1做与运算，判断是否为1
        int count =0;
        while (true){
            if((days & 1)==1){
                //为1则将count加1
                count++;
                //将days右移一位
                days = days >> 1;
            }else {
                //不为1，说明断签了，跳出循环
                break;
            }
        }
        return Result.success(count);
    }

}
