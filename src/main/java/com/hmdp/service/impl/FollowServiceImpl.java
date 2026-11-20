package com.hmdp.service.impl;

import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.UserVO;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private UserMapper userMapper;
    /*
    * 获取关注状态
    * */
    @Override
    public Result isFollow(Long blogUserId) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_USER_KEY + userId;
        //查询redis，当前发帖人是否在当前用户的关注列表里面（Set里存的是字符串，Long必须转String）
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, String.valueOf(blogUserId));
        if(Boolean.TRUE.equals(isMember)){
            //在集合里，已关注
            return Result.success(true);
        }
        //不在集合里分两种情况：key存在说明真的没关注，false可信，直接返回
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))){
            return Result.success(false);
        }
        //key不存在，可能是缓存丢失，回源数据库查该用户的关注列表
        List<Follow> follows = query().eq("user_id", userId).list();
        if(follows.isEmpty()){
            //数据库里也没有，该用户本来就谁都没关注
            return Result.success(false);
        }
        //把关注列表整体重建进redis，下次查询直接命中缓存
        String[] followUserIds = follows.stream()
                .map(f -> String.valueOf(f.getFollowUserId()))
                .toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(key, followUserIds);
        //根据数据库数据判断关注状态
        boolean isFollow = follows.stream()
                .anyMatch(f -> f.getFollowUserId().equals(blogUserId));
        return Result.success(isFollow);
    }


    /*
    * 关注/取关当前发帖用户
    * */
    @Override
    public Result followOrNot(Long id, Boolean isFollow) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //根据前端传过来的取反值，来判断是否需要关注/取关
        if(isFollow){
            //需要关注当前用户
            followMapper.follow(userId,id);
            stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOW_USER_KEY + userId, String.valueOf(id));
        }else{
            //需要取关当前发帖用户
            followMapper.unfollow(userId,id);
            stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOW_USER_KEY + userId, String.valueOf(id));
        }
        return Result.success();
    }


    /*
    * 查询共同关注
    * */
    @Override
    public Result commonFollow(Long id) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //使用Redis的set求出两个用户之间的关注列表交集
        Set<String> commonFollowSet = stringRedisTemplate.opsForSet().intersect(RedisConstants.FOLLOW_USER_KEY + userId, RedisConstants.FOLLOW_USER_KEY + id);
        //判断是否为空
        if(commonFollowSet == null || commonFollowSet.isEmpty()){
            //为空返回空集
            return Result.success(Collections.emptyList());
        }
        //不为空首先需要把set转list
        List<Long> commonFollowList = commonFollowSet.stream().map(v -> Long.valueOf(v)).collect(Collectors.toList());
        //然后根据list查询用户数据
        List<User> users = userMapper.findUsersByIds(String.join(",", commonFollowSet), commonFollowList);
        //脱敏：只返回id、昵称、头像给前端
        List<UserVO> userVOList = users.stream().map(u -> new UserVO(u.getId(), u.getNickName(), u.getIcon())).collect(Collectors.toList());
        return Result.success(userVOList);
    }
}
