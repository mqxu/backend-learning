package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.zhihu.common.BusinessException;
import top.mqxu.zhihu.common.ErrorCode;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.UserContext;
import top.mqxu.zhihu.dto.CommentSaveRequest;
import top.mqxu.zhihu.entity.Answer;
import top.mqxu.zhihu.entity.Comment;
import top.mqxu.zhihu.mapper.AnswerMapper;
import top.mqxu.zhihu.mapper.CommentMapper;
import top.mqxu.zhihu.vo.CommentVO;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 回答下的评论。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final AnswerMapper answerMapper;
    private final AnswerService answerService;
    private final LookupService lookupService;

    public PageResult<CommentVO> page(Long answerId, long pageNum, long pageSize) {
        answerService.require(answerId);
        Page<Comment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getAnswerId, answerId)
                        // 一级评论在前，楼中楼按时间跟在后面
                        .orderByAsc(Comment::getParentId)
                        .orderByAsc(Comment::getCreatedAt));
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    @Transactional
    public CommentVO create(Long answerId, CommentSaveRequest request) {
        Long userId = UserContext.requireUserId();
        Answer answer = answerService.require(answerId);

        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        Long replyToUserId = request.getReplyToUserId();
        if (parentId > 0) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getAnswerId(), answerId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "父评论不属于这条回答");
            }
            // 没指定回复谁时，默认回复父评论的作者
            if (replyToUserId == null) {
                replyToUserId = parent.getAuthorId();
            }
        }

        Comment comment = new Comment();
        comment.setAnswerId(answerId);
        comment.setAuthorId(userId);
        comment.setContent(request.getContent());
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        commentMapper.insert(comment);

        answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                .eq(Answer::getId, answer.getId())
                .setSql("comment_count = comment_count + 1"));
        return toVOs(List.of(comment)).get(0);
    }

    @Transactional
    public void delete(Long commentId) {
        Long userId = UserContext.requireUserId();
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        commentMapper.deleteById(commentId);
        answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                .eq(Answer::getId, comment.getAnswerId())
                .setSql("comment_count = GREATEST(comment_count - 1, 0)"));
    }

    private List<CommentVO> toVOs(List<Comment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = comments.stream().map(Comment::getAuthorId).distinct().toList();
        List<Long> replyIds = comments.stream()
                .map(Comment::getReplyToUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // 作者与被回复人可能是两拨人，合成一次查询
        Map<Long, String> names = lookupService.nicknames(
                Stream.concat(authorIds.stream(), replyIds.stream()).distinct().toList());

        return comments.stream()
                .map(c -> new CommentVO(c.getId(), c.getAnswerId(), c.getContent(),
                        c.getAuthorId(), names.get(c.getAuthorId()),
                        c.getParentId(), c.getReplyToUserId(),
                        c.getReplyToUserId() == null ? null : names.get(c.getReplyToUserId()),
                        c.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
