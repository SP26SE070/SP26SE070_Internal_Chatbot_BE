# SP26SE070 - Internal Consulting Chatbot Platform for Businesses using RAG

## 📝 Project Introduction
This project is developed to address the challenges of knowledge management in modern organizations, where information is often fragmented and difficult to access. We are building a **Multi-tenant SaaS** platform providing an internal consulting chatbot that allows businesses to safely leverage their private data using **Retrieval-Augmented Generation (RAG)**. 

The system ensures AI responses are accurate and grounded by retrieving information directly from internal documents such as HR policies, IT guidelines, and operational procedures. This not only reduces the burden on support departments but also enhances the overall productivity of all employees.

* **Project Name (English):** Internal consulting chatbot platform for businesses using RAG.
* **Project Name (Vietnamese):** Nền tảng chatbot tư vấn nội bộ cho doanh nghiệp dùng RAG.
* **Project Code:** SP26SE070.
* **Group Code:** GSP26SE114.
* **Duration:** January 1, 2026 - April 30, 2026.

## Bulk Document Upload

Use `scripts/bulk_upload_documents.py` to upload a folder as one validated batch. Add
`manifest.csv` or `manifest.json` to the folder, or pass `--manifest`; see
`scripts/documents.example/`.

Required manifest columns:

```text
filename,title,department,min_role_level,visibility,category,tags,version_note
```

Use `department=ALL` with `visibility=COMPANY_WIDE`. Use a department code or name
with `visibility=DEPARTMENT`. Separate tag codes or names with semicolons. Category
and tags may be blank.

The script validates every row and file before upload, resolves active tenant
metadata, and checks projected document count and storage usage against the tenant
subscription. Use a tenant-admin token so the plan check can read full tenant usage.
Run a remote preflight without uploading:

```powershell
py scripts/bulk_upload_documents.py `
  --folder scripts/documents.example `
  --token $env:BULK_UPLOAD_TOKEN `
  --dry-run
```

Remove `--dry-run` after the precheck passes.

---

## 👥 Team Information (Group GSP26SE114)
The project is conducted by Software Engineering students at **FPT University**:

| No. | Full Name | Student ID | Primary Role |
| :---: | :--- | :---: | :--- |
| 1 | **Phạm Hồng Quân** | SE161574 | Leader & Backend Developer |
| 2 | **Đại Kim Nguyên** | SE151283 | Backend Developer |
| 3 | **Trương Trí Sỹ** | SE173472 | Frontend Developer |
| 4 | **Lê Minh Quân** | SE182901 | Frontend Developer |

* **Supervisor:** Mr. Phan Minh Tâm.

---

## 💻 Core Tech Stack (Backend)
The Backend is designed to meet strict enterprise security and scalability requirements:

* **Programming Language:** Java 21 (LTS).
* **Core Framework:** Spring Boot 3.x.
* **Database:**
    * **Relational Database:** PostgreSQL for metadata storage and system management.
    * **Vector Database:** Integrated **pgvector** extension for storing and querying embeddings for RAG.
* **Artificial Intelligence (AI):** Spring AI integrating Large Language Models (LLMs) with Vietnamese support.
* **Security:** Spring Security & JWT (JSON Web Token) for session management and user identification.
* **Access Control:** Role-Based Access Control (RBAC) supporting department-based document access.

---

## 💳 Payment
To support the **SaaS** business model and automate the subscription renewal process for businesses, the system integrates an automated payment solution:

* **Payment Gateway:** **SePay** (Real-time bank transaction automation via API/Webhook).
* **Payment Method:** Bank transfer via **Dynamic VietQR**.
* **Technical Mechanism:**
    * **VietQR Pro:** The Backend calls SePay API to generate a dynamic QR code containing the exact amount and a unique transaction ID for each tenant/invoice.
    * **Webhook Integration:** SePay sends a real-time HTTP POST request (Webhook) to the Backend as soon as a matching transaction is detected in the bank account.
    * **Automatic Provisioning:** The system verifies the Webhook signature for security, reconciles the order code in the database, and automatically updates the subscription status without manual intervention.
* **Business Workflow:**
    1.  The business (Tenant) selects a service plan (Basic/Premium/Enterprise).
    2.  The system displays a unique QR code generated via SePay.
    3.  The user scans and pays via their Mobile Banking app.
    4.  SePay triggers a Webhook to the `SP26SE070` Backend endpoint.
    5.  The Backend validates the signature, extends the subscription validity, and unlocks features for the Tenant.
---
© 2026 **Group GSP26SE114** - FPT Education.
