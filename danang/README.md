# Cánh Giấy · Đà Nẵng

**Gấp cánh giấy. Rồi bay qua phố biển.**

Game bay màu nước, gấp bằng giấy, gói trong một file HTML duy nhất (chạy bằng [three.js](https://threejs.org)). Đây là game anh em với Paper World, nhưng tập trung hoàn toàn vào **bay**, lấy bối cảnh du lịch biển Đà Nẵng: Mỹ Khê, bán đảo Sơn Trà, Ngũ Hành Sơn và phố cổ Hội An.

**▶ Chơi:** mở thư mục `danang/` trên cùng host với Paper World (ví dụ `…/PaperWorld/danang/`).

## Bản đồ

Một vùng Đà Nẵng – Hội An được dựng tay theo địa lý thật, thu nhỏ khoảng 1:5 theo chiều ngang, độ cao được phóng đại:

| Vùng | Có gì |
| --- | --- |
| **Biển Mỹ Khê** | Bãi cát trắng, dù che, thúng chai, cứu hộ, đội cano kéo dù lượn dọc bờ biển, hải âu, khách sạn ven biển |
| **Thành phố** | Nhà cửa rực rỡ tông cam – xanh Ahamove theo phong cách Paper World, toà **Ahamove Sky Hub** có sân đỗ trực thăng trên nóc, bãi đáp ở Mỹ Khê, Hội An và cảng Tiên Sa, trực thăng giao hàng Ahamove bay qua lại |
| **Sông Hàn** | Cầu Rồng (về đêm phun lửa, phun nước, đổi màu), Thuận Phước, Sông Hàn, Trần Thị Lý, Vòng quay Mặt Trời, Cá chép hoá rồng, Nhà thờ Con Gà, sân bay có máy bay cất/hạ cánh |
| **Bán đảo Sơn Trà** | Chùa Linh Ứng và tượng Quan Âm, trạm radar trên đỉnh, đỉnh Bàn Cờ (bãi cất cánh dù lượn), hải đăng Tiên Sa, Cây Đa ngàn năm, cảng Tiên Sa, đàn voọc chà vá chân nâu |
| **Ngũ Hành Sơn** | Năm ngọn núi đá vôi Kim – Mộc – Thủy – Hỏa – Thổ, chùa Tam Thai, động Huyền Không, làng đá Non Nước |
| **Hội An** | Phố cổ tường vàng mái ngói, Chùa Cầu, Hội quán Phúc Kiến, đèn lồng, hoa đăng trên sông Thu Bồn, chợ đêm, rừng dừa Bảy Mẫu, làng rau Trà Quế, biển An Bàng – Cửa Đại |
| **Xa hơn** | Cù Lao Chàm, đèo Hải Vân, Cầu Vàng Bà Nà và cáp treo, đồng lúa Quảng Nam có nón lá và trâu |

## Bay

Năm phương tiện: bốn loại cánh dùng chung một mô hình vật lý point-mass (lực nâng, hệ số tải `n`, thất tốc, lực cản cảm ứng, năng lượng), cộng trực thăng có mô hình bay riêng:

| Cánh | Đặc điểm |
| --- | --- |
| ✈ **Máy bay giấy** | Lượn nhanh, tăng tốc bằng “sức gió”, nhào lộn, nảy thia lia trên mặt nước |
| 🪂 **Dù lượn** | Chậm, không có động cơ: sống nhờ cột khí nóng và gió sườn núi, điều chỉnh bằng thanh tốc độ và phanh |
| 🐦 **Chim yến** | Vỗ cánh để leo, xếp cánh để bổ nhào, rẽ gắt nhất |
| 🛩 **Thủy phi cơ** | Có động cơ, đáp xuống biển và cất cánh lại từ mặt nước |
| 🚁 **Trực thăng Ahamove** | Lơ lửng tại chỗ, tiến/lùi, đi ngang, lên/xuống; đáp lên nóc nhà và bãi đáp (Hỗ trợ bay tự giảm tốc khi sắp chạm) |

- **Không khí sống động:** gió biển thổi từ hướng Đông – Đông Nam ban ngày (gió đất về đêm); sườn núi đón gió tạo lực nâng; 25 cột khí nóng nghiêng theo gió, mạnh nhất buổi trưa, có mây tích đánh dấu; mây có thể bay xuyên qua.
- **Đồng hồ bay:** tốc độ, chân trời nhân tạo, variometer kèm tiếng bíp như dù lượn thật, độ cao, la bàn, cảnh báo **THẤT TỐC** và **KÉO LÊN!**
- **Hỗ trợ bay** (bật mặc định): tự cân cánh, giữ tốc độ, chống thất tốc, tránh địa hình. Tắt đi để bay như thật (có dao động phugoid, rơi khi thất tốc).
- **Hạ cánh** êm xuống bãi cát, **đáp nước** bằng thủy phi cơ; va chạm thì “nhàu giấy” và gấp lại từ điểm an toàn gần nhất.
- **Năm góc máy:** bám đuôi, gần, buồng lái, điện ảnh (flyby), toàn cảnh.

## Mục tiêu

- **8 tuyến bay** có huy chương vàng/bạc/đồng và kỷ lục: Luồn gầm cầu sông Hàn, Vòng quanh Sơn Trà, Năm ngọn Ngũ Hành, Phố Hội đèn lồng, Lướt sóng Mỹ Khê, Đại lộ ven biển Sơn Trà → Hội An, Ra đảo Cù Lao Chàm, Cầu Vàng trên mây.
- **34 dấu mộc địa danh** trong sổ tay hành trình, **12 tổ yến vàng** giấu trên vách đá, **19 thử thách** (rồng phun lửa, đáp trực thăng lên Ahamove Sky Hub, vòng quanh Mẹ Quan Âm, cao nghìn mét, ném thia lia, phố Hội về đêm…), bưu thiếp khi chụp ảnh đúng địa danh.
- Tiến trình được lưu trên thiết bị.

## Điều khiển

| Phím | Tác dụng |
| --- | --- |
| W / S · ↑ ↓ | Ngóc / chúc mũi (có thể đảo trục) |
| A / D · ← → | Nghiêng cánh để rẽ |
| Q / E | Bánh lái |
| Shift | Tăng tốc · thanh tốc độ · ga tối đa |
| X | Phanh gió · thắng dù · giảm ga |
| Space | Vỗ cánh · nhào lộn (Space = lộn xoắn, Space + W = lộn vòng) · cất cánh lại |
| 1 – 5 | Đổi phương tiện (5 = trực thăng Ahamove) |
| Trực thăng | W/S tiến · lùi, A/D xoay, Q/E đi ngang, Space lên, X xuống, Shift bay nhanh |
| C | Đổi góc máy |
| M · L · J | Bản đồ (bấm để bay tới) · tuyến bay · sổ tay |
| T · 0 · K | Đổi giờ · tự động · mưa |
| P · H | Chụp ảnh · ẩn giao diện |
| R · Esc | Bay lại · bỏ tuyến / đóng bảng |
| B · N · V | Nhạc bật/tắt · bài kế · âm vario |
| Chuột | Kéo để ngắm, cuộn để zoom |

**Điện thoại:** cần analog bên trái, các nút VỖ CÁNH / TĂNG TỐC / PHANH / đổi cánh / góc máy / chụp ảnh bên phải; có thể bật lái bằng nghiêng máy trong Cài đặt. **Tay cầm:** cần trái lái, cần phải ngắm, RT tăng tốc, LT phanh, A vỗ cánh, Y góc máy, X đổi cánh.

## Chạy trên máy

Không cần build. Chạy server tĩnh ở thư mục gốc repo:

```bash
python3 -m http.server 8431
```

Rồi mở http://localhost:8431/danang/.
