package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.NoteSharePreview;
import com.example.platform.dto.NoteShareResponse;
import com.example.platform.entity.Note;
import com.example.platform.entity.NoteShare;
import com.example.platform.entity.NoteTag;
import com.example.platform.mapper.NoteMapper;
import com.example.platform.mapper.NoteShareMapper;
import com.example.platform.mapper.NoteTagMapper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final int MAX_TAG_COUNT = 12;
    private static final int MAX_TAG_LENGTH = 50;
    private static final int MAX_SHARE_TOKEN_LENGTH = 64;
    private static final int MAX_ANCHOR_TYPE_LENGTH = 20;
    private static final int MAX_ANCHOR_TEXT_LENGTH = 1000;
    private static final int MAX_ANCHOR_IMAGE_LENGTH = 300_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteShareMapper noteShareMapper;

    public NoteService(NoteMapper noteMapper, NoteTagMapper noteTagMapper, NoteShareMapper noteShareMapper) {
        this.noteMapper = noteMapper;
        this.noteTagMapper = noteTagMapper;
        this.noteShareMapper = noteShareMapper;
    }

    public List<Note> listByUser(Long userId, String category, String keyword) {
        List<Note> notes = noteMapper.findByUserId(userId, optionalText(category, MAX_CATEGORY_LENGTH), optionalText(keyword, 100));
        for (Note note : notes) {
            note.setTags(noteTagMapper.findByNoteId(note.getId()));
        }
        return notes;
    }

    public List<Note> listByResource(Long resourceId, Long userId) {
        List<Note> notes = noteMapper.findByResourceId(resourceId, userId);
        for (Note note : notes) {
            note.setTags(noteTagMapper.findByNoteId(note.getId()));
        }
        return notes;
    }

    public Note getById(Long id, Long userId) {
        Note note = noteMapper.findById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BusinessException(404, "笔记不存在");
        }
        note.setTags(noteTagMapper.findByNoteId(note.getId()));
        return note;
    }

    @Transactional
    public Note create(Note note, List<String> tagNames) {
        note.setTitle(requiredText(note.getTitle(), "笔记标题不能为空", MAX_TITLE_LENGTH));
        note.setContent(requiredText(note.getContent(), "笔记内容不能为空", 0));
        note.setCategory(optionalText(note.getCategory(), MAX_CATEGORY_LENGTH));
        note.setAnchorType(normalizeAnchorType(note.getAnchorType()));
        note.setAnchorText(optionalText(note.getAnchorText(), MAX_ANCHOR_TEXT_LENGTH));
        note.setAnchorImage(optionalText(note.getAnchorImage(), MAX_ANCHOR_IMAGE_LENGTH));
        note.setAnchorSeconds(normalizeAnchorSeconds(note.getAnchorSeconds()));
        note.setIsFavorite(normalizeFavorite(note.getIsFavorite()));
        note.setStatus(1);
        noteMapper.insert(note);
        syncTags(note.getId(), note.getUserId(), tagNames);
        return getById(note.getId(), note.getUserId());
    }

    @Transactional
    public Note update(Long id, Note note, List<String> tagNames, Long userId) {
        Note existing = getById(id, userId);
        note.setId(id);
        note.setUserId(userId);
        note.setTitle(requiredText(note.getTitle(), "笔记标题不能为空", MAX_TITLE_LENGTH));
        note.setContent(requiredText(note.getContent(), "笔记内容不能为空", 0));
        note.setCategory(note.getCategory() == null ? existing.getCategory() : optionalText(note.getCategory(), MAX_CATEGORY_LENGTH));
        note.setResourceId(note.getResourceId() == null ? existing.getResourceId() : note.getResourceId());
        note.setAnchorType(note.getAnchorType() == null ? existing.getAnchorType() : normalizeAnchorType(note.getAnchorType()));
        note.setAnchorText(note.getAnchorText() == null ? existing.getAnchorText() : optionalText(note.getAnchorText(), MAX_ANCHOR_TEXT_LENGTH));
        note.setAnchorImage(note.getAnchorImage() == null ? existing.getAnchorImage() : optionalText(note.getAnchorImage(), MAX_ANCHOR_IMAGE_LENGTH));
        note.setAnchorSeconds(note.getAnchorSeconds() == null ? existing.getAnchorSeconds() : normalizeAnchorSeconds(note.getAnchorSeconds()));
        note.setIsFavorite(note.getIsFavorite() == null ? existing.getIsFavorite() : normalizeFavorite(note.getIsFavorite()));
        note.setStatus(1);
        if (noteMapper.update(note) == 0) {
            throw new BusinessException(404, "笔记不存在");
        }
        if (tagNames != null) {
            noteTagMapper.deleteNoteTagsByNoteId(id);
            syncTags(id, userId, tagNames);
        }
        return getById(id, userId);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        getById(id, userId);
        noteMapper.deleteById(id);
        noteTagMapper.deleteNoteTagsByNoteId(id);
    }

    private Long getOrCreateTag(String tagName, Long userId) {
        NoteTag existing = noteTagMapper.findByNameAndUserId(tagName, userId);
        if (existing != null) {
            return existing.getId();
        }
        NoteTag tag = new NoteTag();
        tag.setName(tagName);
        tag.setUserId(userId);
        noteTagMapper.insert(tag);
        return tag.getId();
    }

    public List<NoteTag> listTags(Long userId) {
        return noteTagMapper.findByUserId(userId);
    }

    @Transactional
    public Note merge(List<Long> noteIds, String title, Long userId) {
        if (noteIds == null || noteIds.size() < 2) {
            throw new BusinessException(400, "请至少选择两条要整合的笔记");
        }
        String mergedTitle = requiredText(title, "整合后的标题不能为空", MAX_TITLE_LENGTH);
        StringBuilder mergedContent = new StringBuilder();
        String category = null;
        for (Long noteId : noteIds) {
            Note note = getById(noteId, userId);
            if (category == null) {
                category = note.getCategory();
            }
            mergedContent.append("## ").append(note.getTitle()).append("\n\n");
            mergedContent.append(note.getContent()).append("\n\n");
        }
        Note merged = new Note();
        merged.setTitle(mergedTitle);
        merged.setContent(mergedContent.toString());
        merged.setCategory(category);
        merged.setUserId(userId);
        merged.setIsFavorite(0);
        merged.setStatus(1);
        noteMapper.insert(merged);
        return getById(merged.getId(), userId);
    }

    public String export(Long userId, String category, Long tagId) {
        String normalizedCategory = optionalText(category, MAX_CATEGORY_LENGTH);
        List<Note> notes;
        if (tagId != null) {
            notes = noteMapper.findByUserId(userId, null, null);
            notes.removeIf(note -> {
                List<NoteTag> tags = noteTagMapper.findByNoteId(note.getId());
                return tags.stream().noneMatch(tag -> tag.getId().equals(tagId));
            });
        } else {
            notes = noteMapper.findByUserId(userId, normalizedCategory, null);
        }
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 笔记导出\n\n");
        if (normalizedCategory != null) {
            markdown.append("**分类**: ").append(normalizedCategory).append("\n\n");
        }
        for (Note note : notes) {
            markdown.append("## ").append(note.getTitle()).append("\n\n");
            if (note.getAnchorSeconds() != null) {
                markdown.append("> 视频时间点: ").append(formatSeconds(note.getAnchorSeconds())).append("\n\n");
            }
            if (note.getAnchorText() != null) {
                markdown.append("> 记录片段: ").append(note.getAnchorText().replace("\n", "\n> ")).append("\n\n");
            }
            markdown.append(note.getContent()).append("\n\n");
            markdown.append("---\n\n");
        }
        return markdown.toString();
    }

    @Transactional
    public NoteShareResponse createShare(Long noteId, Long ownerId) {
        getById(noteId, ownerId);
        NoteShare existing = noteShareMapper.findActiveByNoteAndOwner(noteId, ownerId);
        if (existing != null) {
            return new NoteShareResponse(existing.getToken());
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String token = generateShareToken();
            if (noteShareMapper.findByToken(token) != null) {
                continue;
            }
            NoteShare share = new NoteShare();
            share.setToken(token);
            share.setNoteId(noteId);
            share.setOwnerId(ownerId);
            share.setImportCount(0);
            share.setStatus(1);
            noteShareMapper.insert(share);
            return new NoteShareResponse(token);
        }
        throw new BusinessException(500, "生成分享链接失败，请稍后重试");
    }

    public NoteSharePreview getShared(String token) {
        SharedNote shared = loadSharedNote(token);
        Note note = shared.note;
        NoteSharePreview preview = new NoteSharePreview();
        preview.setToken(shared.share.getToken());
        preview.setTitle(note.getTitle());
        preview.setContent(note.getContent());
        preview.setCategory(note.getCategory());
        preview.setResourceId(note.getResourceId());
        preview.setResourceTitle(note.getResourceTitle());
        preview.setAnchorType(note.getAnchorType());
        preview.setAnchorText(note.getAnchorText());
        preview.setAnchorImage(note.getAnchorImage());
        preview.setAnchorSeconds(note.getAnchorSeconds());
        preview.setTagNames(toTagNames(note.getTags()));
        preview.setCreateTime(note.getCreateTime());
        preview.setShareTime(shared.share.getCreateTime());
        return preview;
    }

    @Transactional
    public Note importShared(String token, Long userId) {
        SharedNote shared = loadSharedNote(token);
        Note source = shared.note;
        Note copy = new Note();
        copy.setTitle(source.getTitle());
        copy.setContent(source.getContent());
        copy.setCategory(source.getCategory());
        copy.setResourceId(source.getResourceId());
        copy.setAnchorType(source.getAnchorType());
        copy.setAnchorText(source.getAnchorText());
        copy.setAnchorImage(source.getAnchorImage());
        copy.setAnchorSeconds(source.getAnchorSeconds());
        copy.setUserId(userId);
        copy.setIsFavorite(0);
        copy.setStatus(1);

        Note imported = create(copy, toTagNames(source.getTags()));
        noteShareMapper.incrementImportCount(shared.share.getId());
        return imported;
    }

    private void syncTags(Long noteId, Long userId, List<String> tagNames) {
        for (String tagName : normalizeTagNames(tagNames)) {
            Long tagId = getOrCreateTag(tagName, userId);
            noteTagMapper.addNoteTag(noteId, tagId);
        }
    }

    private List<String> normalizeTagNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : tagNames) {
            String tag = optionalText(raw, MAX_TAG_LENGTH);
            if (tag != null) {
                normalized.add(tag);
            }
            if (normalized.size() >= MAX_TAG_COUNT) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private String requiredText(String value, String message, int maxLength) {
        String text = optionalText(value, maxLength);
        if (text == null) {
            throw new BusinessException(400, message);
        }
        return text;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return maxLength > 0 && text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private Integer normalizeFavorite(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private String normalizeAnchorType(String value) {
        String type = optionalText(value, MAX_ANCHOR_TYPE_LENGTH);
        if (type == null) {
            return null;
        }
        type = type.toUpperCase();
        return switch (type) {
            case "VIDEO", "TEXT", "DOCUMENT", "RESOURCE" -> type;
            default -> "RESOURCE";
        };
    }

    private Double normalizeAnchorSeconds(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            return null;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }

    private SharedNote loadSharedNote(String token) {
        String normalizedToken = requiredText(token, "分享链接不存在或已失效", MAX_SHARE_TOKEN_LENGTH);
        NoteShare share = noteShareMapper.findActiveByToken(normalizedToken);
        if (share == null) {
            throw new BusinessException(404, "分享链接不存在或已失效");
        }

        Note note = noteMapper.findById(share.getNoteId());
        if (note == null || !share.getOwnerId().equals(note.getUserId())) {
            throw new BusinessException(404, "分享链接不存在或已失效");
        }
        note.setTags(noteTagMapper.findByNoteId(note.getId()));
        return new SharedNote(share, note);
    }

    private List<String> toTagNames(List<NoteTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(NoteTag::getName).toList();
    }

    private String generateShareToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String formatSeconds(Double seconds) {
        int total = Math.max(0, (int) Math.floor(seconds));
        int hour = total / 3600;
        int minute = (total % 3600) / 60;
        int second = total % 60;
        if (hour > 0) {
            return String.format("%02d:%02d:%02d", hour, minute, second);
        }
        return String.format("%02d:%02d", minute, second);
    }

    private static class SharedNote {
        private final NoteShare share;
        private final Note note;

        private SharedNote(NoteShare share, Note note) {
            this.share = share;
            this.note = note;
        }
    }
}
