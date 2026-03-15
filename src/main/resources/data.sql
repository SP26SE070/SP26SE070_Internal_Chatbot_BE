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
    updated_at TIMESTAMP
);

-- Tạo bảng Roles
-- NOTE: permissions column is DEPRECATED and not used
-- Basic role permissions are defined in RolePermissionConstants.java
-- Additional user permissions are stored in user_permissions table
CREATE TABLE roles (
    role_id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
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
    must_change_password BOOLEAN DEFAULT FALSE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_tenant_department ON users(tenant_id, department_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_active ON users(tenant_id, is_active);

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

-- Tạo bảng Documents (Knowledge Base)
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
    
    -- Access Control
    visibility VARCHAR(30) NOT NULL DEFAULT 'COMPANY_WIDE',
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
    amount DECIMAL(15, 2) NOT NULL,
    tax_amount DECIMAL(15, 2) DEFAULT 0,
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
INSERT INTO roles (code, name, description, tenant_id, role_type) VALUES
('SUPER_ADMIN', 'Super Administrator', 'System administrator with full access to platform', NULL, 'SYSTEM'),
('STAFF', 'Platform Staff', 'Staff member who approves tenant registrations', NULL, 'SYSTEM');

-- Tenant Fixed Roles (tenant_id = NULL, role_type = 'FIXED')
-- Note: TENANT_ADMIN can manage all organization features including documents
--       EMPLOYEE has basic access and can use chatbot
--       TENANT_ADMIN can grant additional permissions to specific users via user_permissions table
INSERT INTO roles (code, name, description, tenant_id, role_type) VALUES
('TENANT_ADMIN', 'Tenant Administrator', 'Organization administrator with full tenant access', NULL, 'FIXED'),
('EMPLOYEE', 'Employee', 'Regular employee with basic access', NULL, 'FIXED');

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
    storage_path, tenant_id, description, visibility,
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
 'COMPANY_WIDE',
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '8 days',
 'COMPLETED', TRUE,
 'c1200000-0000-0000-0000-000000000012', 'Nội quy công ty 2026'),

-- 2. Hướng dẫn onboarding - COMPANY_WIDE
('d2000000-0000-0000-0000-000000000002',
 'huong_dan_onboarding.pdf', 'Hướng dẫn Onboarding nhân viên mới.pdf', 'application/pdf', 512000,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/huong_dan_onboarding.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Hướng dẫn từng bước cho nhân viên mới: đăng ký hệ thống, làm quen môi trường làm việc, các đầu mối liên hệ',
 'COMPANY_WIDE',
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '7 days',
 'COMPLETED', TRUE,
 'c3100000-0000-0000-0000-000000000031', 'Hướng dẫn Onboarding nhân viên mới'),

-- 3. Chính sách nghỉ phép - COMPANY_WIDE
('d3000000-0000-0000-0000-000000000003',
 'chinh_sach_nghi_phep_2026.pdf', 'Chính sách nghỉ phép 2026.pdf', 'application/pdf', 153600,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/chinh_sach_nghi_phep_2026.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Quy định ngày phép năm, phép ốm, phép thai sản và các loại phép đặc biệt',
 'COMPANY_WIDE',
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '6 days',
 'COMPLETED', TRUE,
 'c1100000-0000-0000-0000-000000000011', 'Chính sách nghỉ phép 2026'),

-- 4. Kiến trúc hệ thống nội bộ - chỉ DEV department
('d4000000-0000-0000-0000-000000000004',
 'system_architecture_v2.pdf', 'System Architecture v2.0.pdf', 'application/pdf', 1048576,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/system_architecture_v2.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Tài liệu kiến trúc hệ thống nội bộ phiên bản 2.0: microservices, database schema, API contracts',
 'SPECIFIC_DEPARTMENTS',
 (SELECT department_id FROM departments WHERE tenant_id = '550e8400-e29b-41d4-a716-446655440000' AND code = 'DEV'),
 (SELECT jsonb_agg(department_id) FROM departments WHERE tenant_id = '550e8400-e29b-41d4-a716-446655440000' AND code = 'DEV'),
 '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '5 days',
 'COMPLETED', TRUE,
 'c2100000-0000-0000-0000-000000000021', 'System Architecture v2.0'),

-- 5. Coding Standards - COMPANY_WIDE
('d5000000-0000-0000-0000-000000000005',
 'coding_standards_java.pdf', 'Coding Standards - Java & Spring Boot.pdf', 'application/pdf', 307200,
 'tenant-550e8400-e29b-41d4-a716-446655440000/documents/coding_standards_java.pdf',
 '550e8400-e29b-41d4-a716-446655440000',
 'Tiêu chuẩn code Java và Spring Boot: đặt tên, cấu trúc package, xử lý lỗi, logging',
 'COMPANY_WIDE',
 NULL, '[]'::jsonb, '[]'::jsonb,
 (SELECT user_id FROM users WHERE email = 'admin@fpt.com'),
 CURRENT_TIMESTAMP - interval '4 days',
 'PENDING', TRUE,
 'c2200000-0000-0000-0000-000000000022', 'Coding Standards - Java & Spring Boot');
