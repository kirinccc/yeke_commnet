package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;


    /*
    * 获取当前用户对发帖用户的关注状态
    * */
    @GetMapping("/or/not/{blogUserId}")
    public Result isFollow(@PathVariable("blogUserId") Long blogUserId) {
        return followService.isFollow(blogUserId);
    }

    /*
    * 关注/取关当前发帖用户
    * */
    @PutMapping("/{id}/{isFollow}")
    public Result followOrNot(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        return followService.followOrNot(id, isFollow);
    }


    /*
     * 查询共同关注
     * */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id){
        return followService.commonFollow(id);
    }

}
