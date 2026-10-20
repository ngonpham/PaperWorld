# Phi Đội Giấy · Đà Nẵng — bản Android (Kotlin)

Bản port native của [Phi Đội Giấy](../phidoi/) sang **Kotlin + OpenGL ES 3.0**, không dùng WebView. Toàn bộ engine (scene graph, toon shading, viền mực, đổ bóng, instancing, post-process giấy màu nước), thế giới Đà Nẵng – Hội An dựng tay, mô hình bay, không chiến, nhiệm vụ, HUD, nút cảm ứng và nhạc ngũ cung tự sinh đều được viết lại, bám sát từng dòng của bản web để cảm giác chơi giống hệt.

## Build

Cần JDK 17+ và Android SDK (compileSdk 35). Tạo `local.properties` với `sdk.dir=/đường/dẫn/android-sdk`.

```sh
cd android
./gradlew :app:assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # ký bằng debug key, đủ để cài thử (adb install …)
```

Yêu cầu máy: Android 8.0+ (API 26), GPU hỗ trợ OpenGL ES 3.0. Game chạy ngang màn hình, chất lượng mặc định **VỪA** trên điện thoại (đổi trong ⚙ Cài đặt, mở lại game để áp dụng).

## Cấu trúc

| Module | Nội dung |
| --- | --- |
| `core/` | Kotlin thuần (JVM), không phụ thuộc Android — chạy được cả trên desktop để test |
| `core/…/engine/` | Toán học (Vec3, Quat, Mat4…), `Geometry` + bộ sinh hình (box, cylinder, sphere, lathe, tube…), scene graph, vật liệu, `Renderer` GLES3 (toon 3 nấc, viền mực inverted-hull, shadow map PCF, instancing, sort trong suốt), `Pipeline` (MSAA → post-process: viền mực theo độ sâu, nhòe màu nước, vân giấy, mép giấy xé), `Canvas2D` + `RecordingCanvas` |
| `core/…/world/` | Địa lý (`Geog.sample` — bờ biển, sông, Sơn Trà, Ngũ Hành Sơn, núi), lưới 20 m, địa hình 3 cấp chi tiết, biển, trời, giờ trong ngày, các landmark (`World+City.kt`, `World+SonTra.kt`, `World+Coast.kt`, `World+South.kt`), instancing theo ô 800 m, collider, **gộp mesh tĩnh** (`Batch.kt`) để giảm draw call |
| `core/…/game/` | `Game` (trạng thái toàn cục như module scope của bản web), mô hình bay (`Flight.kt`), hạt / gió / thermal / mây (`Life.kt`), vật thể chuyển động (`Moving.kt`), không chiến (`Combat.kt`, `Weapons.kt`, `Enemies.kt`), nhiệm vụ (`Missions.kt`), camera + vòng lặp (`Loop.kt`), giao diện Canvas (`Ui.kt`, HUD, bản đồ, menu), âm thanh tổng hợp (`Audio.kt`), `GameHost` (dựng thế giới từng bước, khung hình, input) |
| `app/` | Android: `GameActivity` (GLSurfaceView chạy game trên luồng GL; HUD được ghi vào `RecordingCanvas` rồi vẽ lại trên luồng UI bằng `HudView`), `AndroidGL` (GLES30), `AndroidCanvas` (android.graphics), `AudioTrackOut`, `PrefsStore` (SharedPreferences) |
| `desktop/` | Harness test không màn hình: LWJGL + EGL (ANGLE/SwiftShader) → chụp PNG; AWT cho Canvas2D |

Số ngẫu nhiên dùng đúng mulberry32 (seed 20261003) và simplex noise như bản web, gọi theo cùng thứ tự, nên cây cối, nhà cửa, mây… mọc đúng chỗ như trên web.

## Test trên desktop

```sh
cd android
./gradlew :desktop:run -Pargs=world -Dpd.cam=-250,90,-1000 -Dpd.look=-20,10,-1300 -Dpd.out=/tmp/world.png
./gradlew :desktop:run -Pargs=game "-Dpd.script=mission:m1;sim:3" -Dpd.sec=0.5 -Dpd.out=/tmp/game.png
./gradlew :desktop:run -Pargs=combat     # kiểm tra súng hội tụ, tên lửa khoá & không xuyên, pháo sáng, boss, tiếp tế, bot thắng m1
./gradlew :desktop:run -Pargs=audio -Dpd.out=/tmp/audio.wav
```

Harness cần Chromium/ANGLE (SwiftShader) có sẵn trong môi trường build; mỗi khung hình render mất ~0,7 s bằng GPU phần mềm.

## Điều khiển (cảm ứng)

Cần trái: ngóc/chúc mũi & nghiêng cánh · **BẮN** giữ để bắn · **🚀** tên lửa · **✦** pháo sáng · **🎯** đổi mục tiêu · **🎥** góc máy · **ĐỐT SAU** tăng tốc · **PHANH** · kéo màn hình để nhìn quanh · ⌖ bản đồ · ❚❚ tạm dừng · ⚙ cài đặt. Bàn phím / tay cầm Bluetooth dùng phím như bản web (W/S A/D Q/E, SHIFT, X, SPACE, F, G, TAB, V, C, M, ESC).

## Khác với bản web

- Reverb là mạng Freeverb thay cho convolution (nhẹ hơn cho điện thoại); compressor đơn giản hoá.
- Các landmark tĩnh được gộp theo vật liệu lúc dựng thế giới; phần chuyển động (vòng quay, đầu rồng, đèn hải đăng, cáp treo…) được dò tự động và giữ nguyên.
- Độ phân giải 3D tự giảm/tăng theo FPS (như `autoRes` bản web).
