package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.DanmakuConfigDTO;
import com.example.platform.dto.DanmakuDTO;
import com.example.platform.entity.Danmaku;
import com.example.platform.entity.Note;
import com.example.platform.entity.NoteShare;
import com.example.platform.entity.Resource;
import com.example.platform.entity.User;
import com.example.platform.mapper.DanmakuMapper;
import com.example.platform.mapper.NoteMapper;
import com.example.platform.mapper.NoteShareMapper;
import com.example.platform.mapper.ResourceMapper;
import com.example.platform.security.CurrentUser;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DanmakuService {
    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList("scroll", "top", "bottom"));
    private static final Set<String> ALLOWED_PERMISSIONS = new HashSet<>(Arrays.asList("EVERYONE", "LOGGED", "OWNER"));

    private final DanmakuMapper danmakuMapper;
    private final ResourceMapper resourceMapper;
    private final NoteShareMapper noteShareMapper;
    private final NoteMapper noteMapper;

    public DanmakuService(DanmakuMapper danmakuMapper, ResourceMapper resourceMapper,
                          NoteShareMapper noteShareMapper, NoteMapper noteMapper) {
        this.danmakuMapper = danmakuMapper;
        this.resourceMapper = resourceMapper;
        this.noteShareMapper = noteShareMapper;
        this.noteMapper = noteMapper;
    }

    public List<Danmaku> list(Long resourceId) {
        return list(resourceId, null, null);
    }

    public List<Danmaku> list(Long resourceId, String mode, String shareToken) {
        Resource resource = resourceMapper.findById(resourceId);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        List<Danmaku> danmakus = danmakuMapper.findByResource(resourceId);
        if (!isSharedNoteMode(mode)) {
            return danmakus;
        }

        Set<Long> visibleUserIds = new LinkedHashSet<>();
        if (resource.getUserId() != null) {
            visibleUserIds.add(resource.getUserId());
        }
        User current = CurrentUser.get();
        if (current != null) {
            visibleUserIds.add(current.getId());
        }
        Long shareOwnerId = resolveShareOwnerId(resourceId, shareToken);
        if (shareOwnerId != null) {
            visibleUserIds.add(shareOwnerId);
        }
        if (visibleUserIds.isEmpty()) {
            return List.of();
        }
        return danmakus.stream()
                .filter(danmaku -> danmaku.getUserId() != null && visibleUserIds.contains(danmaku.getUserId()))
                .toList();
    }

    public Danmaku send(Long resourceId, DanmakuDTO dto) {
        Resource resource = resourceMapper.findById(resourceId);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        if (!Integer.valueOf(1).equals(resource.getDanmakuEnabled())) {
            throw new BusinessException(403, "该资源已关闭弹幕");
        }
        User current = CurrentUser.get();
        checkPermission(resource, current);

        Danmaku danmaku = new Danmaku();
        danmaku.setResourceId(resourceId);
        danmaku.setUserId(current == null ? null : current.getId());
        danmaku.setContent(dto.getContent().trim());
        BigDecimal time = dto.getTimeSeconds() == null ? BigDecimal.ZERO : dto.getTimeSeconds();
        if (time.signum() < 0) time = BigDecimal.ZERO;
        danmaku.setTimeSeconds(time);
        String type = dto.getType() == null ? "scroll" : dto.getType().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) type = "scroll";
        danmaku.setType(type);
        danmaku.setColor(normalizeColor(dto.getColor()));
        danmaku.setStatus(1);
        danmakuMapper.insert(danmaku);
        return danmakuMapper.findById(danmaku.getId());
    }

    public void delete(Long danmakuId) {
        Danmaku danmaku = danmakuMapper.findById(danmakuId);
        if (danmaku == null) {
            throw new BusinessException(404, "弹幕不存在");
        }
        User current = CurrentUser.get();
        if (current == null) {
            throw new BusinessException(401, "请先登录");
        }
        boolean isAdmin = "ADMIN".equals(current.getRole());
        boolean isAuthor = danmaku.getUserId() != null && danmaku.getUserId().equals(current.getId());
        boolean isOwner = false;
        if (!isAdmin && !isAuthor) {
            Resource resource = resourceMapper.findById(danmaku.getResourceId());
            isOwner = resource != null && resource.getUserId() != null
                    && resource.getUserId().equals(current.getId());
        }
        if (!isAdmin && !isAuthor && !isOwner) {
            throw new BusinessException(403, "无权限删除该弹幕");
        }
        danmakuMapper.softDelete(danmakuId);
    }

    public Resource updateConfig(Long resourceId, DanmakuConfigDTO config) {
        Resource resource = resourceMapper.findById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }
        User current = CurrentUser.get();
        if (current == null) {
            throw new BusinessException(401, "请先登录");
        }
        boolean allowed = "ADMIN".equals(current.getRole())
                || (resource.getUserId() != null && resource.getUserId().equals(current.getId()));
        if (!allowed) {
            throw new BusinessException(403, "只有发布者或管理员可以修改弹幕配置");
        }
        Integer enabled = Boolean.FALSE.equals(config.getEnabled()) ? 0 : 1;
        String permission = config.getPermission();
        if (permission == null) permission = resource.getDanmakuPermission();
        permission = permission == null ? "LOGGED" : permission.toUpperCase();
        if (!ALLOWED_PERMISSIONS.contains(permission)) permission = "LOGGED";
        resourceMapper.updateDanmakuConfig(resourceId, enabled, permission);
        return resourceMapper.findById(resourceId);
    }

    private void checkPermission(Resource resource, User current) {
        String permission = resource.getDanmakuPermission();
        if (permission == null) permission = "LOGGED";
        switch (permission.toUpperCase()) {
            case "EVERYONE":
                return;
            case "LOGGED":
                if (current == null) throw new BusinessException(401, "请登录后再发送弹幕");
                return;
            case "OWNER":
                if (current == null) throw new BusinessException(401, "请登录后再发送弹幕");
                boolean isAdmin = "ADMIN".equals(current.getRole());
                boolean isOwner = resource.getUserId() != null && resource.getUserId().equals(current.getId());
                if (!isAdmin && !isOwner) {
                    throw new BusinessException(403, "仅发布者可发送弹幕");
                }
                return;
            default:
                throw new BusinessException(500, "未知的弹幕权限配置");
        }
    }

    private String normalizeColor(String color) {
        if (color == null) return "#ffffff";
        String trimmed = color.trim();
        if (trimmed.isEmpty()) return "#ffffff";
        if (trimmed.length() > 200) return "#ffffff";
        // Hex solid
        if (trimmed.matches("^#[0-9a-fA-F]{3,8}$")) return trimmed.toLowerCase();
        // rgb/rgba
        if (trimmed.matches("^rgba?\\([0-9.,%\\s]+\\)$")) return trimmed;
        // Gradient: grad:#hex,#hex[,...] or anim:#hex,#hex[,...]
        // Accept any number of comma-separated hex stops after the prefix.
        if (trimmed.matches("^(grad|anim):#[0-9a-fA-F]{3,8}(,#[0-9a-fA-F]{3,8})+$")) {
            return trimmed.toLowerCase();
        }
        return "#ffffff";
    }

    private boolean isSharedNoteMode(String mode) {
        if (mode == null) {
            return false;
        }
        String normalized = mode.trim().toUpperCase();
        return "SHARE_NOTE".equals(normalized) || "SHARED_NOTE".equals(normalized);
    }

    private Long resolveShareOwnerId(Long resourceId, String shareToken) {
        if (shareToken == null || shareToken.trim().isEmpty()) {
            return null;
        }
        NoteShare share = noteShareMapper.findActiveByToken(shareToken.trim());
        if (share == null) {
            return null;
        }
        Note note = noteMapper.findById(share.getNoteId());
        if (note == null || !Objects.equals(share.getOwnerId(), note.getUserId())) {
            return null;
        }
        return resourceId.equals(note.getResourceId()) ? share.getOwnerId() : null;
    }
}
