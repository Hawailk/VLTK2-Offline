# VLTK2 Offline

Game RPG offline 2D pixel art, viết bằng Java ME MIDP 2.0 cho J2ME Loader. Nhân vật, quái, cây và địa hình được dựng bằng sprite/palette pixel tự vẽ; không cần kết nối mạng khi chơi và không sử dụng tài nguyên trích xuất từ game khác.

## Build

Yêu cầu JDK 8 trở lên, Apache Ant và kết nối mạng lần đầu để tải thư viện API MicroEmulator từ Maven Central.

```sh
ant clean jar
```

Build tạo `dist/VLTK2Offline.jar` cùng `dist/VLTK2Offline.jad`. JAR game chỉ chứa mã game; thư viện MicroEmulator được dùng làm classpath biên dịch, không được đóng gói vào JAR.

## Chạy trên Android

1. Chép `dist/VLTK2Offline.jar` vào thiết bị Android.
2. Mở J2ME Loader, chọn thêm game và chọn JAR đó (hoặc import JAD đi kèm).
3. Chạy game ở chế độ màn hình dọc hoặc ngang. D-pad góc trái để di chuyển, nút kiếm góc phải để đánh.

## Điều khiển và tiến trình

- D-pad cảm ứng hoặc phím điều hướng để di chuyển; nút kiếm hoặc phím chọn để đánh quái ở gần.
- Quái đuổi theo và gây sát thương. Hạ quái nhận EXP; đủ EXP sẽ lên cấp và hồi sinh lực.
- Nhiệm vụ yêu cầu hạ 8 quái rồi đi tới cổng ở góc đông nam; sprite có nhịp bước, hiệu ứng chém và chớp khi trúng đòn.
- Bản đồ mini ở góc trên phải đánh dấu vị trí nhân vật, quái, hồ và cổng thoát để dễ tìm đường.
- Hoàn thành nhiệm vụ hiện màn chiến thắng; chạm màn hình hoặc nhấn phím để bắt đầu lượt mới. Trạng thái chiến thắng được lưu offline.
- Tiến trình cấp, EXP, sinh lực và vị trí được lưu cục bộ bằng MIDP RecordStore khi hạ quái, định kỳ và khi ứng dụng tạm dừng/đóng.
- Khi bị hạ, nhân vật hồi sinh tại điểm xuất phát. Bản đồ cuộn theo nhân vật trong một vùng thế giới rộng.

## Thư viện

Build lấy `org.microemu:microemulator:2.0.4` từ Maven Central vào `build/deps/`. Đây là API MIDP dùng để biên dịch; J2ME Loader cung cấp môi trường MIDP trên Android.
