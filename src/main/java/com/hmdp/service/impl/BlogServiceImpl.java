package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import com.hmdp.vo.BlogVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
        return Result.success(blogVO);
    }
}
