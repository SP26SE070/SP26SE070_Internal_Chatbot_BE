package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gsp26se114.chatbot_rag_be.entity.Department;
import com.gsp26se114.chatbot_rag_be.entity.EmployeeImportSession;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.ConfirmEmployeeImportRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.EmployeeImportConfirmResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.EmployeeImportPreviewResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.EmployeeImportPreviewResponse.ImportSummary;
import com.gsp26se114.chatbot_rag_be.payload.response.EmployeeImportPreviewResponse.InvalidImportRow;
import com.gsp26se114.chatbot_rag_be.payload.response.EmployeeImportPreviewResponse.ValidImportRow;
import com.gsp26se114.chatbot_rag_be.repository.AuditLogRepository;
import com.gsp26se114.chatbot_rag_be.repository.DepartmentRepository;
import com.gsp26se114.chatbot_rag_be.repository.EmployeeImportSessionRepository;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.text.Normalizer;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeImportService {

    private static final Gson GSON = new Gson();
    private static final Type VALID_ROWS_TYPE = new TypeToken<List<StoredValidRow>>() {}.getType();
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0\\d{9}|\\+84\\d{9})$");
    private static final List<String> FORBIDDEN_ROLE_CODES = List.of("SUPER_ADMIN", "STAFF", "TENANT_ADMIN");
    private static final Set<String> REQUIRED_HEADER_KEYS = Set.of("ho_ten", "contact_email", "ma_vai_tro");
    private static final Map<String, String> HEADER_KEY_ALIASES = Map.ofEntries(
            Map.entry("stt", "stt"),
            Map.entry("so_thu_tu", "stt"),
            Map.entry("sothutu", "stt"),
            Map.entry("no", "stt"),
            Map.entry("ho_va_ten", "ho_ten"),
            Map.entry("ho_ten", "ho_ten"),
            Map.entry("hoten", "ho_ten"),
            Map.entry("hovaten", "ho_ten"),
            Map.entry("ten", "ho_ten"),
            Map.entry("email_lien_he", "contact_email"),
            Map.entry("emaillienhe", "contact_email"),
            Map.entry("contact_email", "contact_email"),
            Map.entry("email", "contact_email"),
            Map.entry("so_dien_thoai", "so_dien_thoai"),
            Map.entry("so_dien_thoa", "so_dien_thoai"),
            Map.entry("sdt", "so_dien_thoai"),
            Map.entry("dien_thoai", "so_dien_thoai"),
            Map.entry("ngay_sinh", "ngay_sinh"),
            Map.entry("dia_chi", "dia_chi"),
            Map.entry("ma_vai_tro", "ma_vai_tro"),
            Map.entry("mavaitro", "ma_vai_tro"),
            Map.entry("vai_tro", "ma_vai_tro"),
            Map.entry("ma_phong_ban", "ma_phong_ban"),
            Map.entry("phong_ban", "ma_phong_ban")
    );
    /** Tên hiển thị tiếng Việt cho vai trò hệ thống (cột Tham chiếu) */
    private static final Map<String, String> ROLE_DISPLAY_NAME_VI = Map.of(
            "EMPLOYEE", "Nhân viên",
            "CEO", "Giám đốc điều hành"
    );
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeImportSessionRepository sessionRepository;
    private final UserProvisioningService userProvisioningService;
    private final WelcomeEmailDispatcher welcomeEmailDispatcher;
    private final SubscriptionValidationService subscriptionValidationService;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.import.max-rows:500}")
    private int maxRows;

    @Value("${app.import.session-ttl-minutes:30}")
    private int sessionTtlMinutes;

    public byte[] buildTemplate(String tenantAdminEmail) throws IOException {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet dataSheet = workbook.createSheet("Nhân viên");
            Row header = dataSheet.createRow(0);
            String[] labels = {
                    "STT",
                    "Họ và tên *",
                    "Email liên hệ *",
                    "Số điện thoại",
                    "Ngày sinh",
                    "Địa chỉ",
                    "Mã vai trò *",
                    "Mã phòng ban"
            };
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            // Cột Số điện thoại = Text để Excel không tự bỏ số 0 đầu
            dataSheet.setDefaultColumnStyle(3, textStyle);

            for (int i = 0; i < labels.length; i++) {
                header.createCell(i).setCellValue(labels[i]);
            }
            Row example = dataSheet.createRow(1);
            example.createCell(0).setCellValue(1);
            example.createCell(1).setCellValue("Nguyễn Văn A");
            example.createCell(2).setCellValue("nguyenvana@congty.com");
            Cell phoneExample = example.createCell(3);
            phoneExample.setCellStyle(textStyle);
            phoneExample.setCellValue("0912345678");
            example.createCell(4).setCellValue("01/01/1990");
            example.createCell(5).setCellValue("Hà Nội");
            example.createCell(6).setCellValue("EMPLOYEE");
            example.createCell(7).setCellValue("HR");

            Sheet guide = workbook.createSheet("Hướng dẫn");
            guide.createRow(0).createCell(0).setCellValue("1. Sheet \"Nhân viên\": nhập danh sách cần import.");
            guide.createRow(1).createCell(0).setCellValue(
                    "2. Cột STT: đánh số 1, 2, 3… (tùy chọn, chỉ để dễ theo dõi khi nhập)."
            );
            guide.createRow(2).createCell(0).setCellValue("3. Cột bắt buộc: Họ và tên, Email liên hệ, Mã vai trò.");
            guide.createRow(3).createCell(0).setCellValue("4. Ngày sinh: dd/MM/yyyy (ví dụ 01/01/1990).");
            guide.createRow(4).createCell(0).setCellValue(
                    "5. Số điện thoại: cột đã định dạng Text — gõ đủ 10 số (vd: 0912345678) hoặc +84xxxxxxxxx."
            );
            guide.createRow(5).createCell(0).setCellValue("6. Mã vai trò / Mã phòng ban: copy đúng cột \"Mã\" ở sheet Tham chiếu.");
            guide.createRow(6).createCell(0).setCellValue("7. Sheet Tham chiếu: danh sách riêng theo tổ chức của bạn (tải mẫu lúc đăng nhập).");
            guide.createRow(7).createCell(0).setCellValue("8. Không đổi tên các cột dòng đầu sheet Nhân viên.");

            Sheet ref = workbook.createSheet("Tham chiếu");
            ref.createRow(0).createCell(0).setCellValue(
                    "Danh sách vai trò & phòng ban của tổ chức bạn — chỉ để tra cứu khi điền Excel"
            );
            Row refHeader = ref.createRow(1);
            refHeader.createCell(0).setCellValue("Loại");
            refHeader.createCell(1).setCellValue("Mã (điền vào Excel)");
            refHeader.createCell(2).setCellValue("Tên hiển thị");
            int rowIdx = 2;
            List<RoleEntity> roles = loadAssignableRoles(tenantId);
            for (RoleEntity role : roles) {
                Row row = ref.createRow(rowIdx++);
                row.createCell(0).setCellValue("Vai trò");
                row.createCell(1).setCellValue(role.getCode());
                row.createCell(2).setCellValue(toVietnameseRoleDisplayName(role));
            }
            for (Department dept : departmentRepository.findByTenantIdAndIsActive(tenantId, true)) {
                Row row = ref.createRow(rowIdx++);
                row.createCell(0).setCellValue("Phòng ban");
                row.createCell(1).setCellValue(dept.getCode());
                row.createCell(2).setCellValue(toVietnameseDepartmentDisplayName(dept));
            }

            for (int i = 0; i < labels.length; i++) {
                dataSheet.autoSizeColumn(i);
            }
            for (int i = 0; i < 3; i++) {
                ref.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public EmployeeImportPreviewResponse preview(String tenantAdminEmail, MultipartFile file) throws IOException {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("FILE_EMPTY");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".xlsx")) {
            throw new IllegalArgumentException("FILE_TYPE_XLSX_ONLY");
        }

        List<StoredValidRow> validStored = new ArrayList<>();
        List<InvalidImportRow> invalid = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        Set<String> phonesInFile = new HashSet<>();
        int dataRows = 0;

        Map<String, RoleEntity> rolesByCode = indexRoles(tenantId);
        Map<String, Department> deptsByCode = indexDepartments(tenantId);

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = resolveDataSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("SHEET_MISSING");
            }

            Map<String, Integer> colIndex = parseHeaderRow(sheet.getRow(0));
            if (!colIndex.containsKey("ho_ten") || !colIndex.containsKey("contact_email") || !colIndex.containsKey("ma_vai_tro")) {
                throw new IllegalArgumentException("HEADERS_MISSING");
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmptyRow(row, colIndex)) {
                    continue;
                }
                dataRows++;
                if (dataRows > maxRows) {
                    throw new IllegalArgumentException("MAX_ROWS|" + maxRows);
                }

                int rowNumber = r + 1;
                validateRow(rowNumber, row, colIndex, tenantAdmin, rolesByCode, deptsByCode,
                        emailsInFile, phonesInFile, validStored, invalid);
            }
        }

        if (dataRows == 0) {
            throw new IllegalArgumentException("NO_DATA_ROWS");
        }

        EmployeeImportSession session = new EmployeeImportSession();
        session.setTenantId(tenantId);
        session.setCreatedByUserId(tenantAdmin.getId());
        session.setValidRowsJson(GSON.toJson(validStored));
        session.setInvalidRowsJson(GSON.toJson(invalid));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(sessionTtlMinutes));
        session = sessionRepository.save(session);

        List<ValidImportRow> validPreview = validStored.stream()
                .map(this::toValidPreview)
                .toList();

        return new EmployeeImportPreviewResponse(
                session.getId(),
                session.getExpiresAt(),
                new ImportSummary(dataRows, validPreview.size(), invalid.size()),
                validPreview,
                invalid
        );
    }

    @Transactional
    public EmployeeImportConfirmResponse confirm(String tenantAdminEmail, ConfirmEmployeeImportRequest request) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();

        EmployeeImportSession session = sessionRepository
                .findByIdAndTenantId(request.importSessionId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("SESSION_NOT_FOUND"));

        if (!"PENDING".equals(session.getStatus())) {
            throw new IllegalArgumentException("SESSION_ALREADY_DONE");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("SESSION_EXPIRED");
        }
        if (!session.getCreatedByUserId().equals(tenantAdmin.getId())) {
            throw new IllegalArgumentException("SESSION_FORBIDDEN");
        }

        List<StoredValidRow> rows = GSON.fromJson(session.getValidRowsJson(), VALID_ROWS_TYPE);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("NO_VALID_ROWS");
        }

        subscriptionValidationService.validateBulkUserCreation(tenantId, rows.size());

        List<EmployeeImportConfirmResponse.CreatedImportUser> created = new ArrayList<>();
        List<EmployeeImportConfirmResponse.FailedImportUser> failed = new ArrayList<>();
        int emailsQueued = 0;

        for (StoredValidRow row : rows) {
            try {
                LocalDate dob = row.dateOfBirthIso != null && !row.dateOfBirthIso.isBlank()
                        ? LocalDate.parse(row.dateOfBirthIso) : null;
                CreateUserRequest createReq = new CreateUserRequest(
                        row.fullName,
                        row.contactEmail,
                        row.phoneNumber,
                        dob,
                        row.address,
                        row.roleId,
                        row.departmentId,
                        null
                );
                UserProvisioningService.ProvisionedUser provisioned =
                        userProvisioningService.provisionUser(tenantAdmin, createReq);

                WelcomeEmailDispatcher.WelcomeEmailJob job = new WelcomeEmailDispatcher.WelcomeEmailJob(
                        provisioned.user().getContactEmail(),
                        provisioned.user().getFullName(),
                        provisioned.user().getEmail(),
                        provisioned.temporaryPassword(),
                        provisioned.role().getName(),
                        provisioned.department() != null ? provisioned.department().getName() : "Chưa xác định",
                        provisioned.tenant().getName()
                );
                welcomeEmailDispatcher.sendEmployeeWelcomeAsync(job);
                emailsQueued++;

                created.add(new EmployeeImportConfirmResponse.CreatedImportUser(
                        row.rowNumber,
                        provisioned.user().getId(),
                        provisioned.user().getFullName(),
                        provisioned.user().getEmail(),
                        provisioned.user().getContactEmail()
                ));

                writeAuditLog(tenantAdmin, provisioned.user().getEmail());
            } catch (Exception e) {
                log.warn("Import row {} failed: {}", row.rowNumber, e.getMessage());
                failed.add(new EmployeeImportConfirmResponse.FailedImportUser(
                        row.rowNumber,
                        row.contactEmail,
                        e.getMessage() != null ? e.getMessage() : "UNKNOWN"
                ));
            }
        }

        session.setStatus("COMPLETED");
        sessionRepository.save(session);

        return new EmployeeImportConfirmResponse(
                created.size(),
                failed.size(),
                emailsQueued,
                created,
                failed
        );
    }

    private void validateRow(
            int rowNumber,
            Row row,
            Map<String, Integer> colIndex,
            User tenantAdmin,
            Map<String, RoleEntity> rolesByCode,
            Map<String, Department> deptsByCode,
            Set<String> emailsInFile,
            Set<String> phonesInFile,
            List<StoredValidRow> validStored,
            List<InvalidImportRow> invalid
    ) {
        String stt = parseSttCell(row, colIndex);
        String fullName = cellString(row, colIndex.get("ho_ten"));
        String contactEmail = cellString(row, colIndex.get("contact_email"));
        String phone = colIndex.containsKey("so_dien_thoai")
                ? cellPhoneString(row, colIndex.get("so_dien_thoai"))
                : null;
        String dobStr = colIndex.containsKey("ngay_sinh") ? cellString(row, colIndex.get("ngay_sinh")) : null;
        String address = colIndex.containsKey("dia_chi") ? cellString(row, colIndex.get("dia_chi")) : null;
        String roleCode = cellString(row, colIndex.get("ma_vai_tro"));
        String deptCode = colIndex.containsKey("ma_phong_ban") ? cellString(row, colIndex.get("ma_phong_ban")) : null;

        List<String> errors = new ArrayList<>();

        if (fullName == null || fullName.isBlank()) {
            errors.add(EmployeeImportErrorCodes.FULL_NAME_REQUIRED);
        }
        if (contactEmail == null || contactEmail.isBlank()) {
            errors.add(EmployeeImportErrorCodes.CONTACT_EMAIL_REQUIRED);
        } else if (!contactEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            errors.add(EmployeeImportErrorCodes.CONTACT_EMAIL_INVALID);
        } else {
            String emailLower = contactEmail.trim().toLowerCase();
            if (!emailsInFile.add(emailLower)) {
                errors.add(EmployeeImportErrorCodes.EMAIL_DUPLICATE_IN_FILE);
            } else if (userRepository.existsByContactEmailIgnoreCase(contactEmail.trim())) {
                errors.add(EmployeeImportErrorCodes.EMAIL_EXISTS_IN_SYSTEM);
            }
        }

        if (phone != null && !phone.isBlank()) {
            String cleanPhone = phone.replace("-", "").trim();
            if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
                errors.add(EmployeeImportErrorCodes.PHONE_INVALID);
            } else {
                String normalized = cleanPhone.startsWith("+84") ? "0" + cleanPhone.substring(3) : cleanPhone;
                if (!phonesInFile.add(normalized)) {
                    errors.add(EmployeeImportErrorCodes.PHONE_DUPLICATE_IN_FILE);
                } else if (userRepository.existsByPhoneNumber(normalized)) {
                    errors.add(EmployeeImportErrorCodes.PHONE_EXISTS_IN_SYSTEM);
                }
            }
        }

        LocalDate dateOfBirth = null;
        if (dobStr != null && !dobStr.isBlank()) {
            Optional<LocalDate> parsed = parseDate(dobStr);
            if (parsed.isEmpty()) {
                errors.add(EmployeeImportErrorCodes.DOB_INVALID);
            } else {
                try {
                    dateOfBirth = parsed.get();
                    new CreateUserRequest(
                            "x", "a@b.com", null, dateOfBirth, null, 1, null, null
                    );
                } catch (IllegalArgumentException e) {
                    errors.add(mapDobValidationCode(e));
                    dateOfBirth = null;
                }
            }
        }

        if (address != null && address.length() > 500) {
            errors.add(EmployeeImportErrorCodes.ADDRESS_TOO_LONG);
        }

        RoleEntity role = null;
        if (roleCode == null || roleCode.isBlank()) {
            errors.add(EmployeeImportErrorCodes.ROLE_REQUIRED);
        } else {
            role = rolesByCode.get(roleCode.trim().toUpperCase());
            if (role == null) {
                errors.add(EmployeeImportErrorCodes.ROLE_NOT_FOUND);
            } else if (FORBIDDEN_ROLE_CODES.contains(role.getCode())) {
                errors.add(EmployeeImportErrorCodes.withParam(
                        EmployeeImportErrorCodes.ROLE_FORBIDDEN, role.getCode()));
            }
        }

        Department department = null;
        if (deptCode != null && !deptCode.isBlank()) {
            department = deptsByCode.get(deptCode.trim().toUpperCase());
            if (department == null) {
                errors.add(EmployeeImportErrorCodes.DEPT_NOT_FOUND);
            }
        }

        if (!isTenantAdmin(tenantAdmin) && role != null) {
            Integer actorDept = tenantAdmin.getDepartmentId();
            if (department == null || actorDept == null || !actorDept.equals(department.getId())) {
                errors.add(EmployeeImportErrorCodes.DEPT_SCOPE_FORBIDDEN);
            }
        }

        if (!errors.isEmpty()) {
            invalid.add(new InvalidImportRow(
                    rowNumber,
                    stt,
                    fullName != null && !fullName.isBlank() ? fullName.trim() : null,
                    contactEmail != null && !contactEmail.isBlank() ? contactEmail.trim() : null,
                    errors
            ));
            return;
        }

        validStored.add(new StoredValidRow(
                rowNumber,
                stt,
                fullName.trim(),
                contactEmail.trim(),
                phone != null && !phone.isBlank() ? phone.replace("-", "").trim() : null,
                dateOfBirth != null ? dateOfBirth.format(DateTimeFormatter.ISO_LOCAL_DATE) : null,
                address != null ? address.trim() : null,
                role.getId(),
                department != null ? department.getId() : null,
                role.getCode(),
                role.getName(),
                department != null ? department.getCode() : null,
                department != null ? department.getName() : null
        ));
    }

    private static String mapDobValidationCode(IllegalArgumentException e) {
        String m = e.getMessage();
        if (m != null && m.contains("10 tuổi")) {
            return EmployeeImportErrorCodes.DOB_TOO_YOUNG;
        }
        if (m != null && m.contains("100 tuổi")) {
            return EmployeeImportErrorCodes.DOB_TOO_OLD;
        }
        return EmployeeImportErrorCodes.DOB_INVALID;
    }

    private Sheet resolveDataSheet(Workbook workbook) {
        for (String name : List.of("Nhân viên", "Nhan_vien", "Nhan vien")) {
            Sheet sheet = workbook.getSheet(name);
            if (sheet != null) {
                return sheet;
            }
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private String parseSttCell(Row row, Map<String, Integer> colIndex) {
        if (!colIndex.containsKey("stt")) {
            return null;
        }
        String raw = cellString(row, colIndex.get("stt"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            double n = Double.parseDouble(trimmed.replace(",", "."));
            if (n == Math.floor(n) && n >= 0 && n < 10_000_000) {
                return String.valueOf((long) n);
            }
        } catch (NumberFormatException ignored) {
            // giữ nguyên chuỗi người dùng nhập
        }
        return trimmed;
    }

    private ValidImportRow toValidPreview(StoredValidRow row) {
        return new ValidImportRow(
                row.rowNumber,
                row.stt,
                row.fullName,
                row.contactEmail,
                row.phoneNumber,
                row.dateOfBirthIso != null
                        ? LocalDate.parse(row.dateOfBirthIso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : null,
                row.address,
                row.roleCode,
                row.roleName,
                row.departmentCode,
                row.departmentName
        );
    }

    private Map<String, Integer> parseHeaderRow(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("File thiếu dòng tiêu đề");
        }
        Map<String, Integer> map = new HashMap<>();
        short lastCell = headerRow.getLastCellNum();
        for (int i = 0; i < lastCell; i++) {
            String raw = cellString(headerRow, i);
            String key = resolveHeaderKey(raw);
            if (key != null) {
                map.put(key, i);
            }
        }
        return map;
    }

    private String resolveHeaderKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace("*", "")
                .replace("(bắt buộc)", "")
                .replace("(bat buoc)", "")
                .trim();
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'd');
        normalized = normalized
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (HEADER_KEY_ALIASES.containsKey(normalized)) {
            return HEADER_KEY_ALIASES.get(normalized);
        }
        if (REQUIRED_HEADER_KEYS.contains(normalized)) {
            return normalized;
        }
        return null;
    }

    private boolean isEmptyRow(Row row, Map<String, Integer> colIndex) {
        if (row == null) return true;
        for (Map.Entry<String, Integer> entry : colIndex.entrySet()) {
            if ("stt".equals(entry.getKey())) {
                continue;
            }
            String v = cellString(row, entry.getValue());
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private String cellString(Row row, Integer col) {
        if (row == null || col == null) return null;
        return cellString(row, col.intValue());
    }

    private String cellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
                double n = cell.getNumericCellValue();
                if (n == Math.floor(n)) {
                    yield String.valueOf((long) n);
                }
                yield String.valueOf(n);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                yield switch (cell.getCachedFormulaResultType()) {
                    case STRING -> cell.getStringCellValue().trim();
                    case NUMERIC -> {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            yield cell.getLocalDateTimeCellValue().toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        }
                        double n = cell.getNumericCellValue();
                        yield n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
                    }
                    default -> cell.getCellFormula();
                };
            }
            default -> null;
        };
    }

    private String cellPhoneString(Row row, Integer col) {
        String raw = cellString(row, col);
        return normalizePhoneFromExcel(raw);
    }

    /**
     * Excel thường lưu 0912345678 thành số 912345678 — khôi phục số 0 đầu nếu đủ điều kiện VN.
     */
    private String normalizePhoneFromExcel(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String clean = raw.replace("-", "").replace(" ", "").trim();
        if (clean.startsWith("+")) {
            return clean;
        }
        if (clean.matches("^\\d{9}$") && clean.charAt(0) == '9') {
            return "0" + clean;
        }
        if (clean.matches("^84\\d{9}$")) {
            return "+84" + clean.substring(2);
        }
        return clean;
    }

    private Optional<LocalDate> parseDate(String raw) {
        String trimmed = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(trimmed, fmt));
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    private Map<String, RoleEntity> indexRoles(UUID tenantId) {
        Map<String, RoleEntity> map = new HashMap<>();
        for (RoleEntity role : loadAssignableRoles(tenantId)) {
            map.put(role.getCode().toUpperCase(), role);
        }
        return map;
    }

    private List<RoleEntity> loadAssignableRoles(UUID tenantId) {
        Map<Integer, RoleEntity> byId = new LinkedHashMap<>();
        roleRepository.findByCode("EMPLOYEE").ifPresent(r -> byId.put(r.getId(), r));
        for (RoleEntity r : roleRepository.findByTenantId(tenantId)) {
            if (!FORBIDDEN_ROLE_CODES.contains(r.getCode())) {
                byId.put(r.getId(), r);
            }
        }
        return List.copyOf(byId.values());
    }

    private String toVietnameseRoleDisplayName(RoleEntity role) {
        if (role.getCode() != null) {
            String mapped = ROLE_DISPLAY_NAME_VI.get(role.getCode().toUpperCase(Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
        }
        if (role.getName() != null && !role.getName().isBlank()) {
            return role.getName();
        }
        return role.getCode();
    }

    private String toVietnameseDepartmentDisplayName(Department dept) {
        if (dept.getName() != null && !dept.getName().isBlank()) {
            return dept.getName();
        }
        return dept.getCode();
    }

    private Map<String, Department> indexDepartments(UUID tenantId) {
        Map<String, Department> map = new HashMap<>();
        for (Department d : departmentRepository.findByTenantIdAndIsActive(tenantId, true)) {
            map.put(d.getCode().toUpperCase(), d);
        }
        return map;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));
    }

    private boolean isTenantAdmin(User actor) {
        if (actor.getRoleId() == null) return false;
        return roleRepository.findById(actor.getRoleId())
                .map(r -> "TENANT_ADMIN".equals(r.getCode()))
                .orElse(false);
    }

    private void writeAuditLog(User actor, String targetEmail) {
        AuditLog logEntry = new AuditLog();
        logEntry.setTenantId(actor.getTenantId());
        logEntry.setUserId(actor.getId());
        logEntry.setUserEmail(actor.getEmail());
        logEntry.setAction("USER_CREATE_IMPORT");
        logEntry.setEntityType("User");
        logEntry.setDescription("Imported user: " + targetEmail);
        logEntry.setStatus("SUCCESS");
        logEntry.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(logEntry);
    }

    /** JSON-serializable row for confirm step */
    static class StoredValidRow {
        int rowNumber;
        String stt;
        String fullName;
        String contactEmail;
        String phoneNumber;
        String dateOfBirthIso;
        String address;
        Integer roleId;
        Integer departmentId;
        String roleCode;
        String roleName;
        String departmentCode;
        String departmentName;

        StoredValidRow(int rowNumber, String stt, String fullName, String contactEmail, String phoneNumber,
                       String dateOfBirthIso, String address, Integer roleId, Integer departmentId,
                       String roleCode, String roleName, String departmentCode, String departmentName) {
            this.rowNumber = rowNumber;
            this.stt = stt;
            this.fullName = fullName;
            this.contactEmail = contactEmail;
            this.phoneNumber = phoneNumber;
            this.dateOfBirthIso = dateOfBirthIso;
            this.address = address;
            this.roleId = roleId;
            this.departmentId = departmentId;
            this.roleCode = roleCode;
            this.roleName = roleName;
            this.departmentCode = departmentCode;
            this.departmentName = departmentName;
        }
    }
}
