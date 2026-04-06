package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.constants.PermissionConstants;
import com.gsp26se114.chatbot_rag_be.entity.OnboardingModule;
import com.gsp26se114.chatbot_rag_be.entity.OnboardingProgress;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateOnboardingModuleRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateOnboardingModuleRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateOnboardingProgressRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingModuleResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingMyModuleResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingMyOverviewResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingProgressResponse;
import com.gsp26se114.chatbot_rag_be.repository.OnboardingModuleRepository;
import com.gsp26se114.chatbot_rag_be.repository.OnboardingProgressRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "text/plain"
    );

    private final OnboardingModuleRepository onboardingModuleRepository;
    private final OnboardingProgressRepository onboardingProgressRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final MinioService minioService;

    public List<OnboardingModuleResponse> getTenantModules(String userEmail, boolean includeInactive) {
        User actor = requireTenantUser(userEmail);
        UUID tenantId = actor.getTenantId();

        List<OnboardingModule> modules = includeInactive
                ? onboardingModuleRepository.findByTenantIdOrderByDisplayOrderAscCreatedAtAsc(tenantId)
                : onboardingModuleRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(tenantId);

        return modules.stream()
                .map(this::toModuleResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OnboardingModuleResponse createTenantModule(String userEmail, CreateOnboardingModuleRequest request) {
        User actor = requireTenantUser(userEmail);

        List<String> requiredPermissions = normalizePermissions(request.requiredPermissions());
        validatePermissions(requiredPermissions);

        OnboardingModule module = new OnboardingModule();
        module.setTenantId(actor.getTenantId());
        module.setTitle(request.title().trim());
        module.setSummary(request.summary() != null ? request.summary().trim() : null);
        module.setContent(request.content().trim());
        module.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        module.setEstimatedMinutes(request.estimatedMinutes() != null ? request.estimatedMinutes() : 5);
        module.setRequiredPermissions(requiredPermissions);
        module.setIsActive(true);
        module.setCreatedBy(actor.getId());
        module.setCreatedAt(LocalDateTime.now());

        OnboardingModule saved = onboardingModuleRepository.save(module);
        log.info("Created onboarding module {} for tenant {}", saved.getId(), actor.getTenantId());
        return toModuleResponse(saved);
    }

    @Transactional
    public OnboardingModuleResponse updateTenantModule(
            String userEmail,
            UUID moduleId,
            UpdateOnboardingModuleRequest request
    ) {
        User actor = requireTenantUser(userEmail);
        OnboardingModule module = onboardingModuleRepository.findByIdAndTenantId(moduleId, actor.getTenantId())
                .orElseThrow(() -> new RuntimeException("Onboarding module không tồn tại"));

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("title không được để trống");
            }
            module.setTitle(title);
        }
        if (request.summary() != null) {
            module.setSummary(request.summary().trim());
        }
        if (request.content() != null) {
            String content = request.content().trim();
            if (content.isEmpty()) {
                throw new IllegalArgumentException("content không được để trống");
            }
            module.setContent(content);
        }
        if (request.displayOrder() != null) {
            module.setDisplayOrder(request.displayOrder());
        }
        if (request.estimatedMinutes() != null) {
            module.setEstimatedMinutes(request.estimatedMinutes());
        }
        if (request.requiredPermissions() != null) {
            List<String> requiredPermissions = normalizePermissions(request.requiredPermissions());
            validatePermissions(requiredPermissions);
            module.setRequiredPermissions(requiredPermissions);
        }
        if (request.isActive() != null) {
            module.setIsActive(request.isActive());
        }

        module.setUpdatedAt(LocalDateTime.now());
        OnboardingModule saved = onboardingModuleRepository.save(module);
        log.info("Updated onboarding module {} for tenant {}", saved.getId(), actor.getTenantId());
        return toModuleResponse(saved);
    }

    @Transactional
    public void deactivateTenantModule(String userEmail, UUID moduleId) {
        User actor = requireTenantUser(userEmail);
        OnboardingModule module = onboardingModuleRepository.findByIdAndTenantId(moduleId, actor.getTenantId())
                .orElseThrow(() -> new RuntimeException("Onboarding module không tồn tại"));

        module.setIsActive(false);
        module.setUpdatedAt(LocalDateTime.now());
        onboardingModuleRepository.save(module);
        log.info("Deactivated onboarding module {} for tenant {}", module.getId(), actor.getTenantId());
    }

    public List<OnboardingModuleResponse> getModulesForTenant(UUID tenantId, boolean includeInactive) {
        List<OnboardingModule> modules = includeInactive
                ? onboardingModuleRepository.findByTenantIdOrderByDisplayOrderAscCreatedAtAsc(tenantId)
                : onboardingModuleRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(tenantId);

        return modules.stream()
                .map(this::toModuleResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OnboardingModuleResponse createModuleForTenantByStaff(
            String actorEmail,
            UUID tenantId,
            CreateOnboardingModuleRequest request
    ) {
        User actor = requireExistingUser(actorEmail);

        List<String> requiredPermissions = normalizePermissions(request.requiredPermissions());
        validatePermissions(requiredPermissions);

        OnboardingModule module = new OnboardingModule();
        module.setTenantId(tenantId);
        module.setTitle(request.title().trim());
        module.setSummary(request.summary() != null ? request.summary().trim() : null);
        module.setContent(request.content().trim());
        module.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        module.setEstimatedMinutes(request.estimatedMinutes() != null ? request.estimatedMinutes() : 5);
        module.setRequiredPermissions(requiredPermissions);
        module.setIsActive(true);
        module.setCreatedBy(actor.getId());
        module.setCreatedAt(LocalDateTime.now());

        OnboardingModule saved = onboardingModuleRepository.save(module);
        log.info("Staff {} created onboarding module {} for tenant {}", actor.getEmail(), saved.getId(), tenantId);
        return toModuleResponse(saved);
    }

    @Transactional
    public OnboardingModuleResponse updateModuleForTenantByStaff(
            String actorEmail,
            UUID tenantId,
            UUID moduleId,
            UpdateOnboardingModuleRequest request
    ) {
        User actor = requireExistingUser(actorEmail);
        OnboardingModule module = onboardingModuleRepository.findByIdAndTenantId(moduleId, tenantId)
                .orElseThrow(() -> new RuntimeException("Onboarding module không tồn tại"));

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("title không được để trống");
            }
            module.setTitle(title);
        }
        if (request.summary() != null) {
            module.setSummary(request.summary().trim());
        }
        if (request.content() != null) {
            String content = request.content().trim();
            if (content.isEmpty()) {
                throw new IllegalArgumentException("content không được để trống");
            }
            module.setContent(content);
        }
        if (request.displayOrder() != null) {
            module.setDisplayOrder(request.displayOrder());
        }
        if (request.estimatedMinutes() != null) {
            module.setEstimatedMinutes(request.estimatedMinutes());
        }
        if (request.requiredPermissions() != null) {
            List<String> requiredPermissions = normalizePermissions(request.requiredPermissions());
            validatePermissions(requiredPermissions);
            module.setRequiredPermissions(requiredPermissions);
        }
        if (request.isActive() != null) {
            module.setIsActive(request.isActive());
        }

        module.setUpdatedAt(LocalDateTime.now());
        OnboardingModule saved = onboardingModuleRepository.save(module);
        log.info("Staff {} updated onboarding module {} for tenant {}", actor.getEmail(), saved.getId(), tenantId);
        return toModuleResponse(saved);
    }

    @Transactional
    public void deactivateModuleForTenantByStaff(UUID tenantId, UUID moduleId) {
        OnboardingModule module = onboardingModuleRepository.findByIdAndTenantId(moduleId, tenantId)
                .orElseThrow(() -> new RuntimeException("Onboarding module không tồn tại"));

        module.setIsActive(false);
        module.setUpdatedAt(LocalDateTime.now());
        onboardingModuleRepository.save(module);
        log.info("Deactivated onboarding module {} for tenant {} by staff action", module.getId(), tenantId);
    }

    @Transactional
    public OnboardingModuleResponse uploadModuleAttachmentForTenantByStaff(
            String actorEmail,
            UUID tenantId,
            UUID moduleId,
            MultipartFile file
    ) {
        User actor = requireExistingUser(actorEmail);
        validateAttachmentFile(file);

        OnboardingModule module = onboardingModuleRepository.findByIdAndTenantId(moduleId, tenantId)
                .orElseThrow(() -> new RuntimeException("Onboarding module không tồn tại"));

        String oldPath = module.getDetailFilePath();
        String folder = "tenant-" + tenantId + "/onboarding";
        String storagePath = minioService.uploadDocument(file, folder);

        if (oldPath != null && !oldPath.isBlank()) {
            try {
                minioService.deleteDocument(oldPath);
            } catch (RuntimeException ex) {
                log.warn("Could not delete old onboarding attachment path {}", oldPath, ex);
            }
        }

        module.setDetailFilePath(storagePath);
        module.setDetailFileName(sanitizeFileName(file.getOriginalFilename()));
        module.setDetailFileType(normalizeAttachmentContentType(file));
        module.setDetailFileSize(file.getSize());
        module.setUpdatedAt(LocalDateTime.now());

        OnboardingModule saved = onboardingModuleRepository.save(module);
        log.info("Staff {} uploaded onboarding attachment for module {} (tenant {})",
                actor.getEmail(), moduleId, tenantId);
        return toModuleResponse(saved);
    }

    public OnboardingAttachmentData getMyModuleAttachment(String userEmail, UUID moduleId) {
        User user = requireTenantUser(userEmail);
        OnboardingModule module = findVisibleModuleForUser(user, moduleId);

        if (module.getDetailFilePath() == null || module.getDetailFilePath().isBlank()) {
            throw new RuntimeException("Module onboarding chưa có file chi tiết");
        }

        byte[] content = minioService.downloadDocument(module.getDetailFilePath());
        String fileName = sanitizeFileName(module.getDetailFileName());
        String contentType = module.getDetailFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = resolveContentTypeFromFilename(fileName);
        }

        return new OnboardingAttachmentData(fileName, contentType, content);
    }

    public OnboardingMyOverviewResponse getMyOnboarding(String userEmail) {
        User user = requireTenantUser(userEmail);

        List<OnboardingModule> activeModules = onboardingModuleRepository
                .findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(user.getTenantId());

        List<OnboardingModule> visibleModules = activeModules.stream()
                .filter(module -> isVisibleForUser(module, user))
                .collect(Collectors.toList());

        Map<UUID, OnboardingProgress> progressByModuleId = onboardingProgressRepository
                .findByUserIdAndTenantId(user.getId(), user.getTenantId())
                .stream()
                .collect(Collectors.toMap(OnboardingProgress::getModuleId, p -> p, (left, right) -> left, HashMap::new));

        List<OnboardingMyModuleResponse> modules = new ArrayList<>();
        int completedCount = 0;

        for (OnboardingModule module : visibleModules) {
            OnboardingProgress progress = progressByModuleId.get(module.getId());
            int readPercent = progress != null && progress.getReadPercent() != null ? progress.getReadPercent() : 0;
            boolean completed = progress != null && Boolean.TRUE.equals(progress.getCompleted());
            if (completed) {
                completedCount++;
            }

            modules.add(new OnboardingMyModuleResponse(
                    module.getId(),
                    module.getTitle(),
                    module.getSummary(),
                    module.getContent(),
                    module.getDisplayOrder(),
                    module.getEstimatedMinutes(),
                    module.getRequiredPermissions(),
                    module.getDetailFileName(),
                    module.getDetailFileType(),
                    module.getDetailFileSize(),
                    readPercent,
                    completed,
                    progress != null ? progress.getCompletedAt() : null,
                    progress != null ? progress.getLastViewedAt() : null
            ));
        }

        int totalModules = modules.size();
        int progressPercent = totalModules == 0
                ? 100
                : (int) Math.round(completedCount * 100.0 / totalModules);

        return new OnboardingMyOverviewResponse(
                totalModules,
                completedCount,
                progressPercent,
                completedCount < totalModules,
                modules
        );
    }

    @Transactional
    public OnboardingProgressResponse updateMyProgress(
            String userEmail,
            UUID moduleId,
            UpdateOnboardingProgressRequest request
    ) {
        User user = requireTenantUser(userEmail);
        OnboardingModule module = findVisibleModuleForUser(user, moduleId);

        int readPercent = request.readPercent();
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdAndModuleId(user.getId(), module.getId())
                .orElseGet(() -> createProgress(user, module));

        progress.setReadPercent(readPercent);
        progress.setLastViewedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(progress.getCompleted()) && readPercent < 100) {
            progress.setCompleted(false);
            progress.setCompletedAt(null);
        }

        progress.setUpdatedAt(LocalDateTime.now());
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        return new OnboardingProgressResponse(
                saved.getModuleId(),
                saved.getReadPercent(),
                saved.getCompleted(),
                saved.getCompletedAt(),
                saved.getLastViewedAt()
        );
    }

    @Transactional
    public OnboardingProgressResponse markMyModuleCompleted(String userEmail, UUID moduleId) {
        User user = requireTenantUser(userEmail);
        OnboardingModule module = findVisibleModuleForUser(user, moduleId);

        OnboardingProgress progress = onboardingProgressRepository.findByUserIdAndModuleId(user.getId(), module.getId())
                .orElseGet(() -> createProgress(user, module));

        int currentReadPercent = progress.getReadPercent() != null ? progress.getReadPercent() : 0;
        if (currentReadPercent < 100) {
            throw new IllegalArgumentException("Bạn cần đọc 100% nội dung trước khi đánh dấu hoàn thành");
        }

        progress.setCompleted(true);
        progress.setCompletedAt(LocalDateTime.now());
        progress.setLastViewedAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());

        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        return new OnboardingProgressResponse(
                saved.getModuleId(),
                saved.getReadPercent(),
                saved.getCompleted(),
                saved.getCompletedAt(),
                saved.getLastViewedAt()
        );
    }

    private OnboardingProgress createProgress(User user, OnboardingModule module) {
        OnboardingProgress progress = new OnboardingProgress();
        progress.setTenantId(user.getTenantId());
        progress.setUserId(user.getId());
        progress.setModuleId(module.getId());
        progress.setReadPercent(0);
        progress.setCompleted(false);
        progress.setCreatedAt(LocalDateTime.now());
        return progress;
    }

    private OnboardingModule findVisibleModuleForUser(User user, UUID moduleId) {
        OnboardingModule module = onboardingModuleRepository.findByIdAndTenantId(moduleId, user.getTenantId())
                .orElseThrow(() -> new RuntimeException("Onboarding module không tồn tại"));

        if (!Boolean.TRUE.equals(module.getIsActive())) {
            throw new RuntimeException("Onboarding module không còn hoạt động");
        }

        if (!isVisibleForUser(module, user)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập onboarding module này");
        }

        return module;
    }

    private boolean isVisibleForUser(OnboardingModule module, User user) {
        List<String> requiredPermissions = normalizePermissions(module.getRequiredPermissions());
        if (requiredPermissions.isEmpty()) {
            return true;
        }
        return permissionService.hasAllPermissions(user, requiredPermissions.toArray(new String[0]));
    }

    private OnboardingModuleResponse toModuleResponse(OnboardingModule module) {
        return new OnboardingModuleResponse(
                module.getId(),
                module.getTitle(),
                module.getSummary(),
                module.getContent(),
                module.getDisplayOrder(),
                module.getEstimatedMinutes(),
                module.getRequiredPermissions(),
                module.getDetailFileName(),
                module.getDetailFileType(),
                module.getDetailFileSize(),
                module.getIsActive(),
                module.getCreatedAt(),
                module.getUpdatedAt()
        );
    }

    private void validateAttachmentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File đính kèm không được để trống");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("File đính kèm không được vượt quá 10MB");
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        boolean isPdf = hasExtension(fileName, ".pdf");
        boolean isTxt = hasExtension(fileName, ".txt");
        if (!isPdf && !isTxt) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file chi tiết định dạng .txt hoặc .pdf");
        }

        String contentType = normalizeAttachmentContentType(file);
        if (!ALLOWED_ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ MIME type text/plain hoặc application/pdf");
        }
    }

    private String normalizeAttachmentContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return resolveContentTypeFromFilename(file.getOriginalFilename());
        }

        String normalized = contentType.trim().toLowerCase();
        if ("application/octet-stream".equals(normalized)) {
            return resolveContentTypeFromFilename(file.getOriginalFilename());
        }
        if (normalized.startsWith("text/plain")) {
            return "text/plain";
        }
        if ("application/pdf".equals(normalized)) {
            return "application/pdf";
        }
        return normalized;
    }

    private String resolveContentTypeFromFilename(String fileName) {
        if (hasExtension(fileName, ".pdf")) {
            return "application/pdf";
        }
        if (hasExtension(fileName, ".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "onboarding-detail";
        }
        return fileName.replace("\\", "_").replace("/", "_").trim();
    }

    private boolean hasExtension(String fileName, String expectedExtension) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return fileName.trim().toLowerCase().endsWith(expectedExtension);
    }

    private void validatePermissions(List<String> permissions) {
        for (String permission : permissions) {
            if (!PermissionConstants.isValid(permission)) {
                throw new IllegalArgumentException("Permission onboarding không hợp lệ: " + permission);
            }
        }
    }

    private List<String> normalizePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .filter(permission -> permission != null && !permission.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private User requireTenantUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với email: " + userEmail));

        if (user.getTenantId() == null) {
            throw new AccessDeniedException("API này chỉ áp dụng cho tài khoản trong tenant");
        }
        return user;
    }

    private User requireExistingUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với email: " + userEmail));
    }

    public record OnboardingAttachmentData(String fileName, String contentType, byte[] content) {
    }
}
