-- Reproducible final state for the HungDong Tech demo tenant.
-- Demo password for all accounts: 123456
DO $demo$
DECLARE
    v_tenant_id UUID := 'a98b6fcf-ba25-4a95-9128-78da1df9ab99';
    v_staff_id UUID;
    v_subscription_id UUID;
    v_standard_plan subscription_plans%ROWTYPE;
    v_tenant_admin_role_id INTEGER;
    v_employee_role_id INTEGER;
    v_manager_role_id INTEGER;
    v_marketing_id INTEGER;
    v_finance_id INTEGER;
    v_engineering_id INTEGER;
    v_password TEXT := '$2a$10$cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy';
BEGIN
    SELECT user_id INTO v_staff_id
    FROM users
    WHERE email = 'staff@system.com';

    SELECT * INTO v_standard_plan
    FROM subscription_plans
    WHERE code = 'STANDARD' AND is_active = TRUE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Active STANDARD subscription plan is required';
    END IF;

    SELECT role_id INTO v_tenant_admin_role_id FROM roles WHERE code = 'TENANT_ADMIN';
    SELECT role_id INTO v_employee_role_id FROM roles WHERE code = 'EMPLOYEE';

    INSERT INTO tenants (
        tenant_id, name, contact_email, representative_name, representative_position,
        request_message, requested_at, status, reviewed_by, reviewed_at,
        is_trial, trial_used
    )
    VALUES (
        v_tenant_id, 'Công ty Cổ phần Công nghệ Hừng Đông', 'admin@hungdong.vn',
        'HungDong Tech Admin', 'Demo Tenant Admin', 'HungDong Tech demo tenant',
        CURRENT_TIMESTAMP, 'ACTIVE', v_staff_id, CURRENT_TIMESTAMP, FALSE, FALSE
    )
    ON CONFLICT (contact_email) DO UPDATE
    SET name = EXCLUDED.name,
        status = 'ACTIVE',
        updated_at = CURRENT_TIMESTAMP
    RETURNING tenant_id INTO v_tenant_id;

    INSERT INTO departments (tenant_id, code, name, description, is_active)
    VALUES
        (v_tenant_id, 'MKT', 'Marketing', 'Marketing', TRUE),
        (v_tenant_id, 'SALES', 'Sales', 'Sales', TRUE),
        (v_tenant_id, 'FINANCE', 'Finance', 'Finance', TRUE),
        (v_tenant_id, 'ENGINEERING', 'Engineering', 'Engineering', TRUE),
        (v_tenant_id, 'HR', 'HR', 'HR', TRUE),
        (v_tenant_id, 'OPERATIONS', 'Operations', 'Operations', TRUE)
    ON CONFLICT (tenant_id, code) DO UPDATE
    SET name = EXCLUDED.name,
        description = EXCLUDED.description,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP;

    SELECT department_id INTO v_marketing_id FROM departments WHERE tenant_id = v_tenant_id AND code = 'MKT';
    SELECT department_id INTO v_finance_id FROM departments WHERE tenant_id = v_tenant_id AND code = 'FINANCE';
    SELECT department_id INTO v_engineering_id FROM departments WHERE tenant_id = v_tenant_id AND code = 'ENGINEERING';

    SELECT role_id INTO v_manager_role_id
    FROM roles
    WHERE tenant_id = v_tenant_id AND code = 'MARKETING_MANAGER';

    IF v_manager_role_id IS NULL THEN
        INSERT INTO roles (
            code, name, level, description, tenant_id, role_type,
            is_active, permissions, created_by
        )
        VALUES (
            'MARKETING_MANAGER', 'Marketing Manager', 2,
            'Level-2 manager scoped to Marketing', v_tenant_id, 'CUSTOM',
            TRUE, '["DOCUMENT_READ"]'::jsonb, v_staff_id
        )
        RETURNING role_id INTO v_manager_role_id;
    END IF;

    UPDATE users
    SET email = 'admin@hungdong.vn',
        password = v_password,
        full_name = 'HungDong Tech Admin',
        role_id = v_tenant_admin_role_id,
        tenant_id = v_tenant_id,
        must_change_password = FALSE,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND (email = 'admin@hungdong.vn' OR contact_email = 'admin@hungdong.vn');

    IF NOT FOUND THEN
        INSERT INTO users (
            email, contact_email, password, full_name, role_id, tenant_id,
            must_change_password, is_active
        )
        VALUES (
            'admin@hungdong.vn', 'admin@hungdong.vn', v_password,
            'HungDong Tech Admin', v_tenant_admin_role_id, v_tenant_id, FALSE, TRUE
        );
    END IF;

    UPDATE users
    SET email = 'mkt.manager@hungdong.vn',
        password = v_password,
        full_name = 'Marketing Manager',
        role_id = v_manager_role_id,
        department_id = v_marketing_id,
        tenant_id = v_tenant_id,
        must_change_password = FALSE,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND (email = 'mkt.manager@hungdong.vn' OR contact_email = 'mkt.manager@hungdong.vn');

    IF NOT FOUND THEN
        INSERT INTO users (
            email, contact_email, password, full_name, role_id, department_id,
            tenant_id, must_change_password, is_active
        )
        VALUES (
            'mkt.manager@hungdong.vn', 'mkt.manager@hungdong.vn', v_password,
            'Marketing Manager', v_manager_role_id, v_marketing_id, v_tenant_id, FALSE, TRUE
        );
    END IF;

    UPDATE users
    SET email = 'mkt.nv@hungdong.vn',
        password = v_password,
        full_name = 'Marketing Employee',
        role_id = v_employee_role_id,
        department_id = v_marketing_id,
        tenant_id = v_tenant_id,
        must_change_password = FALSE,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND (email = 'mkt.nv@hungdong.vn' OR contact_email = 'mkt.nv@hungdong.vn');

    IF NOT FOUND THEN
        INSERT INTO users (
            email, contact_email, password, full_name, role_id, department_id,
            tenant_id, must_change_password, is_active
        )
        VALUES (
            'mkt.nv@hungdong.vn', 'mkt.nv@hungdong.vn', v_password,
            'Marketing Employee', v_employee_role_id, v_marketing_id, v_tenant_id, FALSE, TRUE
        );
    END IF;

    UPDATE users
    SET email = 'fin.nv@hungdong.vn',
        password = v_password,
        full_name = 'Finance Employee',
        role_id = v_employee_role_id,
        department_id = v_finance_id,
        tenant_id = v_tenant_id,
        must_change_password = FALSE,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND (email = 'fin.nv@hungdong.vn' OR contact_email = 'fin.nv@hungdong.vn');

    IF NOT FOUND THEN
        INSERT INTO users (
            email, contact_email, password, full_name, role_id, department_id,
            tenant_id, must_change_password, is_active
        )
        VALUES (
            'fin.nv@hungdong.vn', 'fin.nv@hungdong.vn', v_password,
            'Finance Employee', v_employee_role_id, v_finance_id, v_tenant_id, FALSE, TRUE
        );
    END IF;

    UPDATE users
    SET email = 'eng.nv@hungdong.vn',
        password = v_password,
        full_name = 'Engineering Employee',
        role_id = v_employee_role_id,
        department_id = v_engineering_id,
        tenant_id = v_tenant_id,
        must_change_password = FALSE,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND (email = 'eng.nv@hungdong.vn' OR contact_email = 'eng.nv@hungdong.vn');

    IF NOT FOUND THEN
        INSERT INTO users (
            email, contact_email, password, full_name, role_id, department_id,
            tenant_id, must_change_password, is_active
        )
        VALUES (
            'eng.nv@hungdong.vn', 'eng.nv@hungdong.vn', v_password,
            'Engineering Employee', v_employee_role_id, v_engineering_id, v_tenant_id, FALSE, TRUE
        );
    END IF;

    SELECT subscription_id INTO v_subscription_id
    FROM subscriptions
    WHERE tenant_id = v_tenant_id AND tier = 'STANDARD' AND status = 'ACTIVE'
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_subscription_id IS NULL THEN
        INSERT INTO subscriptions (
            tenant_id, plan_id, tier, status, start_date, end_date, price, currency,
            billing_cycle, next_billing_date, auto_renew, is_trial,
            max_users, max_documents, max_storage_gb, max_api_calls,
            max_chatbot_requests, max_rag_documents, max_ai_tokens,
            context_window_tokens, rag_chunk_size, ai_model, embedding_model,
            notes
        )
        VALUES (
            v_tenant_id, v_standard_plan.subscription_plan_id, 'STANDARD', 'ACTIVE',
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 year',
            v_standard_plan.yearly_price, v_standard_plan.currency, 'YEARLY',
            CURRENT_TIMESTAMP + INTERVAL '1 year', TRUE, FALSE,
            v_standard_plan.max_users, v_standard_plan.max_documents,
            v_standard_plan.max_storage_gb, v_standard_plan.max_api_calls,
            v_standard_plan.max_chatbot_requests, v_standard_plan.max_rag_documents,
            v_standard_plan.max_ai_tokens, v_standard_plan.context_window_tokens,
            v_standard_plan.rag_chunk_size, v_standard_plan.ai_model,
            v_standard_plan.embedding_model, 'HungDong Tech demo tenant STANDARD subscription'
        )
        RETURNING subscription_id INTO v_subscription_id;
    END IF;

    UPDATE tenants
    SET subscription_id = v_subscription_id,
        is_trial = FALSE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id;

    UPDATE chatbot_configs
    SET chat_mode = 'BALANCED',
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND chat_mode IS DISTINCT FROM 'BALANCED';
END
$demo$;
