-- HungDong Tech demo normalization for the deployed Railway stack.
--
-- Tenant, departments, custom role, and users are provisioned through the
-- deployed backend APIs. This script normalizes demo login credentials after
-- those API flows generate temporary values, then attaches one active STANDARD
-- subscription because the deployed approval API does not create one.
--
-- Demo password for all accounts: 123456
BEGIN;

DO $demo$
DECLARE
    v_tenant_id UUID := 'b398da53-26ce-4ed9-8a2a-865d24622730';
    v_password TEXT :=
        CHR(36) || '2a' || CHR(36) || '10' || CHR(36) ||
        'cCA6u7Es2IIDr74Pah9shuayGvlfemwx6EkunmAuLKhrVwK5uPtGy';
BEGIN
    UPDATE users
    SET email = CASE contact_email
            WHEN 'admin@hungdong.vn' THEN 'admin@hungdong.vn'
            WHEN 'mkt.manager@hungdong.vn' THEN 'mkt.manager@hungdong.vn'
            WHEN 'mkt.nv@hungdong.vn' THEN 'mkt.nv@hungdong.vn'
            WHEN 'fin.nv@hungdong.vn' THEN 'fin.nv@hungdong.vn'
            WHEN 'eng.nv@hungdong.vn' THEN 'eng.nv@hungdong.vn'
        END,
        password = v_password,
        must_change_password = FALSE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id
      AND contact_email IN (
          'admin@hungdong.vn',
          'mkt.manager@hungdong.vn',
          'mkt.nv@hungdong.vn',
          'fin.nv@hungdong.vn',
          'eng.nv@hungdong.vn'
      );

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No HungDong API-created users found for tenant %', v_tenant_id;
    END IF;
END
$demo$;

DO $subscription$
DECLARE
    v_tenant_id UUID := 'b398da53-26ce-4ed9-8a2a-865d24622730';
    v_fpt_tenant_id UUID := '550e8400-e29b-41d4-a716-446655440000';
    v_new_subscription_id UUID := '71e6afca-16e6-45e5-8fa2-377e209599e5';
    v_subscription_id UUID;
    v_standard_plan_id UUID;
    v_fpt subscriptions%ROWTYPE;
BEGIN
    SELECT subscription_plan_id
    INTO v_standard_plan_id
    FROM subscription_plans
    WHERE code = 'STANDARD'
      AND is_active = TRUE;

    IF v_standard_plan_id IS NULL THEN
        RAISE EXCEPTION 'Active STANDARD subscription plan is required';
    END IF;

    SELECT *
    INTO v_fpt
    FROM subscriptions
    WHERE tenant_id = v_fpt_tenant_id
      AND tier = 'STANDARD'
      AND status = 'ACTIVE'
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_fpt.subscription_id IS NULL THEN
        RAISE EXCEPTION 'FPT ACTIVE STANDARD subscription template is required';
    END IF;

    SELECT subscription_id
    INTO v_subscription_id
    FROM subscriptions
    WHERE tenant_id = v_tenant_id
    ORDER BY
        CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END,
        created_at DESC
    LIMIT 1
    FOR UPDATE;

    IF v_subscription_id IS NULL THEN
        v_subscription_id := v_new_subscription_id;

        INSERT INTO subscriptions (
            subscription_id,
            ai_model,
            auto_renew,
            billing_cycle,
            cancellation_reason,
            cancelled_at,
            cancelled_by,
            context_window_tokens,
            created_at,
            created_by,
            currency,
            embedding_model,
            end_date,
            grace_period_days,
            is_trial,
            last_payment_date,
            last_payment_id,
            max_ai_tokens,
            max_api_calls,
            max_chatbot_requests,
            max_documents,
            max_rag_documents,
            max_storage_gb,
            max_users,
            next_billing_date,
            notes,
            payment_gateway,
            payment_method,
            plan_id,
            price,
            rag_chunk_size,
            start_date,
            status,
            tenant_id,
            tier,
            transaction_code,
            trial_end_date,
            updated_at,
            updated_by
        )
        VALUES (
            v_subscription_id,
            v_fpt.ai_model,
            v_fpt.auto_renew,
            v_fpt.billing_cycle,
            NULL,
            NULL,
            NULL,
            v_fpt.context_window_tokens,
            CURRENT_TIMESTAMP,
            NULL,
            v_fpt.currency,
            v_fpt.embedding_model,
            CURRENT_TIMESTAMP + INTERVAL '1 year',
            v_fpt.grace_period_days,
            FALSE,
            v_fpt.last_payment_date,
            v_fpt.last_payment_id,
            v_fpt.max_ai_tokens,
            v_fpt.max_api_calls,
            v_fpt.max_chatbot_requests,
            v_fpt.max_documents,
            v_fpt.max_rag_documents,
            v_fpt.max_storage_gb,
            v_fpt.max_users,
            CURRENT_TIMESTAMP + INTERVAL '1 year',
            NULL,
            v_fpt.payment_gateway,
            v_fpt.payment_method,
            v_standard_plan_id,
            v_fpt.price,
            v_fpt.rag_chunk_size,
            CURRENT_TIMESTAMP,
            'ACTIVE',
            v_tenant_id,
            'STANDARD',
            v_fpt.transaction_code,
            NULL,
            CURRENT_TIMESTAMP,
            NULL
        );
    ELSE
        UPDATE subscriptions
        SET ai_model = v_fpt.ai_model,
            auto_renew = v_fpt.auto_renew,
            billing_cycle = v_fpt.billing_cycle,
            cancellation_reason = NULL,
            cancelled_at = NULL,
            cancelled_by = NULL,
            context_window_tokens = v_fpt.context_window_tokens,
            currency = v_fpt.currency,
            embedding_model = v_fpt.embedding_model,
            end_date = CURRENT_TIMESTAMP + INTERVAL '1 year',
            grace_period_days = v_fpt.grace_period_days,
            is_trial = FALSE,
            last_payment_date = v_fpt.last_payment_date,
            last_payment_id = v_fpt.last_payment_id,
            max_ai_tokens = v_fpt.max_ai_tokens,
            max_api_calls = v_fpt.max_api_calls,
            max_chatbot_requests = v_fpt.max_chatbot_requests,
            max_documents = v_fpt.max_documents,
            max_rag_documents = v_fpt.max_rag_documents,
            max_storage_gb = v_fpt.max_storage_gb,
            max_users = v_fpt.max_users,
            next_billing_date = CURRENT_TIMESTAMP + INTERVAL '1 year',
            notes = NULL,
            payment_gateway = v_fpt.payment_gateway,
            payment_method = v_fpt.payment_method,
            plan_id = v_standard_plan_id,
            price = v_fpt.price,
            rag_chunk_size = v_fpt.rag_chunk_size,
            start_date = CURRENT_TIMESTAMP,
            status = 'ACTIVE',
            tier = 'STANDARD',
            transaction_code = v_fpt.transaction_code,
            trial_end_date = NULL,
            updated_at = CURRENT_TIMESTAMP,
            updated_by = NULL
        WHERE subscription_id = v_subscription_id;
    END IF;

    UPDATE tenants
    SET subscription_id = v_subscription_id,
        is_trial = FALSE,
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = v_tenant_id;
END
$subscription$;

COMMIT;

SELECT
    u.email,
    r.code AS role_code,
    r.level,
    u.department_id,
    u.must_change_password
FROM users u
JOIN roles r ON r.role_id = u.role_id
WHERE u.tenant_id = 'b398da53-26ce-4ed9-8a2a-865d24622730'
ORDER BY u.email;

SELECT
    subscription_id,
    tenant_id,
    plan_id,
    tier,
    status,
    billing_cycle,
    start_date,
    end_date,
    last_payment_id,
    max_users,
    max_documents,
    max_storage_gb,
    max_api_calls,
    max_chatbot_requests,
    max_rag_documents,
    max_ai_tokens,
    context_window_tokens,
    rag_chunk_size
FROM subscriptions
WHERE tenant_id = 'b398da53-26ce-4ed9-8a2a-865d24622730';
