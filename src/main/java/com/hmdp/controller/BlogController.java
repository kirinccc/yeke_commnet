package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.constants.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.BlogVO;
import com.hmdp.vo.UserVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /*
    * 点赞博文
    * */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /*
     * 获取点赞排行榜
     * */
    @GetMapping("/likes/{id}")
    public Result queryLikeBlog(@PathVariable("id") Long id) {
        return blogService.blogLikeTop(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserVO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.success(records);
    }

    /*
    * 分页查询指定用户发布的笔记（他人主页）
    * */
    @GetMapping("/of/user")
    public Result queryBlogOfUser(@RequestParam("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryBlogOfUser(id, current);
    }



    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });
        return Result.success(records);
    }

    /*
    *
    *
    * */
    @GetMapping("/{id}")
    public Result<BlogVO> queryBlogById(@PathVariable("id") Long id) {
        return blogService.getBlogById(id);
    }


    /*分页查询关注用户的笔记*/
    @GetMapping("/of/follow")
    public Result queryFollowerBlog(@RequestParam(value = "lastId") Long lastTimeStamp,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryFollowerBlog(lastTimeStamp,offset);
    }

}
