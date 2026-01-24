# SP26SE070 - Internal Consulting Chatbot Platform for Businesses using RAG

## 📝 Giới thiệu dự án (Project Introduction)
Dự án được phát triển nhằm giải quyết thách thức trong việc quản lý tri thức tại các tổ chức hiện đại, nơi thông tin thường bị phân mảnh và khó tiếp cận. Chúng mình xây dựng một nền tảng **SaaS Multi-tenant** cung cấp chatbot tư vấn nội bộ, cho phép doanh nghiệp khai thác dữ liệu riêng tư một cách an toàn thông qua kỹ thuật **Retrieval-Augmented Generation (RAG)**.

Hệ thống đảm bảo các câu trả lời của AI luôn chính xác và có căn cứ nhờ việc truy xuất trực tiếp từ các tài liệu nội bộ như chính sách nhân sự, hướng dẫn CNTT và quy trình vận hành. Điều này không chỉ giúp giảm tải cho các bộ phận hỗ trợ mà còn tăng cường hiệu suất làm việc cho toàn bộ nhân viên.

* **Tên dự án (English):** Internal consulting chatbot platform for businesses using RAG.
* **Tên dự án (Vietnamese):** Nền tảng chatbot tư vấn nội bộ cho doanh nghiệp dùng RAG.
* **Mã dự án:** SP26SE070.
* **Mã nhóm:** GSP26SE114.
* **Thời gian thực hiện:** 01/01/2026 - 30/04/2026.



---

## 👥 Thông tin nhóm (Team GSP26SE114)
Dự án được thực hiện bởi các sinh viên chuyên ngành **Kỹ thuật Phần mềm (Software Engineering)** tại **Đại học FPT**:

| STT | Họ và Tên | Mã Sinh Viên | Vai trò chính |
| :---: | :--- | :---: | :--- |
| 1 | **Phạm Hồng Quân** | SE161574 | Leader & Backend Developer |
| 2 | **Đại Kim Nguyên** | SE151283 | Backend Developer |
| 3 | **Trương Trí Sỹ** | SE173472 | Frontend Developer |
| 4 | **Lê Minh Quân** | SE182901 | Frontend Developer |

* **Giảng viên hướng dẫn:** Phan Minh Tâm.

---

## 💻 Công nghệ cốt lõi (Core Tech Stack)
Phần Backend được thiết kế để đáp ứng yêu cầu khắt khe về bảo mật và khả năng mở rộng của doanh nghiệp:

* **Ngôn ngữ lập trình:** Java 21 (LTS).
* **Framework chính:** Spring Boot 3.x.
* **Cơ sở dữ liệu:**
    * **Relational Database:** PostgreSQL để lưu trữ metadata và quản lý thông tin hệ thống.
    * **Vector Database:** Tích hợp extension **pgvector** để lưu trữ và truy vấn embeddings cho RAG.
* **Trí tuệ nhân tạo (AI):** Spring AI tích hợp các Large Language Models (LLMs) hỗ trợ tiếng Việt.
* **Bảo mật:** Spring Security & JWT (JSON Web Token) để quản lý phiên đăng nhập và định danh người dùng.
* **Quản lý quyền:** Hệ thống Role-Based Access Control (RBAC) hỗ trợ phân quyền truy cập tài liệu theo phòng ban.

---

## 💳 Thanh toán (Payment)
*(Hiện tại mục này đang để trống theo kế hoạch phát triển của nhóm)*

---
© 2026 **Group GSP26SE114** - FPT Education.
