-------------------------------------------------------
-- 1. KHỞI TẠO CẤU TRÚC (TRÁNH LỖI RELATION NOT EXIST)
-------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";  -- pgvector for embeddings

-- Xóa bảng cũ nếu đã tồn tại để reset dữ liệu
DROP TABLE IF EXISTS department_transfer_requests CASCADE;
DROP TABLE IF EXISTS subscription_plans CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS chat_sessions CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS blacklisted_tokens CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;

-- Xóa sequences (không CASCADE vì sẽ tạo lại ngay sau đó)
DROP SEQUENCE IF EXISTS refresh_tokens_seq;
DROP SEQUENCE IF EXISTS blacklisted_tokens_seq;
DROP SEQUENCE IF EXISTS roles_id_seq;
DROP SEQUENCE IF EXISTS departments_id_seq;

-- Tạo lại sequences với INCREMENT BY 50 (Hibernate default allocationSize)
CREATE SEQUENCE refresh_tokens_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE blacklisted_tokens_seq START WITH 1 INCREMENT BY 50;

-- Tạo bảng Tenants (Organizations)
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Company Information
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    website VARCHAR(255),
    industry VARCHAR(100),
    company_size VARCHAR(50),
    
    -- Representative Information
    contact_email VARCHAR(255) NOT NULL UNIQUE,
    representative_name VARCHAR(100),
    representative_position VARCHAR(100),
    representative_phone VARCHAR(20),
    
    -- Request Information
    request_message TEXT,
    requested_at TIMESTAMP,
    
    -- Approval Information
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    
    -- Subscription Information
    subscription_tier VARCHAR(50) DEFAULT 'TRIAL',
    subscription_start_date TIMESTAMP,
    subscription_end_date TIMESTAMP,
    
    -- Audit Fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Tạo bảng Roles
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    role_type VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    permissions JSONB DEFAULT '[]'::jsonb,
    created_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Unique index cho system/fixed roles (tenant_id IS NULL)
CREATE UNIQUE INDEX idx_roles_code_system ON roles(code) WHERE tenant_id IS NULL;

-- Unique index cho custom roles (tenant_id IS NOT NULL)
CREATE UNIQUE INDEX idx_roles_code_tenant ON roles(code, tenant_id) WHERE tenant_id IS NOT NULL;

-- Tạo bảng Departments
CREATE TABLE departments (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_department_id INTEGER REFERENCES departments(id),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(tenant_id, code)
);

-- Tạo bảng Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL, -- Email đăng nhập - UNIQUE GLOBALLY
    contact_email VARCHAR(255) UNIQUE, -- Email thật để nhận thông báo (đã verify khi lưu vào DB)
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255), -- Họ tên đầy đủ
    phone_number VARCHAR(20), -- Số điện thoại
    employee_code VARCHAR(50) UNIQUE, -- Mã nhân viên
    date_of_birth DATE, -- Ngày sinh
    address VARCHAR(500), -- Địa chỉ
    role_id INTEGER NOT NULL REFERENCES roles(id),
    department_id INTEGER REFERENCES departments(id),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    reset_password_token VARCHAR(255),
    token_expiry TIMESTAMP,
    must_change_password BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    last_login_at TIMESTAMP
);

-- Tạo bảng Refresh Tokens
CREATE TABLE refresh_tokens (
    id SERIAL PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL
);

-- Tạo bảng Blacklist (Cho Logout)
CREATE TABLE blacklisted_tokens (
    id SERIAL PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL
);

-- Tạo bảng Department Transfer Requests
CREATE TABLE department_transfer_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    current_department_id VARCHAR(100),
    requested_department_id VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    review_note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Tạo bảng Documents (Knowledge Base)
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Basic Info
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    
    -- Tenant & Category
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category VARCHAR(100),
    description VARCHAR(1000),
    
    -- Access Control
    visibility VARCHAR(30) NOT NULL DEFAULT 'COMPANY_WIDE',
    owner_department_id INTEGER REFERENCES departments(id),
    accessible_departments JSONB,
    accessible_roles JSONB,
    
    -- Upload History (Audit Trail)
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_by_name VARCHAR(200) NOT NULL,
    uploaded_by_email VARCHAR(255) NOT NULL,
    uploaded_by_role VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Update History
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,
    
    -- Vector DB Info
    vector_db_id VARCHAR(200),
    embedding_status VARCHAR(20) DEFAULT 'PENDING',
    chunk_count INTEGER,
    embedding_model VARCHAR(100),
    embedding_error VARCHAR(1000),
    
    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_by UUID REFERENCES users(id),
    deleted_at TIMESTAMP,
    
    -- Usage Stats
    view_count BIGINT DEFAULT 0,
    last_accessed_at TIMESTAMP
);

-- Indexes for documents
CREATE INDEX IF NOT EXISTS idx_documents_tenant_visibility ON documents(tenant_id, visibility);
CREATE INDEX IF NOT EXISTS idx_documents_uploaded_at ON documents(uploaded_at);
CREATE INDEX IF NOT EXISTS idx_documents_embedding_status ON documents(embedding_status) WHERE is_active = true;

-- Tạo bảng Document Chunks với Vector Embeddings (pgvector)
CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    
    -- Chunk Info
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    
    -- Vector Embedding (pgvector extension)
    embedding vector(768),  -- 768 dimensions (reduced from 3072 via outputDimensionality for HNSW index support)
    embedding_model VARCHAR(100),
    
    -- Denormalized Access Control (copied from parent document for performance)
    visibility VARCHAR(30),
    accessible_departments JSONB,
    accessible_roles JSONB,
    owner_department_id INTEGER,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(document_id, chunk_index)
);

-- Indexes for vector similarity search
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_tenant ON document_chunks(tenant_id);

-- HNSW index for fast similarity search (pgvector)
-- Using cosine distance (<->) which is standard for embeddings
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine ON document_chunks 
USING hnsw (embedding vector_cosine_ops);

-- Alternative: IVFFlat index (faster to build, slightly slower search)
-- CREATE INDEX idx_chunks_embedding_ivfflat ON document_chunks 
-- USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Tạo bảng Subscription Plans (Super Admin manages plan templates)
CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    
    -- Pricing
    monthly_price DECIMAL(15, 2) NOT NULL,
    quarterly_price DECIMAL(15, 2) NOT NULL,
    yearly_price DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'VND',
    
    -- Usage Limits
    max_users INTEGER NOT NULL,
    max_documents INTEGER NOT NULL,
    max_storage_gb INTEGER NOT NULL,
    max_api_calls INTEGER NOT NULL,
    max_chatbot_requests INTEGER NOT NULL,
    max_rag_documents INTEGER NOT NULL,
    max_ai_tokens INTEGER NOT NULL,
    context_window_tokens INTEGER NOT NULL,
    rag_chunk_size INTEGER NOT NULL,
    
    -- AI Configuration
    ai_model VARCHAR(100),
    embedding_model VARCHAR(100),
    enable_rag BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Trial specific
    is_trial BOOLEAN NOT NULL DEFAULT FALSE,
    trial_days INTEGER,
    
    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    features VARCHAR(500),
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID
);

-------------------------------------------------------
-- 2. NẠP DỮ LIỆU MẪU (Password: 123456)
-------------------------------------------------------
-- Thêm Doanh nghiệp mẫu (ACTIVE - Đã được duyệt)
INSERT INTO tenants (
    id, 
    name, 
    address,
    website,
    industry,
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
    subscription_tier,
    subscription_start_date,
    subscription_end_date,
    created_at
) VALUES (
    '550e8400-e29b-41d4-a716-446655440000', 
    'FPT Software', 
    'Tòa nhà FPT, Phố Duy Tân, Cầu Giấy, Hà Nội',
    'https://fpt.com.vn',
    'Information Technology',
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
    'ENTERPRISE',
    CURRENT_TIMESTAMP - interval '5 days',
    CURRENT_TIMESTAMP + interval '355 days',
    CURRENT_TIMESTAMP - interval '7 days'
);

-- Thêm Tenant đang chờ duyệt (PENDING)
INSERT INTO tenants (
    id,
    name,
    address,
    website,
    industry,
    company_size,
    contact_email,
    representative_name,
    representative_position,
    representative_phone,
    request_message,
    requested_at,
    status,
    subscription_tier,
    created_at
) VALUES (
    '660e8400-e29b-41d4-a716-446655440001',
    'VinGroup Corporation',
    '458 Minh Khai, Hai Bà Trưng, Hà Nội',
    'https://vingroup.net',
    'Conglomerate',
    '500+',
    'it.admin@vingroup.net',
    'Trần Thị B',
    'IT Director',
    '+84-24-39749999',
    'VinGroup cần chatbot để quản lý kiến thức nội bộ cho 50,000+ nhân viên',
    CURRENT_TIMESTAMP - interval '2 days',
    'PENDING',
    'TRIAL',
    CURRENT_TIMESTAMP - interval '2 days'
);

-- Thêm Tenant bị từ chối (REJECTED)
INSERT INTO tenants (
    id,
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

-------------------------------------------------------
-- 3. SEED DATA: ROLES
-------------------------------------------------------
-- System Roles (tenant_id = NULL, role_type = 'SYSTEM')
INSERT INTO roles (code, name, description, tenant_id, role_type, permissions) VALUES
('SUPER_ADMIN', 'Super Administrator', 'System administrator with full access to platform', NULL, 'SYSTEM', '["ALL"]'::jsonb),
('STAFF', 'Platform Staff', 'Staff member who approves tenant registrations', NULL, 'SYSTEM', '["TENANT_APPROVE"]'::jsonb);

-- Tenant Fixed Roles (tenant_id = NULL, role_type = 'FIXED')
-- Note: TENANT_ADMIN has DOCUMENT_ALL → Can manage Knowledge Dashboard
--       EMPLOYEE can use chatbot (free for all authenticated users)
--       Custom roles with DOCUMENT_WRITE/DOCUMENT_ALL → Can upload documents
INSERT INTO roles (code, name, description, tenant_id, role_type, permissions) VALUES
('TENANT_ADMIN', 'Tenant Administrator', 'Organization administrator with full tenant access', NULL, 'FIXED', 
 '["USER_ALL", "DEPT_ALL", "ROLE_ALL", "SUBSCRIPTION_MANAGE", "ANALYTICS_VIEW", "DOCUMENT_ALL", "PROFILE_MANAGE"]'::jsonb),
('EMPLOYEE', 'Employee', 'Regular employee with basic access', NULL, 'FIXED', 
 '["PROFILE_MANAGE"]'::jsonb);

-------------------------------------------------------
-- 4. SEED DATA: DEPARTMENTS (FPT University)
-------------------------------------------------------
INSERT INTO departments (tenant_id, code, name, description, is_active) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'ADMINISTRATION', 'Phòng Hành Chính', 'Quản lý hành chính tổng thể', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'KNOWLEDGE', 'Phòng Quản Lý Kiến Thức', 'Quản lý tài liệu và kiến thức', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'DEV', 'Phòng Phát Triển', 'Phát triển phần mềm', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'HR', 'Phòng Nhân Sự', 'Quản lý nhân sự', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'FINANCE', 'Phòng Tài Chính', 'Quản lý tài chính', TRUE),
('550e8400-e29b-41d4-a716-446655440000', 'GOVERNANCE', 'Phòng Quản Trị', 'Quản trị hệ thống', TRUE);

-------------------------------------------------------
-- 5. SEED DATA: USERS (BCrypt hash for password "123456")
-------------------------------------------------------
-- System Users
INSERT INTO users (email, contact_email, password, full_name, phone_number, role_id, department_id, tenant_id)
VALUES 
-- SUPER_ADMIN (role_id = 1, no tenant)
('superadmin@system.com', NULL, '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Super Administrator', '+84-123-456-789', 1, NULL, NULL),

-- STAFF (role_id = 2, no tenant)
('staff@system.com', 'staff@system.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Platform Staff', '+84-987-111-222', 2, NULL, NULL),

-- Tenant Users (FPT Software)
-- TENANT_ADMIN (role_id = 3) - Has DOCUMENT_ALL → Can manage Knowledge Dashboard
('admin@fpt.com', 'fpt.admin.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Tenant Admin', '+84-987-654-321', 3, 1, '550e8400-e29b-41d4-a716-446655440000'),

-- EMPLOYEE (role_id = 4) - Has DOCUMENT_READ → Can only use chatbot
('employee1@fpt.com', 'fpt.employee1.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 1', '+84-901-234-567', 4, 3, '550e8400-e29b-41d4-a716-446655440000'),
('employee2@fpt.com', 'fpt.employee2.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 2', '+84-902-345-678', 4, 4, '550e8400-e29b-41d4-a716-446655440000'),
('employee3@fpt.com', 'fpt.employee3.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 3', '+84-903-456-789', 4, 5, '550e8400-e29b-41d4-a716-446655440000');

-- Thêm User đang chờ Reset Password (OTP: 888888)
INSERT INTO users (email, contact_email, password, full_name, role_id, tenant_id, reset_password_token, token_expiry)
VALUES ('forgot_user@fpt.com', 'forgot.user.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Forgot User', 4, '550e8400-e29b-41d4-a716-446655440000', '888888', CURRENT_TIMESTAMP + interval '15 minutes');

-------------------------------------------------------
-- 6. SEED DATA: SUBSCRIPTION PLANS
-------------------------------------------------------
-- TRIAL Plan (Free 14 days)
INSERT INTO subscription_plans (
    id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model, enable_rag,
    is_trial, trial_days, is_active, display_order,
    features, created_at, updated_at
) VALUES (
    uuid_generate_v4(), 'TRIAL', 'Gói Dùng Thử', 'Gói dùng thử miễn phí 14 ngày để trải nghiệm hệ thống',
    0, 0, 0, 'VND',
    5, 100, 5, 1000,
    500, 50, 10000,
    4096, 512,
    'gpt-3.5-turbo', 'text-embedding-ada-002', false,
    true, 14, true, 0,
    '✅ 5 users, ✅ 100 documents, ✅ 5GB storage, ✅ 1,000 API calls/month, ✅ Basic AI chatbot',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- STARTER Plan
INSERT INTO subscription_plans (
    id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model, enable_rag,
    is_trial, trial_days, is_active, display_order,
    features, created_at, updated_at
) VALUES (
    uuid_generate_v4(), 'STARTER', 'Gói Khởi Đầu', 'Phù hợp cho doanh nghiệp nhỏ và startup',
    5000, 13500, 48000, 'VND',
    10, 500, 10, 5000,
    2000, 200, 50000,
    8192, 512,
    'gpt-3.5-turbo', 'text-embedding-ada-002', true,
    false, null, true, 1,
    '✅ 10 users, ✅ 500 documents, ✅ 10GB storage, ✅ 5,000 API calls/month, ✅ RAG enabled, ✅ Priority support',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- STANDARD Plan
INSERT INTO subscription_plans (
    id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model, enable_rag,
    is_trial, trial_days, is_active, display_order,
    features, created_at, updated_at
) VALUES (
    uuid_generate_v4(), 'STANDARD', 'Gói Tiêu Chuẩn', 'Phù hợp cho doanh nghiệp vừa',
    10000, 27000, 96000, 'VND',
    50, 2000, 50, 20000,
    10000, 1000, 200000,
    16384, 1024,
    'gpt-4', 'text-embedding-ada-002', true,
    false, null, true, 2,
    '✅ 50 users, ✅ 2,000 documents, ✅ 50GB storage, ✅ 20,000 API calls/month, ✅ GPT-4 model, ✅ Advanced RAG, ✅ 24/7 support',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ENTERPRISE Plan
INSERT INTO subscription_plans (
    id, code, name, description,
    monthly_price, quarterly_price, yearly_price, currency,
    max_users, max_documents, max_storage_gb, max_api_calls,
    max_chatbot_requests, max_rag_documents, max_ai_tokens,
    context_window_tokens, rag_chunk_size,
    ai_model, embedding_model, enable_rag,
    is_trial, trial_days, is_active, display_order,
    features, created_at, updated_at
) VALUES (
    uuid_generate_v4(), 'ENTERPRISE', 'Gói Doanh Nghiệp', 'Giải pháp toàn diện cho doanh nghiệp lớn',
    20000, 54000, 192000, 'VND',
    999, 999999, 500, 999999,
    999999, 999999, 999999,
    32768, 2048,
    'gpt-4', 'text-embedding-ada-002', true,
    false, null, true, 3,
    '✅ Unlimited users, ✅ Unlimited documents, ✅ 500GB storage, ✅ Unlimited API calls, ✅ GPT-4 model, ✅ Advanced RAG, ✅ Dedicated support, ✅ Custom integration',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);