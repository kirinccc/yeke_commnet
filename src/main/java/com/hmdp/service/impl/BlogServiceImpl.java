package com.hmdp.service.impl;

import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.BlogVO;
import com.hmdp.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private BlogMapper blogMapper;
    @Autowired
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 用户发布探店笔记
     * @param blog 探店笔记数据
     * @return 探店笔记id
     */
    @Override
    public Result saveBlog(Blog blog) {
        //获取当前发布动态的用户id
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        blogMapper.insert(blog);
        return Result.success(blog.getId());
    }

    /**
     * 根据id查询探店笔记
     * @param id 探店笔记id
     * @return 探店笔记数据
     */

    @Override
    public Result<BlogVO> getBlogById(Long id) {
        //从数据库中查询探店笔记
        Blog blog = blogMapper.selectById(id);
        if (blog == null){
            log.error("探店笔记不存在");
            return Result.error("探店笔记不存在");
        }
        //查询当前笔记发布用户的id，拼装到BlogVO中
        Long userId = blog.getUserId();
        User user = userMapper.selectById(userId);
        BlogVO blogVO = new BlogVO();
        BeanUtils.copyProperties(blog, blogVO);
        blogVO.setIcon(user.getIcon());
        blogVO.setName(user.getNickName());
        //回填当前用户是否已点赞：ZSet中存在该用户即已点赞
        Long currentUserId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, currentUserId.toString());
        blogVO.setIsLike(score != null);
        return Result.success(blogVO);
    }


    /*
    * 点赞
    * */
    @Override
    public Result likeBlog(Long id) {
        //先获取点赞的用户的id
        Long userId = UserHolder.getUser().getId();
        //查询当前用户的id在该帖子的集合里面是否存在
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
        //判断取出来的分数是否存在
        if(score != null){
            //存在则将数据库的点赞数-1
            blogMapper.subLike(id);
            //将当前用户的id从集合中删除
            stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
            return Result.success();
        }
        //不存在则将数据库的点赞数+1
        blogMapper.addLike(id);
        //点赞完成之后还需要将当前用户以及当前点赞时间戳按照分数从大到小存储到集合中
        stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
        return Result.success();
    }


    /*
    * 获取点赞排行榜
    * */
    @Override
    public Result blogLikeTop(Long id) {
        //获取Redis中存储的点赞数据
        Set<String> likedSet = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        //判断取出来的集合是否为空
        if(likedSet == null||likedSet.isEmpty()){
            //为空则返回空集合给前端
            return Result.success(Collections.emptyList());
        }
        //不为空则需要将集合里面id的数据取出来
        List<Long> userIdList = likedSet.stream().map(Long::valueOf).collect(Collectors.toList());
        //将用户id组成字符串，likedSet本身就是有序的String集合，直接join即可
        String userIds = String.join(",", likedSet);
        //根据用户id查询用户数据，按点赞顺序返回
        List<User> users = userMapper.findUsersByIds(userIds, userIdList);
        //脱敏：只返回id、昵称、头像给前端
        List<UserVO> userVOList = users.stream()
                .map(u -> new UserVO(u.getId(), u.getNickName(), u.getIcon()))
                .collect(Collectors.toList());
        return Result.success(userVOList);
    }
}
