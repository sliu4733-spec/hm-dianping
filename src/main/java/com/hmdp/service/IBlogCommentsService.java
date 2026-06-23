package com.hmdp.service;

import com.hmdp.dto.CommentDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IBlogCommentsService extends IService<BlogComments> {

    Result saveComment(BlogComments comment);

    Result queryCommentsByBlogId(Long blogId);

    Result likeComment(Long id);

    Result topComment(Long id);
}
