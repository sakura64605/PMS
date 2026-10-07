package com.hongjie.pms.AI.modules.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.entity.AiUserMemory;
import com.hongjie.pms.AI.modules.mapper.AiUserMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户长期记忆服务：长期记忆 = 用户跨会话的持久事实/偏好，存 MySQL（结构化、可精确覆盖）。
 * <p>短期记忆 = 滑动窗口，仍走 {@link ChatMemoryService}（ai_chat_message）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryService {

    /** 每用户长期记忆条数上限，超出按 重要度→更新时间 淘汰 */
    private static final int MAX_MEMORIES_PER_USER = 50;

    private final AiUserMemoryMapper memoryMapper;

    /** 取某用户启用的长期记忆（重要度高/较新的在前），用于注入 system prompt */
    public List<AiUserMemory> getActiveByUser(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AiUserMemory> wrapper = new LambdaQueryWrapper<AiUserMemory>()
                .eq(AiUserMemory::getUserId, userId)
                .eq(AiUserMemory::getStatus, 1)
                .orderByDesc(AiUserMemory::getImportance)
                .orderByDesc(AiUserMemory::getUpdatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return memoryMapper.selectList(wrapper);
    }

    /** 把模型抽取到的一组长期记忆落库：逐条去重（同用户同内容→刷新时间），超出上限淘汰低优先级 */
    public void saveFacts(Long userId, String sourceSession, List<String> facts) {
        if (userId == null || facts == null || facts.isEmpty()) {
            return;
        }
        int saved = 0;
        for (String fact : facts) {
            if (!StringUtils.hasText(fact)) {
                continue;
            }
            String content = fact.trim();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            AiUserMemory existing = findByUserAndContent(userId, content);
            if (existing != null) {
                // 已存在 → 仅刷新（视为再次确认），并轻微提升重要度上限5
                existing.setImportance(Math.min(5, (existing.getImportance() == null ? 1 : existing.getImportance()) + 1));
                memoryMapper.updateById(existing);
            } else {
                AiUserMemory mem = AiUserMemory.builder()
                        .userId(userId)
                        .content(content)
                        .sourceSession(sourceSession)
                        .importance(1)
                        .status(1)
                        .build();
                memoryMapper.insert(mem);
                saved++;
            }
        }
        enforceCap(userId);
        log.info("用户 {} 长期记忆保存: 新增{}条(重复{}条刷新)", userId, saved, facts.size() - saved);
    }

    private AiUserMemory findByUserAndContent(Long userId, String content) {
        LambdaQueryWrapper<AiUserMemory> wrapper = new LambdaQueryWrapper<AiUserMemory>()
                .eq(AiUserMemory::getUserId, userId)
                .eq(AiUserMemory::getContent, content)
                .last("LIMIT 1");
        return memoryMapper.selectOne(wrapper);
    }

    /** 超出上限时，按 重要度升序→更新时间升序 删掉多余的低价值记忆 */
    private void enforceCap(Long userId) {
        LambdaQueryWrapper<AiUserMemory> countWrapper = new LambdaQueryWrapper<AiUserMemory>()
                .eq(AiUserMemory::getUserId, userId)
                .eq(AiUserMemory::getStatus, 1);
        Long total = memoryMapper.selectCount(countWrapper);
        if (total == null || total <= MAX_MEMORIES_PER_USER) {
            return;
        }
        long excess = total - MAX_MEMORIES_PER_USER;
        LambdaQueryWrapper<AiUserMemory> drop = new LambdaQueryWrapper<AiUserMemory>()
                .eq(AiUserMemory::getUserId, userId)
                .eq(AiUserMemory::getStatus, 1)
                .orderByAsc(AiUserMemory::getImportance)
                .orderByAsc(AiUserMemory::getUpdatedAt)
                .last("LIMIT " + excess);
        List<AiUserMemory> toDrop = memoryMapper.selectList(drop);
        for (AiUserMemory m : toDrop) {
            memoryMapper.deleteById(m.getId());
        }
        log.info("用户 {} 长期记忆超限，淘汰 {} 条", userId, toDrop.size());
    }

    /** 删除单条记忆（管理员/用户侧清理用） */
    public void deleteMemory(Long id) {
        memoryMapper.deleteById(id);
    }

    // ==================== 用户自助查看/修改（与对话自动添加共用同表，按内容去重天然不冲突） ====================

    /** 用户查看自己的长期记忆（启用的，重要度高/较新在前） */
    public List<AiUserMemory> listByUser(Long userId) {
        return getActiveByUser(userId, 100);
    }

    /** 用户手动添加一条长期记忆（source_session 标记为 manual 以便区分来源） */
    public AiUserMemory addManual(Long userId, String content) {
        if (userId == null || !StringUtils.hasText(content)) {
            return null;
        }
        String c = content.trim();
        if (c.length() > 500) {
            c = c.substring(0, 500);
        }
        AiUserMemory existing = findByUserAndContent(userId, c);
        if (existing != null) {
            // 已存在（可能是对话自动添加的）→ 重新启用并刷新，避免重复
            existing.setStatus(1);
            existing.setImportance(Math.min(5, (existing.getImportance() == null ? 1 : existing.getImportance()) + 1));
            memoryMapper.updateById(existing);
            return existing;
        }
        AiUserMemory mem = AiUserMemory.builder()
                .userId(userId).content(c).sourceSession("manual").importance(1).status(1).build();
        memoryMapper.insert(mem);
        enforceCap(userId);
        log.info("用户 {} 手动添加长期记忆: {}", userId, c);
        return mem;
    }

    /** 用户修改自己的某条记忆内容/重要度（校验归属） */
    public boolean updateByUser(Long userId, Long id, String content, Integer importance) {
        AiUserMemory mem = memoryMapper.selectById(id);
        if (mem == null || !mem.getUserId().equals(userId)) {
            return false;
        }
        if (StringUtils.hasText(content)) {
            String c = content.trim();
            mem.setContent(c.length() > 500 ? c.substring(0, 500) : c);
        }
        if (importance != null) {
            mem.setImportance(Math.max(1, Math.min(5, importance)));
        }
        memoryMapper.updateById(mem);
        log.info("用户 {} 修改长期记忆 id={}", userId, id);
        return true;
    }

    /** 用户删除自己的某条记忆：软删（status=0），避免下次对话又把它抽回来 */
    public boolean deleteByUser(Long userId, Long id) {
        AiUserMemory mem = memoryMapper.selectById(id);
        if (mem == null || !mem.getUserId().equals(userId)) {
            return false;
        }
        mem.setStatus(0);
        memoryMapper.updateById(mem);
        log.info("用户 {} 删除长期记忆 id={}", userId, id);
        return true;
    }

    /** 清空某用户全部记忆 */
    public void clearByUser(Long userId) {
        LambdaQueryWrapper<AiUserMemory> wrapper = new LambdaQueryWrapper<AiUserMemory>()
                .eq(AiUserMemory::getUserId, userId);
        memoryMapper.delete(wrapper);
    }
}
