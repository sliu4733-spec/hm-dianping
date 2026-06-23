package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.CommentDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    private static final String COMMENT_LIKED_KEY = "comment:liked:";

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result saveComment(BlogComments comment) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (comment == null || comment.getBlogId() == null) {
            return Result.fail("评论对象不能为空");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return Result.fail("评论内容不能为空");
        }

        comment.setId(null);
        comment.setUserId(user.getId());
        comment.setContent(comment.getContent().trim());
        comment.setLiked(0);
        comment.setStatus(false);
        comment.setIsTop(false);
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        if (comment.getAnswerId() == null) {
            comment.setAnswerId(0L);
        }

        save(comment);
        blogService.update()
                .setSql("comments = comments + 1")
                .eq("id", comment.getBlogId())
                .update();
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryCommentsByBlogId(Long blogId) {
        List<BlogComments> allComments = query()
                .eq("blog_id", blogId)
                .orderByAsc("create_time")
                .list();
        if (allComments == null || allComments.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        Set<Long> userIds = allComments.stream()
                .map(BlogComments::getUserId)
                .collect(Collectors.toSet());
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        Blog blog = (Blog) blogService.queryBlogById(blogId).getData();
        Long blogAuthorId = blog == null ? null : blog.getUserId();
        UserDTO currentUser = UserHolder.getUser();

        List<CommentDTO> result = allComments.stream()
                .filter(comment -> comment.getParentId() == null || comment.getParentId() == 0)
                .map(comment -> toDTO(comment, userMap, blogAuthorId, currentUser, allComments))
                .sorted((left, right) -> {
                    boolean leftTop = Boolean.TRUE.equals(left.getIsTop());
                    boolean rightTop = Boolean.TRUE.equals(right.getIsTop());
                    if (leftTop == rightTop) {
                        return 0;
                    }
                    return leftTop ? -1 : 1;
                })
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    @Override
    public Result likeComment(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        String key = COMMENT_LIKED_KEY + id;
        String userId = user.getId().toString();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score == null) {
            update().setSql("liked = liked + 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
        } else {
            update().setSql("liked = liked - 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId);
        }
        return Result.ok();
    }

    @Override
    public Result topComment(Long id) {
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        Blog blog = (Blog) blogService.queryBlogById(comment.getBlogId()).getData();
        Long currentUserId = UserHolder.getUser().getId();
        if (blog == null || !blog.getUserId().equals(currentUserId)) {
            return Result.fail("只有笔记作者才能置顶评论");
        }

        boolean newTop = !Boolean.TRUE.equals(comment.getIsTop());
        update().set("is_top", newTop).eq("id", id).update();
        return Result.ok();
    }

    private CommentDTO toDTO(BlogComments comment,
                             Map<Long, User> userMap,
                             Long blogAuthorId,
                             UserDTO currentUser,
                             List<BlogComments> allComments) {
        CommentDTO dto = new CommentDTO();
        BeanUtil.copyProperties(comment, dto);
        User commentUser = userMap.get(comment.getUserId());
        if (commentUser != null) {
            dto.setUserName(commentUser.getNickName());
            dto.setUserIcon(commentUser.getIcon());
        }
        dto.setIsBlogAuthor(blogAuthorId != null && blogAuthorId.equals(comment.getUserId()));

        List<CommentDTO> replies = allComments.stream()
                .filter(reply -> reply.getParentId() != null && reply.getParentId().equals(comment.getId()))
                .map(reply -> toReplyDTO(reply, userMap, blogAuthorId, allComments))
                .collect(Collectors.toList());
        dto.setReplies(replies);
        return dto;
    }

    private CommentDTO toReplyDTO(BlogComments reply,
                                  Map<Long, User> userMap,
                                  Long blogAuthorId,
                                  List<BlogComments> allComments) {
        CommentDTO replyDTO = new CommentDTO();
        BeanUtil.copyProperties(reply, replyDTO);
        User replyUser = userMap.get(reply.getUserId());
        if (replyUser != null) {
            replyDTO.setUserName(replyUser.getNickName());
            replyDTO.setUserIcon(replyUser.getIcon());
        }
        replyDTO.setIsBlogAuthor(blogAuthorId != null && blogAuthorId.equals(reply.getUserId()));

        if (reply.getAnswerId() != null && reply.getAnswerId() != 0) {
            BlogComments answered = allComments.stream()
                    .filter(comment -> comment.getId().equals(reply.getAnswerId()))
                    .findFirst()
                    .orElse(null);
            if (answered != null) {
                replyDTO.setAnswerUserId(answered.getUserId());
                User answeredUser = userMap.get(answered.getUserId());
                if (answeredUser != null) {
                    replyDTO.setAnswerUserName(answeredUser.getNickName());
                }
            }
        }
        return replyDTO;
    }
}
