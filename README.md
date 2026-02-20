# SP26SE070 - Internal Consulting Chatbot Platform for Businesses using RAG

## 📝 Project Introduction
This project is developed to address the challenges of knowledge management in modern organizations, where information is often fragmented and difficult to access. We are building a **Multi-tenant SaaS** platform providing an internal consulting chatbot that allows businesses to safely leverage their private data using **Retrieval-Augmented Generation (RAG)**. 

The system ensures AI responses are accurate and grounded by retrieving information directly from internal documents such as HR policies, IT guidelines, and operational procedures. This not only reduces the burden on support departments but also enhances the overall productivity of all employees.

* **Project Name (English):** Internal consulting chatbot platform for businesses using RAG.
* **Project Name (Vietnamese):** Nền tảng chatbot tư vấn nội bộ cho doanh nghiệp dùng RAG.
* **Project Code:** SP26SE070.
* **Group Code:** GSP26SE114.
* **Duration:** January 1, 2026 - April 30, 2026.

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
Để hỗ trợ mô hình kinh doanh **SaaS** và tự động hóa quy trình gia hạn gói dịch vụ cho doanh nghiệp, hệ thống tích hợp giải pháp thanh toán tự động:

* **Payment Gateway:** **SePay** (Giải pháp tự động hóa ngân hàng qua API/Webhook).
* **Cơ chế hoạt động (Mechanism):**
    * **VietQR Dynamic:** Backend gọi API SePay để tạo mã QR động chứa số tiền và nội dung chuyển khoản định danh cho từng giao dịch/tenant.
    * **Webhook Integration:** SePay gửi tín hiệu (Webhook) thời gian thực về Backend ngay khi nhận được biến động số dư ngân hàng.
    * **Automatic Provisioning:** Hệ thống tự động kiểm tra nội dung chuyển khoản, đối soát với đơn hàng trong Database và cập nhật trạng thái gói dịch vụ (Subscription) ngay lập tức mà không cần nhân viên phê duyệt.
* **Quy trình nghiệp vụ:**
    1.  Doanh nghiệp (Tenant) chọn gói dịch vụ (Basic/Premium/Enterprise).
    2.  Hệ thống hiển thị mã QR thanh toán từ SePay.
    3.  Người dùng quét mã và thanh toán qua App Ngân hàng.
    4.  SePay gọi Webhook tới Endpoint của Backend `SP26SE070`.
    5.  Backend xác thực chữ ký (Signature), cập nhật hạn sử dụng và kích hoạt quyền truy cập cho doanh nghiệp.
---
© 2026 **Group GSP26SE114** - FPT Education.
