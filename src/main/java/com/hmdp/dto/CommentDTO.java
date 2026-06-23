package com.hmdp.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class CommentDTO {
    private Long id;
    private Long userId;
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    private Boolean isTop;
    private LocalDateTime createTime;

    private String userName;
    private String userIcon;
    private Boolean isBlogAuthor;
    private Long answerUserId;
    private String answerUserName;
    private List<CommentDTO> replies;
}
