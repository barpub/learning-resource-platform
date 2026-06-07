package com.example.platform.controller;

import com.example.platform.common.BusinessException;
import com.example.platform.common.Result;
import com.example.platform.dto.NoteSharePreview;
import com.example.platform.dto.NoteShareResponse;
import com.example.platform.entity.Note;
import com.example.platform.entity.NoteTag;
import com.example.platform.security.CurrentUser;
import com.example.platform.service.NoteService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    private Long requireUser() {
        Long userId = CurrentUser.id();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    @GetMapping
    public Result<List<Note>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.success(noteService.listByUser(requireUser(), category, keyword));
    }

    @PostMapping("/{id}/share")
    public Result<NoteShareResponse> createShare(@PathVariable Long id) {
        return Result.success(noteService.createShare(id, requireUser()));
    }

    @GetMapping("/share/{token}")
    public Result<NoteSharePreview> getShare(@PathVariable String token) {
        return Result.success(noteService.getShared(token));
    }

    @PostMapping("/share/{token}/import")
    public Result<Note> importShare(@PathVariable String token) {
        return Result.success(noteService.importShared(token, requireUser()));
    }

    @GetMapping("/{id}")
    public Result<Note> get(@PathVariable Long id) {
        return Result.success(noteService.getById(id, requireUser()));
    }

    @PostMapping
    public Result<Note> create(@RequestBody NoteRequest request) {
        requireBody(request);
        Long userId = requireUser();
        Note note = new Note();
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setCategory(request.getCategory());
        note.setResourceId(request.getResourceId());
        note.setAnchorType(request.getAnchorType());
        note.setAnchorText(request.getAnchorText());
        note.setAnchorImage(request.getAnchorImage());
        note.setAnchorSeconds(request.getAnchorSeconds());
        note.setUserId(userId);
        note.setIsFavorite(request.getIsFavorite() != null ? request.getIsFavorite() : 0);
        return Result.success(noteService.create(note, request.getTagNames()));
    }

    @PutMapping("/{id}")
    public Result<Note> update(@PathVariable Long id, @RequestBody NoteRequest request) {
        requireBody(request);
        Long userId = requireUser();
        Note note = new Note();
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setCategory(request.getCategory());
        note.setResourceId(request.getResourceId());
        note.setAnchorType(request.getAnchorType());
        note.setAnchorText(request.getAnchorText());
        note.setAnchorImage(request.getAnchorImage());
        note.setAnchorSeconds(request.getAnchorSeconds());
        note.setIsFavorite(request.getIsFavorite());
        return Result.success(noteService.update(id, note, request.getTagNames(), userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noteService.delete(id, requireUser());
        return Result.success(null);
    }

    @GetMapping("/tags")
    public Result<List<NoteTag>> listTags() {
        return Result.success(noteService.listTags(requireUser()));
    }

    @GetMapping("/by-resource/{resourceId}")
    public Result<List<Note>> listByResource(@PathVariable Long resourceId) {
        return Result.success(noteService.listByResource(resourceId, requireUser()));
    }

    @PostMapping("/merge")
    public Result<Note> merge(@RequestBody MergeRequest request) {
        requireBody(request);
        return Result.success(noteService.merge(request.getNoteIds(), request.getTitle(), requireUser()));
    }

    @GetMapping("/export")
    public Result<String> export(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long tagId) {
        return Result.success(noteService.export(requireUser(), category, tagId));
    }

    private void requireBody(Object request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
    }

    static class NoteRequest {
        private String title;
        private String content;
        private String category;
        private Long resourceId;
        private String anchorType;
        private String anchorText;
        private String anchorImage;
        private Double anchorSeconds;
        private Integer isFavorite;
        private List<String> tagNames;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public String getAnchorType() { return anchorType; }
        public void setAnchorType(String anchorType) { this.anchorType = anchorType; }
        public String getAnchorText() { return anchorText; }
        public void setAnchorText(String anchorText) { this.anchorText = anchorText; }
        public String getAnchorImage() { return anchorImage; }
        public void setAnchorImage(String anchorImage) { this.anchorImage = anchorImage; }
        public Double getAnchorSeconds() { return anchorSeconds; }
        public void setAnchorSeconds(Double anchorSeconds) { this.anchorSeconds = anchorSeconds; }
        public Integer getIsFavorite() { return isFavorite; }
        public void setIsFavorite(Integer isFavorite) { this.isFavorite = isFavorite; }
        public List<String> getTagNames() { return tagNames; }
        public void setTagNames(List<String> tagNames) { this.tagNames = tagNames; }
    }

    static class MergeRequest {
        private List<Long> noteIds;
        private String title;

        public List<Long> getNoteIds() { return noteIds; }
        public void setNoteIds(List<Long> noteIds) { this.noteIds = noteIds; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
