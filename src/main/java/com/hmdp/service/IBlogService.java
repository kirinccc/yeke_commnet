package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.vo.BlogVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {


    Result saveBlog(Blog blog);

    Result<BlogVO> getBlogById(Long id);

    Result likeBlog(Long id);

    Result blogLikeTop(Long id);
}
