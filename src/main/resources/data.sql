-------------------------------------------------------
-- 1. KHỞI TẠO CẤU TRÚC (TRÁNH LỖI RELATION NOT EXIST)
-------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";  -- pgvector for embeddings

-- Xóa bảng cũ nếu đã tồn tại để reset dữ liệu
-- Xóa bảng con trước để tránh conflict khi DROP bảng cha
DROP TABLE IF EXISTS document_versions CASCADE;
DROP TABLE IF EXISTS document_chunks CASCADE;
DROP TABLE IF EXISTS document_tag_mappings CASCADE;
DROP TABLE IF EXISTS document_tags CASCADE;
DROP TABLE IF EXISTS payment_transactions CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS subscription_plans CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS onboarding_progress CASCADE;
DROP TABLE IF EXISTS onboarding_modules CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS blacklisted_tokens CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;
DROP TABLE IF EXISTS document_categories CASCADE;
DROP TABLE IF EXISTS document_summaries CASCADE;
DROP TABLE IF EXISTS chat_sessions CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS renewal_reminders CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS chatbot_configs CASCADE;
DROP TABLE IF EXISTS user_permission_grants CASCADE;
DROP TABLE IF EXISTS invoices CASCADE;

-- Xóa sequences (không CASCADE vì sẽ tạo lại ngay sau đó)
DROP SEQUENCE IF EXISTS roles_id_seq;
DROP SEQUENCE IF EXISTS departments_id_seq;
DROP SEQUENCE IF EXISTS refresh_tokens_seq;
DROP SEQUENCE IF EXISTS blacklisted_tokens_seq;

-- Không cần sequence thủ công cho refresh_tokens và blacklisted_tokens vì dùng SERIAL

-- Tạo bảng Tenants (Organizations)
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Company Information
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    website VARCHAR(255),
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
    
    -- Subscription Information (FK resolved via ALTER TABLE after subscriptions is created)
    subscription_id UUID, -- References subscriptions(subscription_id), added as FK constraint below
    is_trial BOOLEAN NOT NULL DEFAULT FALSE,
    trial_used BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Audit Fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    inactive_at TIMESTAMP,
    marked_for_deletion BOOLEAN NOT NULL DEFAULT FALSE
);

-- Tạo bảng Roles
-- NOTE: permissions column is DEPRECATED and not used
-- Basic role permissions are defined in RolePermissionConstants.java
-- Additional user permissions are stored in user_permissions table
CREATE TABLE roles (
    role_id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    level INTEGER NOT NULL CHECK (level BETWEEN 1 AND 5),
    description VARCHAR(500),
    tenant_id UUID REFERENCES tenants(tenant_id) ON DELETE CASCADE,
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
    department_id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(tenant_id, code)
);

-- Tạo bảng Users
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL, -- Email đăng nhập - UNIQUE GLOBALLY
    contact_email VARCHAR(255) UNIQUE, -- Email thật để nhận thông báo (đã verify khi lưu vào DB)
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255), -- Họ tên đầy đủ
    phone_number VARCHAR(20), -- Số điện thoại
    date_of_birth DATE, -- Ngày sinh
    address VARCHAR(500), -- Địa chỉ
    role_id INTEGER NOT NULL REFERENCES roles(role_id),
    department_id INTEGER REFERENCES departments(department_id),
    tenant_id UUID REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    permissions JSONB DEFAULT '[]'::jsonb, -- Permissions bổ sung được TENANT_ADMIN cấp
    reset_password_token VARCHAR(255),
    token_expiry TIMESTAMP,
    password_reset_session_token VARCHAR(255),
    password_reset_session_expiry TIMESTAMP,
    token_version INTEGER NOT NULL DEFAULT 1,
    must_change_password BOOLEAN DEFAULT FALSE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_tenant_department ON users(tenant_id, department_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_active ON users(tenant_id, is_active);

-- Forgot-password: session token after OTP verified (existing DBs)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_session_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_session_expiry TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_password_reset_session_token
    ON users (password_reset_session_token)
    WHERE password_reset_session_token IS NOT NULL;

-- Tạo bảng Onboarding Modules (nội dung onboarding theo tenant)
CREATE TABLE onboarding_modules (
    onboarding_module_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000),
    content TEXT NOT NULL,
    detail_file_name VARCHAR(255),
    detail_file_type VARCHAR(100),
    detail_file_path VARCHAR(500),
    detail_file_size BIGINT,
    estimated_minutes INTEGER DEFAULT 5,
    display_order INTEGER NOT NULL DEFAULT 0,
    required_permissions JSONB DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Backward-compatible columns for existing onboarding_modules tables
ALTER TABLE IF EXISTS onboarding_modules ADD COLUMN IF NOT EXISTS detail_file_name VARCHAR(255);
ALTER TABLE IF EXISTS onboarding_modules ADD COLUMN IF NOT EXISTS detail_file_type VARCHAR(100);
ALTER TABLE IF EXISTS onboarding_modules ADD COLUMN IF NOT EXISTS detail_file_path VARCHAR(500);
ALTER TABLE IF EXISTS onboarding_modules ADD COLUMN IF NOT EXISTS detail_file_size BIGINT;

CREATE INDEX IF NOT EXISTS idx_onboarding_modules_tenant_active
    ON onboarding_modules(tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_onboarding_modules_tenant_order
    ON onboarding_modules(tenant_id, display_order);

-- Tạo bảng Onboarding Progress (tiến độ theo user và module)
CREATE TABLE onboarding_progress (
    onboarding_progress_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    module_id UUID NOT NULL REFERENCES onboarding_modules(onboarding_module_id) ON DELETE CASCADE,
    read_percent INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    last_viewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_onboarding_progress_user_module UNIQUE (user_id, module_id)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_progress_tenant_user
    ON onboarding_progress(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_onboarding_progress_module
    ON onboarding_progress(module_id);

-- Tạo sequence rõ ràng cho refresh_tokens & blacklisted_tokens
-- (tránh lỗi khi schema cũ vẫn tham chiếu refresh_tokens_seq)
CREATE SEQUENCE refresh_tokens_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE blacklisted_tokens_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- Tạo bảng Refresh Tokens
CREATE TABLE refresh_tokens (
    refresh_token_id BIGINT PRIMARY KEY DEFAULT nextval('refresh_tokens_seq'),
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL
);

-- Tạo bảng Blacklist (Cho Logout)
CREATE TABLE blacklisted_tokens (
    blacklisted_token_id BIGINT PRIMARY KEY DEFAULT nextval('blacklisted_tokens_seq'),
    token VARCHAR(255) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL
);

-- Tạo bảng Documents (Document Dashboard)
CREATE TABLE IF NOT EXISTS documents (
    document_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Basic Info
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    
    -- Tenant & Classification
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    category_id UUID,
    description VARCHAR(1000),

    -- Display
    document_title VARCHAR(500),
    active_version_id UUID,
    
    -- Access Control
    visibility VARCHAR(30) NOT NULL DEFAULT 'COMPANY_WIDE',
    minimum_role_level INTEGER NOT NULL DEFAULT 4 CHECK (minimum_role_level BETWEEN 1 AND 5),
    owner_department_id INTEGER REFERENCES departments(department_id),
    accessible_departments JSONB,
    accessible_roles JSONB,
    
    -- Upload History (Audit Trail)
    uploaded_by UUID NOT NULL REFERENCES users(user_id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Update History
    updated_by UUID REFERENCES users(user_id),
    updated_at TIMESTAMP,
    
    -- Embedding Processing
    embedding_status VARCHAR(20) DEFAULT 'PENDING',
    chunk_count INTEGER,
    embedding_model VARCHAR(100),
    embedding_error VARCHAR(1000),
    
    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_by UUID REFERENCES users(user_id),
    deleted_at TIMESTAMP,
    
    -- Usage Stats
    view_count BIGINT DEFAULT 0,
    last_accessed_at TIMESTAMP
);

-- Indexes for documents
CREATE INDEX IF NOT EXISTS idx_documents_tenant_visibility ON documents(tenant_id, visibility);
CREATE INDEX IF NOT EXISTS idx_documents_uploaded_at ON documents(uploaded_at);
CREATE INDEX IF NOT EXISTS idx_documents_embedding_status ON documents(embedding_status) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_documents_tenant_active_uploaded ON documents(tenant_id, uploaded_at DESC) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_documents_tenant_deleted_at ON documents(tenant_id, deleted_at DESC) WHERE is_active = false;

-- Tạo bảng Document Chunks với Vector Embeddings (pgvector)
CREATE TABLE IF NOT EXISTS document_chunks (
    document_chunk_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    version_id UUID,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    
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
    owner_department_id INTEGER REFERENCES departments(department_id),

    minimum_role_level INTEGER NOT NULL DEFAULT 4 CHECK (minimum_role_level BETWEEN 1 AND 5),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(document_id, chunk_index)
);

-- Indexes for vector similarity search
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_version ON document_chunks(version_id);
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
    subscription_plan_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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

-- Enforce fixed plan types and avoid case-related duplicates
UPDATE subscription_plans
SET code = UPPER(code)
WHERE code IS NOT NULL;

ALTER TABLE subscription_plans
    DROP CONSTRAINT IF EXISTS chk_subscription_plans_code_enum;
ALTER TABLE subscription_plans
    ADD CONSTRAINT chk_subscription_plans_code_enum
    CHECK (code IN ('TRIAL', 'STARTER', 'STANDARD', 'ENTERPRISE'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_plans_code_upper
    ON subscription_plans (UPPER(code));

-- Tạo bảng Subscriptions (Actual tenant subscriptions, linked to a plan)
CREATE TABLE subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Relationships
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    plan_id UUID REFERENCES subscription_plans(subscription_plan_id) ON DELETE SET NULL, -- snapshot reference to plan

    -- Subscription Details (may differ from plan if overridden by admin)
    tier VARCHAR(50) NOT NULL,            -- TRIAL, STARTER, STANDARD, ENTERPRISE
    status VARCHAR(50) NOT NULL,          -- ACTIVE, EXPIRED, CANCELLED, SUSPENDED

    -- Billing Information
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    price DECIMAL(10, 2),                 -- Actual price charged (VND or USD)
    currency VARCHAR(10),
    billing_cycle VARCHAR(20),            -- MONTHLY, QUARTERLY, YEARLY
    next_billing_date TIMESTAMP,
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,

    -- Trial Information
    is_trial BOOLEAN NOT NULL DEFAULT FALSE,
    trial_end_date TIMESTAMP,
    grace_period_days INTEGER DEFAULT 7,

    -- Usage Limits (snapshot from plan, may be overridden)
    max_users INTEGER,
    max_documents INTEGER,
    max_storage_gb INTEGER,
    max_api_calls INTEGER,
    max_chatbot_requests INTEGER,
    max_rag_documents INTEGER,
    max_ai_tokens BIGINT,
    context_window_tokens INTEGER,
    rag_chunk_size INTEGER,

    -- AI Model Configuration (snapshot from plan)
    ai_model VARCHAR(100),
    embedding_model VARCHAR(50),

    -- Auto Renewal & Cancellation
    cancelled_at TIMESTAMP,
    cancelled_by UUID,
    cancellation_reason VARCHAR(500),

    -- Payment Tracking
    transaction_code VARCHAR(200),
    payment_method VARCHAR(50),
    last_payment_id VARCHAR(200),
    last_payment_date TIMESTAMP,
    payment_gateway VARCHAR(100),

    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP,
    updated_by UUID,

    -- Notes
    notes VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant ON subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan ON subscriptions(plan_id);

ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS fk_tenant_subscription;

ALTER TABLE tenants
    ADD CONSTRAINT fk_tenant_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(subscription_id) ON DELETE SET NULL;

-- Tạo bảng Payment Transactions
CREATE TABLE payment_transactions (
    payment_transaction_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Relationships
    subscription_id UUID NOT NULL REFERENCES subscriptions(subscription_id),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),

    -- Payment Details
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    transaction_code VARCHAR(200) NOT NULL UNIQUE,
    tier VARCHAR(50) NOT NULL,

    -- Payment Gateway
    gateway VARCHAR(50) NOT NULL DEFAULT 'SEPAY',
    gateway_transaction_id VARCHAR(200),
    gateway_response JSONB,

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000),

    -- QR Code
    qr_content VARCHAR(500),
    qr_image_url VARCHAR(500),
    expires_at TIMESTAMP,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    updated_at TIMESTAMP,

    -- Audit
    created_by UUID,
    notes VARCHAR(1000),

    -- Auto-renewal
    is_auto_renewal BOOLEAN NOT NULL DEFAULT FALSE,
    webhook_retry_count INTEGER NOT NULL DEFAULT 0,
    last_webhook_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_transaction_code ON payment_transactions(transaction_code);
CREATE INDEX IF NOT EXISTS idx_pt_subscription ON payment_transactions(subscription_id);
CREATE INDEX IF NOT EXISTS idx_pt_tenant ON payment_transactions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pt_status ON payment_transactions(status);

-- =====================================================
-- CÁC BẢNG MỚI BỔ SUNG
-- =====================================================

-- Tạo bảng Invoices (Hóa đơn chính thức tách riêng khỏi payment_transactions)
CREATE TABLE invoices (
    invoice_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,      -- INV-2026-000001
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(subscription_id),
    payment_transaction_id UUID REFERENCES payment_transactions(payment_transaction_id),

    -- Thông tin hóa đơn
    total_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'VND',
    billing_period_start TIMESTAMP,
    billing_period_end TIMESTAMP,
    description VARCHAR(1000),

    -- Trạng thái
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',    -- ISSUED, PAID, CANCELLED, OVERDUE

    -- Audit
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);

-- Tạo bảng Renewal Reminders (Track email nhắc gia hạn subscription)
CREATE TABLE renewal_reminders (
    renewal_reminder_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(subscription_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,

    remind_day INTEGER NOT NULL,               -- 7, 3, 0 (số ngày trước hết hạn)
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    email_to VARCHAR(255) NOT NULL,
    status VARCHAR(30) DEFAULT 'SENT',         -- SENT, FAILED, OPENED
    qr_content TEXT,                           -- QR code content đã gửi
    error_message VARCHAR(500),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(subscription_id, remind_day)        -- Chỉ gửi 1 lần mỗi mốc
);
CREATE INDEX IF NOT EXISTS idx_renewal_reminders_subscription ON renewal_reminders(subscription_id);

-- Tạo bảng Document Categories (Phân loại tài liệu dạng cây, per-tenant)
CREATE TABLE document_categories (
    category_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    parent_id UUID REFERENCES document_categories(category_id) ON DELETE SET NULL,  -- Cây phân cấp
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_doc_categories_tenant ON document_categories(tenant_id);
CREATE INDEX IF NOT EXISTS idx_doc_categories_parent ON document_categories(parent_id);

ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS fk_documents_category;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_category
    FOREIGN KEY (category_id) REFERENCES document_categories(category_id) ON DELETE SET NULL;

-- Tạo bảng Document Tags (controlled vocabulary per-tenant)
CREATE TABLE IF NOT EXISTS document_tags (
    tag_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_doc_tags_tenant ON document_tags(tenant_id);

-- Bảng mapping many-to-many document <-> tags
CREATE TABLE IF NOT EXISTS document_tag_mappings (
    document_id UUID NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES document_tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_doc_tag_mappings_tag ON document_tag_mappings(tag_id);

-- Tạo bảng Document Versions (lịch sử các phiên bản tài liệu)
CREATE TABLE IF NOT EXISTS document_versions (
    version_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id       UUID NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    tenant_id         UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,

    -- Snapshot version tối giản
    version_number    INTEGER NOT NULL,                         -- Số phiên bản (1, 2, 3...)
    storage_path      VARCHAR(500) NOT NULL,                    -- Path file cũ trên MinIO

    version_note      VARCHAR(500),                             -- Ghi chú thay đổi: "Cập nhật điều khoản nghỉ phép"

    -- Audit
    created_by        UUID NOT NULL REFERENCES users(user_id),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_versions_document  ON document_versions(document_id);
CREATE INDEX IF NOT EXISTS idx_doc_versions_tenant    ON document_versions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_doc_versions_created   ON document_versions(document_id, version_number);

-- Thêm cột keywords vào document_chunks
ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS category_id UUID REFERENCES document_categories(category_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS tag_ids JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS keywords JSONB DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_document_chunks_category ON document_chunks(category_id);
CREATE INDEX IF NOT EXISTS idx_document_chunks_tag_ids ON document_chunks USING GIN(tag_ids);

-- Tạo bảng Document Summaries (Tóm tắt AI-generated 1-1 với document)
CREATE TABLE document_summaries (
    summary_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL UNIQUE REFERENCES documents(document_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,

    summary_text TEXT,                         -- Tóm tắt ngắn (1-2 đoạn)
    key_topics JSONB DEFAULT '[]'::jsonb,      -- ["Onboarding", "HR Policy", "Benefits"]
    language VARCHAR(10) DEFAULT 'vi',

    -- Trạng thái generate
    status VARCHAR(30) DEFAULT 'PENDING',      -- PENDING, PROCESSING, DONE, FAILED
    model_used VARCHAR(100),                   -- gemini-1.5-flash
    error_message VARCHAR(500),
    token_used INTEGER,

    generated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_doc_summaries_tenant ON document_summaries(tenant_id);

-- Tạo bảng Chatbot Configs (Cấu hình chatbot per-tenant)
CREATE TABLE chatbot_configs (
    config_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(tenant_id) ON DELETE CASCADE,  -- 1-1 với tenant

    -- UI / UX
    bot_name VARCHAR(100) DEFAULT 'AI Assistant',
    welcome_message TEXT DEFAULT 'Xin chào! Tôi có thể giúp gì cho bạn?',
    fallback_message TEXT DEFAULT 'Xin lỗi, tôi không tìm thấy thông tin phù hợp trong tài liệu nội bộ.',
    language VARCHAR(10) DEFAULT 'vi',

    -- Giới hạn hoạt động (trong ngưỡng plan)
    max_messages_per_day INTEGER DEFAULT 100,         -- Mỗi user/ngày
    max_message_length INTEGER DEFAULT 500,           -- Max ký tự per message
    session_timeout_minutes INTEGER DEFAULT 30,       -- Auto end session sau bao lâu
    top_k INTEGER DEFAULT 7,
    similarity_threshold DOUBLE PRECISION DEFAULT 0.7,
    chat_mode VARCHAR(20) DEFAULT 'BALANCED',

    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    updated_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Tạo bảng Chat Sessions (Phiên hội thoại)
CREATE TABLE chat_sessions (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,

    title VARCHAR(500),                        -- Auto-generated từ tin nhắn đầu
    status VARCHAR(30) DEFAULT 'ACTIVE',       -- ACTIVE, ENDED, ARCHIVED
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    last_message_at TIMESTAMP,
    total_messages INTEGER DEFAULT 0,
    total_tokens_used INTEGER DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_tenant ON chat_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON chat_sessions(status);

-- Tạo bảng Chat Messages (Tin nhắn trong session)
CREATE TABLE chat_messages (
    message_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(user_id) ON DELETE SET NULL,

    role VARCHAR(20) NOT NULL,                 -- 'USER' hoặc 'ASSISTANT'
    content TEXT NOT NULL,                     -- Nội dung tin nhắn
    source_chunks JSONB DEFAULT '[]'::jsonb,   -- Chunks tài liệu được dùng để trả lời
    tokens_used INTEGER DEFAULT 0,

    -- Đánh giá câu trả lời (chỉ áp dụng với role = ASSISTANT)
    rating SMALLINT CHECK (rating BETWEEN 1 AND 5),  -- 1-5 sao
    feedback_text VARCHAR(1000),
    rated_at TIMESTAMP,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_tenant ON chat_messages(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_role ON chat_messages(role);

-- Tạo bảng Notifications (Thông báo in-app)
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient_user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,    -- NULL = broadcast
    tenant_id UUID REFERENCES tenants(tenant_id) ON DELETE CASCADE,

    type VARCHAR(50) NOT NULL,                 -- TENANT_APPROVED, SUBSCRIPTION_EXPIRING, DOCUMENT_PROCESSED, etc.
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(500),                   -- Link redirect khi click
    metadata JSONB DEFAULT '{}'::jsonb,        -- Extra data tuỳ type

    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_tenant ON notifications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(recipient_user_id, is_read) WHERE is_read = FALSE;

-- Tạo bảng Audit Logs (Log hành động quan trọng - security & compliance)
CREATE TABLE audit_logs (
    audit_log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(tenant_id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(user_id) ON DELETE SET NULL,
    user_email VARCHAR(255),                   -- Denormalized để log vẫn giữ sau khi xóa user
    user_role VARCHAR(100),                    -- Denormalized

    action VARCHAR(100) NOT NULL,              -- USER_LOGIN, DOCUMENT_UPLOAD, ROLE_GRANTED, etc.
    entity_type VARCHAR(100),                  -- 'User', 'Document', 'Subscription'
    entity_id VARCHAR(255),                    -- UUID của entity bị tác động
    old_value JSONB,                           -- Giá trị trước khi thay đổi
    new_value JSONB,                           -- Giá trị sau khi thay đổi
    description VARCHAR(1000),

    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    status VARCHAR(20) DEFAULT 'SUCCESS',      -- SUCCESS, FAILED
    error_message VARCHAR(500),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);

-- Tạo bảng User Permission Grants (Normalize permissions JSONB thành bảng proper)
CREATE TABLE user_permission_grants (
    grant_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,          -- DOCUMENT_READ, DOCUMENT_WRITE, ANALYTICS_VIEW
    granted_by UUID REFERENCES users(user_id) ON DELETE SET NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    note VARCHAR(500),
    UNIQUE(user_id, permission)
);
CREATE INDEX IF NOT EXISTS idx_permission_grants_user ON user_permission_grants(user_id);
CREATE INDEX IF NOT EXISTS idx_permission_grants_tenant ON user_permission_grants(tenant_id);

-------------------------------------------------------
-- 2. NẠP DỮ LIỆU MẪU (Password: 123456)
---------------------------------------------------------
-- Thêm Doanh nghiệp mẫu (ACTIVE - Đã được duyệt)
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

-- Thêm Tenant đang chờ duyệt (PENDING)
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

-- Thêm Tenant bị từ chối (REJECTED)
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

-------------------------------------------------------
-- 3. SEED DATA: ROLES
-- NOTE: permissions field is not used anymore (deprecated)
-- Basic permissions are defined in RolePermissionConstants.java (hard-coded)
-- User-specific permissions are stored in user_permissions table
-------------------------------------------------------
-- System Roles (tenant_id = NULL, role_type = 'SYSTEM')
INSERT INTO roles (code, name, level, description, tenant_id, role_type) VALUES
('SUPER_ADMIN', 'Super Administrator', 1, 'System administrator with full access to platform', NULL, 'SYSTEM'),
('STAFF', 'Platform Staff', 2, 'Platform staff member', NULL, 'SYSTEM');

-- Tenant Fixed Roles (tenant_id = NULL, role_type = 'FIXED')
-- Note: TENANT_ADMIN can manage all organization features including documents
--       EMPLOYEE has basic access and can use chatbot
--       TENANT_ADMIN can grant additional permissions to specific users via user_permissions table
INSERT INTO roles (code, name, level, description, tenant_id, role_type) VALUES
('TENANT_ADMIN', 'Tenant Administrator', 2, 'Organization administrator with full tenant access', NULL, 'FIXED'),
('EMPLOYEE', 'Employee', 4, 'Regular employee with basic access', NULL, 'FIXED');

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
-- TENANT_ADMIN (role_id = 3) - Has DOCUMENT_ALL → Can manage Document Dashboard
('admin@fpt.com', 'fpt.admin.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Tenant Admin', '+84-987-654-321', 3, 1, '550e8400-e29b-41d4-a716-446655440000'),

-- EMPLOYEE (role_id = 4) - Basic profile + chatbot usage
('employee1@fpt.com', 'fpt.employee1.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 1', '+84-901-234-567', 4, 3, '550e8400-e29b-41d4-a716-446655440000'),
('employee2@fpt.com', 'fpt.employee2.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 2', '+84-902-345-678', 4, 4, '550e8400-e29b-41d4-a716-446655440000'),
('employee3@fpt.com', 'fpt.employee3.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'FPT Employee 3', '+84-903-456-789', 4, 5, '550e8400-e29b-41d4-a716-446655440000');

-- Thêm User đang chờ Reset Password (OTP: 888888)
INSERT INTO users (email, contact_email, password, full_name, role_id, tenant_id, reset_password_token, token_expiry)
VALUES ('forgot_user@fpt.com', 'forgot.user.real@gmail.com', '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy', 'Forgot User', 4, '550e8400-e29b-41d4-a716-446655440000', '888888', CURRENT_TIMESTAMP + interval '15 minutes');

-------------------------------------------------------
-- 5.1 SEED DATA: ONBOARDING MODULES (FPT Software)
-------------------------------------------------------
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

-------------------------------------------------------
-- 6. SEED DATA: SUBSCRIPTION PLANS
-------------------------------------------------------
-- TRIAL Plan (Free 14 days)
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
    'gpt-3.5-turbo', 'text-embedding-ada-002',
    true, 0,
    '✅ 5 users, ✅ 100 documents, ✅ 5GB storage, ✅ 1,000 API calls/month, ✅ Basic AI chatbot',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- STARTER Plan
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
    'gpt-3.5-turbo', 'text-embedding-ada-002',
    true, 1,
    '✅ 10 users, ✅ 500 documents, ✅ 10GB storage, ✅ 5,000 API calls/month, ✅ RAG enabled, ✅ Priority support',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- STANDARD Plan
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
    'gpt-4', 'text-embedding-ada-002',
    true, 2,
    '✅ 50 users, ✅ 2,000 documents, ✅ 50GB storage, ✅ 20,000 API calls/month, ✅ GPT-4 model, ✅ Advanced RAG, ✅ 24/7 support',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ENTERPRISE Plan
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
    999, 999999, 500, 999999,
    999999, 999999, 999999,
    32768, 2048,
    'gpt-4', 'text-embedding-ada-002',
    true, 3,
    '✅ Unlimited users, ✅ Unlimited documents, ✅ 500GB storage, ✅ Unlimited API calls, ✅ GPT-4 model, ✅ Advanced RAG, ✅ Dedicated support, ✅ Custom integration',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-------------------------------------------------------
-- 7. SEED DATA: SUBSCRIPTIONS
-- Tạo subscription thực tế cho các tenant ACTIVE
-------------------------------------------------------
-- Subscription cho FPT Software (ENTERPRISE, ACTIVE)
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
    999, 999999, 500, 999999,
    999999, 999999, 999999,
    32768, 2048,
    'gpt-4', 'text-embedding-ada-002',
    'SEPAY',
    CURRENT_TIMESTAMP - interval '5 days'
);

-- Gán subscription_id cho FPT tenant
UPDATE tenants
SET subscription_id = 'b0000000-0000-0000-0000-000000000001'
WHERE tenant_id = '550e8400-e29b-41d4-a716-446655440000';

-- Đồng bộ trạng thái trial ở tenant theo lịch sử/thực trạng subscription
-- Rule: mỗi tenant chỉ được trial 1 lần trong đời
-- 1) Đã từng có subscription trial -> trial_used = TRUE
UPDATE tenants t
SET trial_used = TRUE
WHERE EXISTS (
    SELECT 1
    FROM subscriptions s
    WHERE s.tenant_id = t.tenant_id
      AND s.is_trial = TRUE
);

-- 2) is_trial phản ánh trạng thái hiện tại: chỉ TRUE khi có subscription ACTIVE và is_trial = TRUE
UPDATE tenants t
SET is_trial = EXISTS (
    SELECT 1
    FROM subscriptions s
    WHERE s.tenant_id = t.tenant_id
      AND s.status = 'ACTIVE'
      AND s.is_trial = TRUE
);

-- Đồng bộ active version cho RAG (document-level)
-- 1) Nếu document có version thì lấy version_number lớn nhất làm active
UPDATE documents d
SET active_version_id = v.version_id
FROM (
    SELECT DISTINCT ON (document_id) document_id, version_id
    FROM document_versions
    ORDER BY document_id, version_number DESC
) v
WHERE d.document_id = v.document_id
  AND d.active_version_id IS NULL;

-- 2) Backfill version_id cho chunks cũ: gán theo active_version_id của document
UPDATE document_chunks c
SET version_id = d.active_version_id
FROM documents d
WHERE c.document_id = d.document_id
  AND c.version_id IS NULL
  AND d.active_version_id IS NOT NULL;

-------------------------------------------------------
-- 7.1 SEED DATA: PAYMENT TRANSACTIONS (for Admin Revenue Chart)
-- Rule test:
--   - SUCCESS transactions MUST be counted in /admin/analytics/revenue
--   - FAILED/CANCELLED must NOT be counted
-------------------------------------------------------
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

-------------------------------------------------------
-- 7.2 SEED DATA: AUDIT LOGS (for Admin Recent Activity)
-- Newest first should appear in /admin/analytics/recent-activities
-------------------------------------------------------
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

-------------------------------------------------------
-- 8. SEED DATA: DOCUMENT CATEGORIES (FPT Software)
-- Tenant: 550e8400-e29b-41d4-a716-446655440000
-- Created by: admin@fpt.com
-------------------------------------------------------
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

-------------------------------------------------------
-- 9. SEED DATA: DOCUMENTS (FPT Software)
-- Tenant: 550e8400-e29b-41d4-a716-446655440000
-- Uploaded by: admin@fpt.com
-- Note: storage_path points to MinIO path (files không thực sự tồn tại, chỉ để test API)
-------------------------------------------------------
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

-------------------------------------------------------
-- 10. SEED DATA: CHAT SESSIONS + MESSAGES (FPT Software)
-- Note: document_chunks are intentionally not seeded here — they have null embeddings
-- and would never match similarity search. Documents above have embedding_status = PENDING
-- and will be processed when real files are uploaded.
-------------------------------------------------------

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

