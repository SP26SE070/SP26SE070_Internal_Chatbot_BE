INSERT INTO tenants (
    tenant_id,
    name,
    address,
    website,
    company_size,
    contact_email,
    representative_name,
    representative_position,
    representative_phone,
    request_message,
    requested_at,
    status,
    reviewed_by,
    reviewed_at,
    created_at
) VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'FPT Software',
    'Tòa nhà FPT, Phố Duy Tân, Cầu Giấy, Hà Nội',
    'https://fpt.com.vn',
    '500+',

    'contact@fpt.com.vn',
    'Nguyễn Văn A',
    'CEO',
    '+84-24-73007300',
    'FPT Software muốn triển khai chatbot nội bộ để hỗ trợ 10,000+ nhân viên',
    CURRENT_TIMESTAMP - interval '7 days',
    'ACTIVE',
    NULL, -- Will be set to actual SUPER_ADMIN UUID after creation
    CURRENT_TIMESTAMP - interval '5 days',
    CURRENT_TIMESTAMP - interval '7 days'
);

INSERT INTO tenants (
    tenant_id,
    name,
    address,
    website,
    company_size,
    contact_email,
    representative_name,
    representative_position,
    representative_phone,
    request_message,
    requested_at,
    status,
    created_at
) VALUES (
    '660e8400-e29b-41d4-a716-446655440001',
    'VinGroup Corporation',
    '458 Minh Khai, Hai Bà Trưng, Hà Nội',
    'https://vingroup.net',
    '500+',
    'it.admin@vingroup.net',
    'Trần Thị B',
    'IT Director',
    '+84-24-39749999',
    'VinGroup cần chatbot để quản lý kiến thức nội bộ cho 50,000+ nhân viên',
    CURRENT_TIMESTAMP - interval '2 days',
    'PENDING',
    CURRENT_TIMESTAMP - interval '2 days'
);

INSERT INTO tenants (
    tenant_id,
    name,
    contact_email,
    representative_name,
    request_message,
    requested_at,
    status,
    reviewed_at,
    rejection_reason,
    created_at
) VALUES (
    '770e8400-e29b-41d4-a716-446655440002',
    'Small Startup Co',
    'contact@smallstartup.com',
    'Lê Văn C',
    'Startup nhỏ muốn dùng thử',
    CURRENT_TIMESTAMP - interval '10 days',
    'REJECTED',
    CURRENT_TIMESTAMP - interval '9 days',
    'Công ty chưa đủ quy mô để sử dụng platform (yêu cầu tối thiểu 50 nhân viên)',
    CURRENT_TIMESTAMP - interval '10 days'
);

INSERT INTO roles (code, name, level, description, tenant_id, role_type) VALUES
('SUPER_ADMIN', 'Super Administrator', 1, 'System administrator with full access to platform', NULL, 'SYSTEM'),
('STAFF', 'Platform Staff', 2, 'Platform staff member', NULL, 'SYSTEM');

INSERT INTO roles (code, name, level, description, tenant_id, role_type) VALUES
('TENANT_ADMIN', 'Tenant Administrator', 2, 'Organization administrator with full tenant access', NULL, 'FIXED'),
('EMPLOYEE', 'Employee', 4, 'Regular employee with basic access', NULL, 'FIXED');

INSERT INTO departments (tenant_id, code, name, description, is_active) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'ADMINISTRATION', 'Phòng Hành Chính', 'Quản lý hành chính tổng thể', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'KNOWLEDGE', 'Phòng Quản Lý Kiến Thức', 'Quản lý tài liệu và kiến thức', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'DEV', 'Phòng Phát Triển', 'Phát triển phần mềm', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'HR', 'Phòng Nhân Sự', 'Quản lý nhân sự', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'FINANCE', 'Phòng Tài Chính', 'Quản lý tài chính', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'GOVERNANCE', 'Phòng Quản Trị', 'Quản trị hệ thống', TRUE);

INSERT INTO users (email, contact_email, password, full_name, phone_number, role_id, department_id, tenant_id)
VALUES
-- SUPER_ADMIN (role_id = 1, no tenant)
('superadmin@system.com', NULL, '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Super Administrator', '+84-123-456-789', 1, NULL, NULL),

-- STAFF (role_id = 2, no tenant)
('staff@system.com', 'staff@system.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Platform Staff', '+84-987-111-222', 2, NULL, NULL),

-- Tenant Users (FPT Software)
-- TENANT_ADMIN (role_id = 3) - Has DOCUMENT_ALL → Can manage Document Dashboard
('admin@fpt.com', 'fpt.admin.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Tenant Admin', '+84-987-654-321', 3, 1, '550e8400-e29b-41d4-a716-446655440000'),

-- EMPLOYEE (role_id = 4) - Basic profile + chatbot usage
('employee1@fpt.com', 'fpt.employee1.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 1', '+84-901-234-567', 4, 3, '550e8400-e29b-41d4-a716-446655440000'),
('employee2@fpt.com', 'fpt.employee2.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 2', '+84-902-345-678', 4, 4, '550e8400-e29b-41d4-a716-446655440000'),
('employee3@fpt.com', 'fpt.employee3.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 3', '+84-903-456-789', 4, 5, '550e8400-e29b-41d4-a716-446655440000');

INSERT INTO users (email, contact_email, password, full_name, role_id, tenant_id, reset_password_token, token_expiry)
VALUES ('forgot_user@fpt.com', 'forgot.user.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Forgot User', 4, '550e8400-e29b-41d4-a716-446655440000', '888888', CURRENT_TIMESTAMP + interval '15 minutes');

INSERT INTO onboarding_modules (
    onboarding_module_id, tenant_id, title, summary, content,
    estimated_minutes, display_order, required_permissions, created_by
) VALUES
(
    'f1000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    'Khởi động hệ thống & Dashboard tổng quan / System Kickoff and Dashboard Tour',
    'Bắt đầu từ dashboard theo role và hiểu đúng ý nghĩa từng mục điều hướng chính trong hệ thống',
    '[VI]\nMục tiêu:\n- Xác định đúng dashboard theo vai trò đăng nhập.\n- Hiểu chức năng từng mục trong sidebar/quick actions trước khi thao tác nghiệp vụ.\n- Nắm thứ tự làm việc chuẩn: Dashboard -> Profile -> Document Dashboard -> Chatbot -> Quản trị nâng cao.\n\nLuồng sử dụng theo role:\n1. Đăng nhập hệ thống.\n2. Nếu là EMPLOYEE, vào dashboard tại /employee để xem tiến độ và các hành động chính.\n3. Nếu là TENANT_ADMIN, vào dashboard tại /tenant-admin để quản trị tổ chức.\n4. Nếu là STAFF, vào dashboard tại /staff để quản trị tenant toàn hệ thống.\n\nChi tiết các mục trên Tenant Admin dashboard (/tenant-admin):\n- Employees (/tenant-admin/employees): quản lý danh sách nhân viên, reset mật khẩu, cập nhật quyền bổ sung.\n- Departments (/tenant-admin/departments): tổ chức phòng ban và phân bổ nhân sự.\n- Roles (/tenant-admin/roles): cấu hình vai trò cố định và custom roles.\n- Document Dashboard (/tenant-admin/documents): quản lý tài liệu nội bộ, danh mục, thẻ.\n- AI Chatbot (/chatbot): hỏi đáp nội bộ có RAG.\n- Analytics (/tenant-admin/analytics): thống kê truy vấn AI, token và tài liệu.\n- Subscription (/tenant-admin/subscription): chọn gói, tạo thanh toán, theo dõi lịch sử.\n\nChecklist sau khi đọc:\n- Tôi biết dashboard ứng với role của mình.\n- Tôi biết mỗi mục quản trị tenant dùng để làm gì.\n- Tôi có thể chỉ ra đúng nơi thao tác khi có yêu cầu nghiệp vụ.\n\n[EN]\nObjective:\n- Identify the correct dashboard for the signed-in role.\n- Understand the purpose of each sidebar/quick-action entry before doing operations.\n- Follow the recommended sequence: Dashboard -> Profile -> Document Dashboard -> Chatbot -> Advanced administration.\n\nRole-based navigation flow:\n1. Sign in.\n2. EMPLOYEE lands on /employee for personal workflow and task shortcuts.\n3. TENANT_ADMIN lands on /tenant-admin for tenant management.\n4. STAFF lands on /staff for cross-tenant platform operations.\n\nTenant Admin dashboard breakdown (/tenant-admin):\n- Employees (/tenant-admin/employees): manage employee list, reset passwords, update extra permissions.\n- Departments (/tenant-admin/departments): maintain department structure and assignment.\n- Roles (/tenant-admin/roles): manage fixed and custom roles.\n- Document Dashboard (/tenant-admin/documents): manage internal documents, categories, tags.\n- AI Chatbot (/chatbot): internal Q&A with RAG.\n- Analytics (/tenant-admin/analytics): usage and document statistics.\n- Subscription (/tenant-admin/subscription): plan selection, payment creation, payment history.\n\nAfter-reading checklist:\n- I can identify my role-specific dashboard.\n- I understand what each tenant admin section does.\n- I know exactly where to perform each core operation.',
    8,
    1,
    '[]'::jsonb,
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com')
),
(
    'f1000000-0000-0000-0000-000000000002',
    '550e8400-e29b-41d4-a716-446655440000',
    'Cập nhật hồ sơ cá nhân / Profile Setup and Personal Info Update',
    'Hướng dẫn chi tiết cách vào Profile để cập nhật thông tin cá nhân, đổi mật khẩu và cập nhật contact email bằng OTP',
    '[VI]\nMục tiêu:\n- Hoàn thiện hồ sơ cá nhân ngay sau lần đăng nhập đầu tiên.\n- Đảm bảo thông tin liên hệ chính xác để nhận OTP/thông báo hệ thống.\n- Áp dụng chính sách bảo mật mật khẩu theo chuẩn hiện tại.\n\nLuồng thao tác đúng theo code hiện tại:\n1. Vào /profile từ menu tài khoản (ở chatbot có menu user ở góc phải trên cùng).\n2. Trong form cập nhật thông tin, điền các trường:\n   - Số điện thoại (phoneNumber)\n   - Ngày sinh (định dạng dd/mm/yyyy, hệ thống kiểm tra >= 18 tuổi)\n   - Địa chỉ (address)\n3. Nhấn nút lưu để gọi API update profile.\n4. Nếu cần đổi mật khẩu:\n   - Nhập mật khẩu cũ (trừ trường hợp bắt buộc đổi lần đầu)\n   - Nhập mật khẩu mới và xác nhận\n   - Mật khẩu phải có chữ hoa, chữ thường, số, ký tự đặc biệt và tối thiểu 8 ký tự.\n5. Nếu cần đổi contact email:\n   - Nhập email mới\n   - Gửi OTP\n   - Nhập OTP để xác nhận cập nhật email liên hệ.\n\nLưu ý thực tế:\n- Email đăng nhập (email) và contact email là hai thông tin khác nhau.\n- Contact email dùng cho kịch bản xác minh/phục hồi, cần luôn hoạt động.\n\nChecklist sau khi đọc:\n- Tôi biết đường dẫn và form cập nhật profile.\n- Tôi biết quy tắc mật khẩu khi đổi.\n- Tôi biết quy trình đổi contact email qua OTP.\n\n[EN]\nObjective:\n- Complete your profile setup right after first login.\n- Keep contact information accurate for OTP/notification flows.\n- Follow the current password policy in the codebase.\n\nWorkflow aligned with current implementation:\n1. Open /profile from the user menu.\n2. Update fields:\n   - Phone number\n   - Date of birth (dd/mm/yyyy, age >= 18 validation)\n   - Address\n3. Save profile updates.\n4. To change password:\n   - Provide old password (except forced first-time change)\n   - Enter and confirm new password\n   - New password must include upper/lowercase letters, number, special character, minimum length 8.\n5. To update contact email:\n   - Enter new contact email\n   - Request OTP\n   - Verify OTP to finalize update.\n\nOperational notes:\n- Login email and contact email are separate fields.\n- Contact email should remain reachable for verification and recovery flows.\n\nAfter-reading checklist:\n- I know the profile route and update form.\n- I understand the password policy.\n- I can complete contact email update via OTP.',
    10,
    2,
    '[]'::jsonb,
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com')
),
(
    'f1000000-0000-0000-0000-000000000003',
    '550e8400-e29b-41d4-a716-446655440000',
    'Document Dashboard: tài liệu, danh mục, thẻ / Document Dashboard: Documents, Categories, Tags',
    'Đồng bộ terminology Document Dashboard và hướng dẫn thao tác tài liệu nội bộ theo đúng màn hình tenant-admin/documents',
    '[VI]\nMục tiêu:\n- Sử dụng đúng module Document Dashboard thay cho cách gọi Knowledge Base cũ.\n- Hiểu cơ chế phân quyền tài liệu theo phạm vi truy cập.\n- Quản lý danh mục/thẻ để tối ưu truy xuất cho chatbot.\n\nĐường dẫn và cấu trúc màn hình:\n1. Vào /tenant-admin/documents.\n2. Màn hình gồm 3 tab chính:\n   - Documents: tải lên, cập nhật quyền, upload phiên bản mới, xóa mềm/khôi phục.\n   - Categories: quản lý nhóm tài liệu.\n   - Tags: gắn nhãn theo chủ đề để lọc nhanh.\n\nLuồng upload tài liệu chuẩn:\n1. Chọn file hợp lệ.\n2. Chọn category (nếu có).\n3. Chọn tags liên quan.\n4. Chọn phạm vi truy cập (company wide / specific departments / specific roles / departments and roles).\n5. Upload và theo dõi trạng thái embedding (pending/processing/completed/failed).\n\nChecklist sau khi đọc:\n- Tôi biết nơi quản lý Document Dashboard.\n- Tôi hiểu sự khác nhau giữa Documents/Categories/Tags.\n- Tôi biết cách set scope truy cập tài liệu đúng nghiệp vụ.\n\n[EN]\nObjective:\n- Use the standardized term Document Dashboard instead of legacy Knowledge Base wording.\n- Understand document access scope behavior in current implementation.\n- Maintain categories/tags to improve chatbot retrieval quality.\n\nRoute and screen structure:\n1. Open /tenant-admin/documents.\n2. The page contains 3 tabs:\n   - Documents: upload, access update, new version upload, soft delete/restore.\n   - Categories: category management.\n   - Tags: topic labeling for faster filtering.\n\nRecommended upload flow:\n1. Select a supported file.\n2. Assign category (optional).\n3. Assign relevant tags.\n4. Configure visibility scope (company/departments/roles/both).\n5. Upload and monitor embedding status (pending/processing/completed/failed).\n\nAfter-reading checklist:\n- I know where the Document Dashboard is managed.\n- I understand the role of Documents/Categories/Tags.\n- I can configure document visibility correctly for business needs.',
    12,
    3,
    '["DOCUMENT_WRITE"]'::jsonb,
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com')
),
(
    'f1000000-0000-0000-0000-000000000004',
    '550e8400-e29b-41d4-a716-446655440000',
    'Sử dụng AI Chatbot và tìm kiếm tài liệu / Chatbot and Document Retrieval Workflow',
    'Thực hành gửi câu hỏi, dùng bộ lọc category/tag/topK và đọc nguồn tham chiếu trong chatbot',
    '[VI]\nMục tiêu:\n- Chat đúng ngữ cảnh để nhận phản hồi chính xác hơn.\n- Biết dùng bộ lọc RAG để thu hẹp tài liệu theo nghiệp vụ.\n- Biết đọc references trước khi áp dụng kết quả AI.\n\nLuồng thao tác tại /chatbot:\n1. Mở chatbot từ sidebar hoặc quick action.\n2. Thiết lập bộ lọc RAG ở đầu trang chat:\n   - Category: chọn nhóm tài liệu liên quan\n   - Top K: số lượng đoạn tài liệu truy xuất\n   - Tags: chọn nhãn chủ đề\n3. Nhập câu hỏi rõ ràng ở khung chat và gửi.\n4. Đọc câu trả lời + phần references (documentName, excerpt, confidence).\n5. Nếu cần truy vấn khác ngữ cảnh, chọn New Chat hoặc mở lịch sử hội thoại để xem lại.\n\nMẹo để tăng chất lượng phản hồi:\n- Nêu rõ phòng ban/ngữ cảnh khi hỏi.\n- Nếu câu hỏi chuyên sâu, tăng Top K ở mức hợp lý.\n- Khi thấy độ tin cậy thấp, kiểm tra lại bằng tài liệu gốc trước khi ra quyết định.\n\nChecklist sau khi đọc:\n- Tôi biết cách cấu hình Category/Tags/Top K.\n- Tôi biết đọc references trong phản hồi chatbot.\n- Tôi biết khi nào cần mở chat mới để tránh nhiễu ngữ cảnh.\n\n[EN]\nObjective:\n- Ask context-rich questions for better response quality.\n- Use RAG filters to narrow retrieval scope.\n- Validate references before applying AI output.\n\nWorkflow on /chatbot:\n1. Open chatbot from navigation/quick action.\n2. Configure RAG filters:\n   - Category\n   - Top K\n   - Tags\n3. Send a clear prompt.\n4. Review answer plus references (document name, excerpt, confidence).\n5. Use New Chat or history when context switching is needed.\n\nQuality tips:\n- Include department/business context in prompts.\n- Increase Top K for deeper retrieval when necessary.\n- Re-check source documents when confidence appears low.\n\nAfter-reading checklist:\n- I can configure Category/Tags/Top K correctly.\n- I can interpret references in chatbot answers.\n- I know when to start a new chat to avoid context drift.',
    11,
    4,
    '["DOCUMENT_READ"]'::jsonb,
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com')
),
(
    'f1000000-0000-0000-0000-000000000005',
    '550e8400-e29b-41d4-a716-446655440000',
    'Tenant Admin: vận hành tổ chức và phân quyền / Tenant Admin Operations and Access Control',
    'Tập trung vào các tác vụ quản trị tenant cốt lõi: nhân sự, phòng ban, vai trò, phân quyền theo nghiệp vụ',
    '[VI]\nMục tiêu:\n- Quản trị người dùng theo cấu trúc tổ chức rõ ràng.\n- Thiết lập vai trò/permission phù hợp cho từng bộ phận.\n- Đảm bảo tính nhất quán giữa role và phạm vi dữ liệu truy cập.\n\nLuồng vận hành chuẩn cho TENANT_ADMIN:\n1. Quản lý nhân sự tại /tenant-admin/employees:\n   - Tạo user mới\n   - Cập nhật thông tin\n   - Cập nhật quyền bổ sung\n   - Vô hiệu hóa/kích hoạt tài khoản\n2. Quản lý phòng ban tại /tenant-admin/departments:\n   - Tạo/cập nhật phòng ban\n   - Phân bổ nhân viên\n3. Quản lý vai trò tại /tenant-admin/roles:\n   - Dùng fixed roles đúng mục đích\n   - Tạo custom role khi cần quyền đặc thù\n   - Chỉ gán permission cần thiết (least privilege)\n\nNguyên tắc phân quyền:\n- Ưu tiên role rõ chức năng thay vì cấp quyền tràn lan cho từng user.\n- Với tài liệu và chatbot, luôn kiểm tra role có permission tương ứng trước khi hỗ trợ user.\n\nChecklist sau khi đọc:\n- Tôi biết nơi thao tác user/department/role.\n- Tôi hiểu cách phối hợp fixed role và custom role.\n- Tôi biết nguyên tắc cấp quyền tối thiểu.\n\n[EN]\nObjective:\n- Operate tenant administration with a clear organizational model.\n- Configure roles/permissions per department responsibility.\n- Keep role assignment consistent with data access scope.\n\nOperational flow for TENANT_ADMIN:\n1. Manage employees at /tenant-admin/employees:\n   - Create users\n   - Update profile data\n   - Adjust additional permissions\n   - Activate/deactivate accounts\n2. Manage departments at /tenant-admin/departments:\n   - Create/update departments\n   - Assign employees\n3. Manage roles at /tenant-admin/roles:\n   - Use fixed roles appropriately\n   - Create custom roles for specific needs\n   - Apply least-privilege permission assignment\n\nAccess-control principles:\n- Prefer role-based control over ad-hoc per-user permission sprawl.\n- For documents/chatbot access, always verify permission alignment before granting guidance.\n\nAfter-reading checklist:\n- I know where to manage users/departments/roles.\n- I understand fixed vs custom role usage.\n- I can apply least-privilege principles.',
    12,
    5,
    '["USER_WRITE"]'::jsonb,
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com')
),
(
    'f1000000-0000-0000-0000-000000000006',
    '550e8400-e29b-41d4-a716-446655440000',
    'Tenant Admin: chọn gói và thanh toán / Tenant Admin: Plan Purchase and Payment Flow',
    'Hướng dẫn đầy đủ cách chọn tier, chu kỳ thanh toán, quét QR/chuyển khoản và theo dõi trạng thái giao dịch',
    '[VI]\nMục tiêu:\n- Chọn đúng gói subscription theo quy mô tổ chức.\n- Thực hiện đúng luồng thanh toán đang triển khai trong hệ thống.\n- Theo dõi lịch sử thanh toán và trạng thái sau khi thanh toán thành công.\n\nLuồng thao tác tại /tenant-admin/subscription:\n1. Mở tab Plans để xem gói hiện tại và khu vực tạo thanh toán.\n2. Chọn Tier (TRIAL/STARTER/STANDARD/ENTERPRISE).\n3. Chọn Billing Cycle (MONTHLY/QUARTERLY/YEARLY).\n4. Nhấn Create payment để tạo giao dịch.\n5. Ở khối chờ thanh toán:\n   - Quét mã QR hoặc chuyển khoản thủ công theo thông tin ngân hàng\n   - Theo dõi trạng thái polling đến khi SUCCESS\n6. Kiểm tra lại thông tin gói sau khi thanh toán thành công.\n7. Vào tab History để kiểm tra lịch sử giao dịch.\n\nLưu ý vận hành:\n- Nếu tổ chức đang có gói trả phí active, cần xử lý theo điều kiện hệ thống trước khi tạo gói mới.\n- Luôn đối chiếu transaction_code khi làm việc với bộ phận kế toán/hỗ trợ.\n\nChecklist sau khi đọc:\n- Tôi biết vị trí và ý nghĩa từng tab trong trang subscription.\n- Tôi biết các bước tạo payment và xác nhận thành công.\n- Tôi biết nơi kiểm tra lịch sử giao dịch.\n\n[EN]\nObjective:\n- Select the proper subscription tier for tenant scale.\n- Follow the implemented payment flow correctly.\n- Verify payment status and billing history after success.\n\nWorkflow at /tenant-admin/subscription:\n1. Open Plans tab to view current plan and payment section.\n2. Select Tier (TRIAL/STARTER/STANDARD/ENTERPRISE).\n3. Select Billing Cycle (MONTHLY/QUARTERLY/YEARLY).\n4. Click Create payment.\n5. In pending payment section:\n   - Scan QR or perform manual bank transfer\n   - Wait for polling status until SUCCESS\n6. Re-check plan details after successful payment.\n7. Open History tab for transaction history.\n\nOperational notes:\n- If a paid active plan exists, follow system constraints before creating another payment.\n- Always keep transaction_code for finance/support reconciliation.\n\nAfter-reading checklist:\n- I understand each tab in the subscription page.\n- I can complete payment creation and success verification.\n- I know where to audit transaction history.',
    10,
    6,
    '["SUBSCRIPTION_MANAGE"]'::jsonb,
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com')
);

INSERT INTO subscription_plans (
    subscription_plan_id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model,
    is_active, display_order,
    features, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000001', 'TRIAL', 'Gói Dùng Thử', 'Gói dùng thử miễn phí 14 ngày để trải nghiệm hệ thống',
    0, 0, 0, 'VND',
    5, 100, 5, 1000,
    500, 50, 10000,
    4096, 512,
    'ts/gemini-2.5-flash', 'tse/gemini/gemini-embedding-001',
    true, 0,
    '✅ 5 users, ✅ 100 documents, ✅ 5GB storage, ✅ 1,000 API calls/month, ✅ Basic AI chatbot',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO subscription_plans (
    subscription_plan_id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model,
    is_active, display_order,
    features, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000002', 'STARTER', 'Gói Khởi Đầu', 'Phù hợp cho doanh nghiệp nhỏ và startup',
    5000, 13500, 48000, 'VND',
    10, 500, 10, 5000,
    2000, 200, 50000,
    8192, 512,
    'ts/gemini-2.5-flash', 'tse/gemini/gemini-embedding-001',
    true, 1,
    '✅ 10 users, ✅ 500 documents, ✅ 10GB storage, ✅ 5,000 API calls/month, ✅ RAG enabled, ✅ Priority support',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO subscription_plans (
    subscription_plan_id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model,
    is_active, display_order,
    features, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000003', 'STANDARD', 'Gói Tiêu Chuẩn', 'Phù hợp cho doanh nghiệp vừa',
    10000, 27000, 96000, 'VND',
    50, 2000, 50, 20000,
    10000, 1000, 200000,
    16384, 1024,
    'ts/gemini-2.5-flash', 'tse/gemini/gemini-embedding-001',
    true, 2,
    '✅ 50 users, ✅ 2,000 documents, ✅ 50GB storage, ✅ 20,000 API calls/month, ✅ Gemini 2.5 Flash, ✅ Advanced RAG, ✅ 24/7 support',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO subscription_plans (
    subscription_plan_id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model,
    is_active, display_order,
    features, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000004', 'ENTERPRISE', 'Gói Doanh Nghiệp', 'Giải pháp toàn diện cho doanh nghiệp lớn',
    20000, 54000, 192000, 'VND',
    999, 999999, 999999, 999999,
    999999, 999999, 999999,
    32768, 2048,
    'ts/gemini-3.1-pro', 'tse/gemini/gemini-embedding-001',
    true, 3,
    '✅ Unlimited users, ✅ Unlimited documents, ✅ Scalable storage (dedicated VPS), ✅ Unlimited API calls, ✅ Gemini 3.1 Pro, ✅ Advanced RAG, ✅ Dedicated support, ✅ Custom integration, ✅ Dedicated database (data isolation)',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO subscriptions (
    subscription_id,
    tenant_id,
    plan_id,
    tier,
    status,
    start_date,
    end_date,
    price,
    currency,
    billing_cycle,
    next_billing_date,
    auto_renew,
    is_trial,
    max_users,
    max_documents,
    max_storage_gb,
    max_api_calls,
    max_chatbot_requests,
    max_rag_documents,
    max_ai_tokens,
    context_window_tokens,
    rag_chunk_size,
    ai_model,
    embedding_model,
    payment_gateway,
    created_at
) VALUES (
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    'a0000000-0000-0000-0000-000000000004', -- ENTERPRISE plan
    'ENTERPRISE',
    'ACTIVE',
    CURRENT_TIMESTAMP - interval '5 days',
    CURRENT_TIMESTAMP + interval '360 days',
    192000.00,
    'VND',
    'YEARLY',
    CURRENT_TIMESTAMP + interval '360 days',
    TRUE,
    FALSE,
    999, 999999, 999999, 999999,
    999999, 999999, 999999,
    32768, 2048,
    'ts/gemini-3.1-pro', 'tse/gemini/gemini-embedding-001',
    'SEPAY',
    CURRENT_TIMESTAMP - interval '5 days'
);

INSERT INTO payment_transactions (
    payment_transaction_id, subscription_id, tenant_id, amount, currency, transaction_code, tier, gateway,
    gateway_transaction_id, status, created_at, paid_at, created_by, notes, is_auto_renewal
) VALUES
(
    'd1000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    45000.00, 'USD', 'TXN-202601-001', 'ENTERPRISE', 'SEPAY',
    'GW-202601-001', 'SUCCESS',
    '2026-01-05 09:00:00', '2026-01-05 09:10:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Jan #1', FALSE
),
(
    'd1000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    52000.00, 'USD', 'TXN-202602-001', 'ENTERPRISE', 'SEPAY',
    'GW-202602-001', 'SUCCESS',
    '2026-02-10 10:00:00', '2026-02-10 10:20:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Feb #1', FALSE
),
(
    'd1000000-0000-0000-0000-000000000003',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    61000.00, 'USD', 'TXN-202603-001', 'ENTERPRISE', 'SEPAY',
    'GW-202603-001', 'SUCCESS',
    '2026-03-15 14:00:00', '2026-03-15 14:35:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Mar #1', FALSE
),
(
    'd1000000-0000-0000-0000-000000000004',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    30000.00, 'USD', 'TXN-202603-FAILED', 'ENTERPRISE', 'SEPAY',
    'GW-202603-FAILED', 'FAILED',
    '2026-03-18 11:00:00', NULL,
    (SELECT user_id FROM users WHERE email = 'staff@system.com'),
    'Should not be counted in revenue', FALSE
),
(
    'd1000000-0000-0000-0000-000000000005',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    70000.00, 'USD', 'TXN-202604-001', 'ENTERPRISE', 'SEPAY',
    'GW-202604-001', 'SUCCESS',
    '2026-04-02 08:00:00', '2026-04-02 08:05:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Apr #1', TRUE
),
(
    'd1000000-0000-0000-0000-000000000006',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    40000.00, 'USD', 'TXN-202605-CANCEL', 'ENTERPRISE', 'SEPAY',
    'GW-202605-CANCEL', 'CANCELLED',
    '2026-05-01 08:00:00', NULL,
    (SELECT user_id FROM users WHERE email = 'staff@system.com'),
    'Cancelled payment test row', FALSE
),
(
    'd1000000-0000-0000-0000-000000000007',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    38000.00, 'USD', 'TXN-202510-001', 'ENTERPRISE', 'SEPAY',
    'GW-202510-001', 'SUCCESS',
    '2025-10-10 09:00:00', '2025-10-10 09:05:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Oct 2025', FALSE
),
(
    'd1000000-0000-0000-0000-000000000008',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    41000.00, 'USD', 'TXN-202511-001', 'ENTERPRISE', 'SEPAY',
    'GW-202511-001', 'SUCCESS',
    '2025-11-12 10:00:00', '2025-11-12 10:15:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Nov 2025', FALSE
),
(
    'd1000000-0000-0000-0000-000000000009',
    'b0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    39500.00, 'USD', 'TXN-202512-001', 'ENTERPRISE', 'SEPAY',
    'GW-202512-001', 'SUCCESS',
    '2025-12-05 14:00:00', '2025-12-05 14:20:00',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'Revenue seed Dec 2025', FALSE
);

INSERT INTO audit_logs (
    audit_log_id, tenant_id, user_id, user_email, user_role,
    action, entity_type, entity_id, old_value, new_value, description,
    ip_address, user_agent, status, created_at
) VALUES
(
    'e1000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440000',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'superadmin@system.com', 'SUPER_ADMIN',
    'TENANT_CREATED', 'Tenant', '550e8400-e29b-41d4-a716-446655440000',
    '{}'::jsonb, '{"tenantName":"FPT Software"}'::jsonb,
    'Company ABC has been added to the platform',
    '1.2.3.4', 'seed-script', 'SUCCESS', CURRENT_TIMESTAMP - interval '10 minutes'
),
(
    'e1000000-0000-0000-0000-000000000002',
    '550e8400-e29b-41d4-a716-446655440000',
    (SELECT user_id FROM users WHERE email = 'staff@system.com'),
    'staff@system.com', 'STAFF',
    'TENANT_STATUS_CHANGED', 'Tenant', '550e8400-e29b-41d4-a716-446655440000',
    '{"status":"PENDING"}'::jsonb, '{"status":"ACTIVE"}'::jsonb,
    'Tenant FPT Software has been approved',
    '1.2.3.5', 'seed-script', 'SUCCESS', CURRENT_TIMESTAMP - interval '30 minutes'
),
(
    'e1000000-0000-0000-0000-000000000003',
    NULL, NULL, NULL, NULL,
    'SYSTEM_WARNING', 'System', 'server-3',
    '{}'::jsonb, '{"cpuPercent":92}'::jsonb,
    'High CPU usage on server 3',
    NULL, 'monitor-agent', 'SUCCESS', CURRENT_TIMESTAMP - interval '60 minutes'
),
(
    'e1000000-0000-0000-0000-000000000004',
    '550e8400-e29b-41d4-a716-446655440000',
    (SELECT user_id FROM users WHERE email = 'superadmin@system.com'),
    'superadmin@system.com', 'SUPER_ADMIN',
    'SUBSCRIPTION_RENEWED', 'Subscription', 'b0000000-0000-0000-0000-000000000001',
    '{"tier":"STANDARD"}'::jsonb, '{"tier":"ENTERPRISE"}'::jsonb,
    'Subscription upgraded to ENTERPRISE',
    '1.2.3.4', 'seed-script', 'SUCCESS', CURRENT_TIMESTAMP - interval '2 hours'
),
(
    'e1000000-0000-0000-0000-000000000005',
    '550e8400-e29b-41d4-a716-446655440000',
    (SELECT user_id FROM users WHERE email = 'staff@system.com'),
    'staff@system.com', 'STAFF',
    'PAYMENT_FAILED', 'PaymentTransaction', 'd1000000-0000-0000-0000-000000000004',
    '{}'::jsonb, '{"transactionCode":"TXN-202603-FAILED"}'::jsonb,
    'Payment TXN-202603-FAILED failed verification',
    '1.2.3.6', 'seed-script', 'FAILED', CURRENT_TIMESTAMP - interval '3 hours'
),
(
    'e1000000-0000-0000-0000-000000000006',
    '550e8400-e29b-41d4-a716-446655440000',
    (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
    'admin@fpt.com', 'TENANT_ADMIN',
    'DOCUMENT_UPLOADED', 'Document', 'doc-seed-001',
    '{}'::jsonb, '{"fileName":"employee-handbook.pdf"}'::jsonb,
    'Document employee-handbook.pdf uploaded',
    '1.2.3.7', 'seed-script', 'SUCCESS', CURRENT_TIMESTAMP - interval '5 hours'
);

INSERT INTO document_categories (category_id, tenant_id, parent_id, name, code, description, is_active, created_by, created_at) VALUES

-- Root categories
('c1000000-0000-0000-0000-000000000001',
 '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'Chính sách & Quy định',
 'POLICY',
 'Tài liệu về chính sách nội bộ, quy định công ty, nội quy lao động',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '10 days'),

('c2000000-0000-0000-0000-000000000002',
 '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'Kỹ thuật & Công nghệ',
 'TECH',
 'Tài liệu kỹ thuật, kiến trúc hệ thống, hướng dẫn phát triển',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '10 days'),

('c3000000-0000-0000-0000-000000000003',
 '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'Nhân sự & Đào tạo',
 'HR',
 'Tài liệu liên quan đến nhân sự, tuyển dụng, onboarding, đào tạo nội bộ',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '10 days'),

('c4000000-0000-0000-0000-000000000004',
 '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'Tài chính & Kế toán',
 'FINANCE',
 'Báo cáo tài chính, hướng dẫn thanh toán, chính sách chi phí',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '10 days'),

-- Sub-categories of POLICY
('c1100000-0000-0000-0000-000000000011',
 '550e8400-e29b-41d4-a716-446655440000',
 'c1000000-0000-0000-0000-000000000001',
 'Chính sách nhân sự',
 'POLICY_HR',
 'Nghỉ phép, phúc lợi, lương thưởng, đánh giá hiệu suất',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '9 days'),

('c1200000-0000-0000-0000-000000000012',
 '550e8400-e29b-41d4-a716-446655440000',
 'c1000000-0000-0000-0000-000000000001',
 'Quy định hành chính',
 'POLICY_ADMIN',
 'Quy định về giờ làm, trang phục, sử dụng tài sản công ty',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '9 days'),

-- Sub-categories of TECH
('c2100000-0000-0000-0000-000000000021',
 '550e8400-e29b-41d4-a716-446655440000',
 'c2000000-0000-0000-0000-000000000002',
 'Kiến trúc hệ thống',
 'TECH_ARCH',
 'System design, architecture diagrams, ADR documents',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '8 days'),

('c2200000-0000-0000-0000-000000000022',
 '550e8400-e29b-41d4-a716-446655440000',
 'c2000000-0000-0000-0000-000000000002',
 'Hướng dẫn phát triển',
 'TECH_DEV',
 'Coding standards, git workflow, code review guidelines, CI/CD',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '8 days'),

-- Sub-category of HR
('c3100000-0000-0000-0000-000000000031',
 '550e8400-e29b-41d4-a716-446655440000',
 'c3000000-0000-0000-0000-000000000003',
 'Onboarding',
 'HR_ONBOARDING',
 'Tài liệu dành cho nhân viên mới: quy trình nhận việc, giới thiệu công ty',
 TRUE,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '7 days');

INSERT INTO documents (
    document_id, file_name, original_file_name, file_type, file_size,
    storage_path, tenant_id, description, visibility, minimum_role_level,
    owner_department_id, accessible_departments, accessible_roles,
    uploaded_by,
    uploaded_at, embedding_status, is_active,
    category_id, document_title
) VALUES

-- 1. Nội quy công ty - COMPANY_WIDE
('d1000000-0000-0000-0000-000000000001',
 'noi_quy_cong_ty_2026.pdf', 'Nội quy công ty 2026.pdf', 'application/pdf', 204800,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/noi_quy_cong_ty_2026.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Nội quy lao động, quy định về giờ giấc, ứng xử, trang phục và kỷ luật',
 'COMPANY_WIDE', 4,
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '8 days',
 'PENDING', TRUE,
 'c1200000-0000-0000-0000-000000000012', 'Nội quy công ty 2026'),

-- 2. Hướng dẫn onboarding - COMPANY_WIDE
('d2000000-0000-0000-0000-000000000002',
 'huong_dan_onboarding.pdf', 'Hướng dẫn Onboarding nhân viên mới.pdf', 'application/pdf', 512000,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/huong_dan_onboarding.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Hướng dẫn từng bước cho nhân viên mới: đăng ký hệ thống, làm quen môi trường làm việc, các đầu mối liên hệ',
 'COMPANY_WIDE', 4,
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '7 days',
 'PENDING', TRUE,
 'c3100000-0000-0000-0000-000000000031', 'Hướng dẫn Onboarding nhân viên mới'),

-- 3. Chính sách nghỉ phép - COMPANY_WIDE
('d3000000-0000-0000-0000-000000000003',
 'chinh_sach_nghi_phep_2026.pdf', 'Chính sách nghỉ phép 2026.pdf', 'application/pdf', 153600,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/chinh_sach_nghi_phep_2026.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Quy định ngày phép năm, phép ốm, phép thai sản và các loại phép đặc biệt',
 'COMPANY_WIDE', 4,
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '6 days',
 'PENDING', TRUE,
 'c1100000-0000-0000-0000-000000000011', 'Chính sách nghỉ phép 2026'),

-- 4. Kiến trúc hệ thống nội bộ - chỉ DEV department
('d4000000-0000-0000-0000-000000000004',
 'system_architecture_v2.pdf', 'System Architecture v2.0.pdf', 'application/pdf', 1048576,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/system_architecture_v2.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Tài liệu kiến trúc hệ thống nội bộ phiên bản 2.0: microservices, database schema, API contracts',
 'SPECIFIC_DEPARTMENTS', 3,
 (SELECT department_id FROM departments WHERE tenant_id = '550e8400-e29b-41d4-a716-446655440000' AND code = 'DEV'),
 (SELECT jsonb_agg(department_id) FROM departments WHERE tenant_id = '550e8400-e29b-41d4-a716-446655440000' AND code = 'DEV'),
 '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '5 days',
 'PENDING', TRUE,
 'c2100000-0000-0000-0000-000000000021', 'System Architecture v2.0'),

-- 5. Coding Standards - COMPANY_WIDE
('d5000000-0000-0000-0000-000000000005',
 'coding_standards_java.pdf', 'Coding Standards - Java & Spring Boot.pdf', 'application/pdf', 307200,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/coding_standards_java.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Tiêu chuẩn code Java và Spring Boot: đặt tên, cấu trúc package, xử lý lỗi, logging',
 'COMPANY_WIDE', 4,
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '4 days',
 'PENDING', TRUE,
 'c2200000-0000-0000-0000-000000000022', 'Coding Standards - Java & Spring Boot');

INSERT INTO chat_sessions (
    session_id, tenant_id, user_id, title, status, started_at, ended_at, last_message_at,
    total_messages, total_tokens_used, created_at, updated_at
) VALUES
('c1000000-0000-0000-0000-000000000001',
 '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'employee1@fpt.com'),
 'Hỏi về quy định nghỉ phép',
 'ENDED',
 CURRENT_TIMESTAMP - interval '3 days',
 CURRENT_TIMESTAMP - interval '3 days' + interval '20 minutes',
 CURRENT_TIMESTAMP - interval '3 days' + interval '20 minutes',
 4, 1180, CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP - interval '3 days'),
('c1000000-0000-0000-0000-000000000002',
 '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'employee2@fpt.com'),
 'Onboarding checklist',
 'ENDED',
 CURRENT_TIMESTAMP - interval '2 days',
 CURRENT_TIMESTAMP - interval '2 days' + interval '18 minutes',
 CURRENT_TIMESTAMP - interval '2 days' + interval '18 minutes',
 4, 980, CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP - interval '2 days'),
('c1000000-0000-0000-0000-000000000003',
 '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 'Coding standards cho dự án mới',
 'ACTIVE',
 CURRENT_TIMESTAMP - interval '8 hours',
 NULL,
 CURRENT_TIMESTAMP - interval '7 hours',
 2, 620, CURRENT_TIMESTAMP - interval '8 hours', CURRENT_TIMESTAMP - interval '7 hours');

INSERT INTO chat_messages (
    message_id, session_id, tenant_id, user_id, role, content, source_chunks, tokens_used,
    rating, feedback_text, rated_at, created_at
) VALUES
('c2000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'employee1@fpt.com'),
 'USER', 'Mỗi năm em được bao nhiêu ngày phép?', '[]'::jsonb, 90, NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '3 days' + interval '1 minutes'),
('c2000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000001', '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'ASSISTANT', 'Theo chính sách hiện tại, nhân viên chính thức có 12 ngày phép năm.', '[]'::jsonb, 290,
 5, 'Rất rõ ràng', CURRENT_TIMESTAMP - interval '3 days' + interval '3 minutes', CURRENT_TIMESTAMP - interval '3 days' + interval '3 minutes'),
('c2000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000001', '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'employee1@fpt.com'),
 'USER', 'Nghỉ ốm cần thủ tục gì?', '[]'::jsonb, 95, NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '3 days' + interval '5 minutes'),
('c2000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000001', '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'ASSISTANT', 'Nghỉ ốm trên 2 ngày cần nộp giấy xác nhận y tế cho quản lý.', '[]'::jsonb, 305,
 NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '3 days' + interval '7 minutes'),
('c2000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000002', '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'employee2@fpt.com'),
 'USER', 'Onboarding tuần đầu cần làm gì?', '[]'::jsonb, 82, NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '2 days' + interval '1 minutes'),
('c2000000-0000-0000-0000-000000000006', 'c1000000-0000-0000-0000-000000000002', '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'ASSISTANT', 'Bạn cần hoàn tất tài khoản Jira, GitLab, email công ty và chấm công.', '[]'::jsonb, 260,
 4, 'Hữu ích', CURRENT_TIMESTAMP - interval '2 days' + interval '4 minutes', CURRENT_TIMESTAMP - interval '2 days' + interval '4 minutes'),
('c2000000-0000-0000-0000-000000000007', 'c1000000-0000-0000-0000-000000000002', '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'employee2@fpt.com'),
 'USER', 'Checklist phải xong trong bao lâu?', '[]'::jsonb, 74, NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '2 days' + interval '7 minutes'),
('c2000000-0000-0000-0000-000000000008', 'c1000000-0000-0000-0000-000000000002', '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'ASSISTANT', 'Checklist onboarding cần hoàn tất trong 5 ngày làm việc.', '[]'::jsonb, 245,
 NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '2 days' + interval '9 minutes'),
('c2000000-0000-0000-0000-000000000009', 'c1000000-0000-0000-0000-000000000003', '550e8400-e29b-41d4-a716-446655440000',
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 'USER', 'Cho tôi tiêu chuẩn code backend chính?', '[]'::jsonb, 88, NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '8 hours' + interval '3 minutes'),
('c2000000-0000-0000-0000-00000000000a', 'c1000000-0000-0000-0000-000000000003', '550e8400-e29b-41d4-a716-446655440000',
 NULL,
 'ASSISTANT', 'Ưu tiên clean architecture, response chuẩn hóa và log correlation-id.', '[]'::jsonb, 280,
 NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '7 hours');
