package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.BlogVO;
import com.hmdp.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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
        //向数据库当中保存完博文之后，需要查出当前用户的所有粉丝id
        List<Long> fansIdList = userMapper.getFansById(userId);
        //将当前发布的探店笔记id转换为String类型
        String blogId = String.valueOf(blog.getId());
        //将所有探店笔记的id和创建时间存储到指定粉丝为id的Redis里面
        for (Long fansId : fansIdList) {
            stringRedisTemplate.opsForZSet().add(RedisConstants.FANS_USER_KEY + fansId, blogId, System.currentTimeMillis());
        }
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

    /*
    * 分页查询指定用户发布的笔记（他人主页）
    * */
    @Override
    public Result queryBlogOfUser(Long id, Integer current) {
        //按发布时间倒序分页查询该用户的笔记
        Page<Blog> page = query()
                .eq("user_id", id)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当前页数据
        List<Blog> records = page.getRecords();
        if(records.isEmpty()){
            //该用户还没发过笔记，直接返回空集合
            return Result.success(records);
        }
        //回填博主昵称和头像，供前端展示
        User user = userMapper.selectById(id);
        records.forEach(blog -> {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });
        return Result.success(records);
    }


    /*
    * 使用滚动分页查询，查看所有关注的博主的新博文
    * */
    @Override
    public Result queryFollowerBlog(Long lastTimeStamp, Integer offset) {
        //获取当前用户的id
        Long userId = UserHolder.getUser().getId();
        //查询当前用户所有关注的博主推送的博文
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(RedisConstants.FANS_USER_KEY + userId, 0, lastTimeStamp, offset, 3);
        //判断取出来的集合是否为空
        if(typedTuples == null||typedTuples.isEmpty()){
            //没有更多了，返回空list并重置游标，保证前端{list,minTime,offset}的解构不报错
            ScrollResult emptyResult = new ScrollResult();
            emptyResult.setList(Collections.emptyList());
            emptyResult.setMinTime(System.currentTimeMillis());
            emptyResult.setOffset(0);
            return Result.success(emptyResult);
        }
        //将当前集合内的博文id取出来，并计算下一次的offset
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tt : typedTuples) {
            //取出博文id
            blogIds.add(Long.valueOf(tt.getValue()));
            //取出博文的时间戳（getScore是Double，直接longValue，转String会变科学计数法导致解析失败）
            long time = tt.getScore().longValue();
            if(time == minTime){
                //相等则将nextOffset+1
                nextOffset++;
            }else{
                //不相等则更新minTime，并将nextOffset置为1
                minTime = time;
                nextOffset = 1;
            }
        }
        //根据博文id来查询全部的博文，SQL里order by field保证顺序和收件箱一致
        String blogIdsStr = StrUtil.join(",",blogIds);
        List<Blog> blogs = blogMapper.queryBolgsByIds(blogIds,blogIdsStr);
        //逐篇组装BlogVO：回填博主昵称头像、当前用户是否点赞
        List<BlogVO> blogVOList = new ArrayList<>(blogs.size());
        for (Blog blog : blogs) {
            BlogVO blogVO = new BlogVO();
            BeanUtils.copyProperties(blog, blogVO);
            //查询blog有关的用户，回填昵称和头像
            User user = userMapper.selectById(blog.getUserId());
            blogVO.setName(user.getNickName());
            blogVO.setIcon(user.getIcon());
            //查询blog是否被当前用户点赞：点赞ZSet中存在该用户即已点赞
            Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), userId.toString());
            blogVO.setIsLike(score != null);
            blogVOList.add(blogVO);
        }
        //封装滚动结果：list + 下一次查询的游标(minTime,nextOffset)
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogVOList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(nextOffset);
        return Result.success(scrollResult);
    }
}
