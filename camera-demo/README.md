# Rifd camera demo / عرض كاميرا رفد

## العربية

صفحة مستقلة تعرض الكاميرا مع مربعات حول الأشخاص وعدّاد. يعمل الكشف على الجهاز باستخدام COCO-SSD وTensorFlow.js داخل worker. عند استمرار بلوغ العدد المحدد، ترسل الصفحة لقطة JPEG أصلية وتنبيهًا إلى رفد. تتضمن الواجهة زرًا للتبديل بين العربية والإنجليزية.

التنبيه من المصدر `CCTV` والفئة `CROWD`، وقرار إنشائه قاعدة `SYSTEM` بعد بلوغ العدد، وليس حكمًا من ExpoGuard أو Gemini. الثقة غير مقاسة وتبقى `null`. تبدأ الخطورة بـ`MEDIUM`، وقد تغيرها قواعد الحادث الموجودة. يستمر الدمج والتوصيات واعتماد الإرسال وتحديث المهام عبر المنطق الحالي. لا تتغير واجهات البلاغات الأخرى.

الإعداد، بعد الموافقة على التنزيل والتشغيل:

1. من جذر المستودع، نفّذ `python3 camera-demo/prepare-assets.py` مرة واحدة مع اتصال بالإنترنت. ينزّل مكتبات بإصدارات محددة وملفات النموذج وتراخيصها إلى هذا المجلد، ولا يثبّت حزمًا. يسجل المصادر وأحجام الملفات وSHA256 في `assets-manifest.json`؛ هذه بصمات للملفات المستلمة وليست تحققًا من توقيع الناشر.
2. فعّل `camera-demo.enabled=true` عند تشغيل الواجهة الخلفية. المسار غير موجود افتراضيًا. استخدم قاعدة بيانات عرض مخصصة؛ الإعداد الأصلي `create-drop` يعيد إنشاء البيانات، لذا يحدد المثال أدناه `update`.
3. شغّل خادم الملفات المحلي، وافتح `http://localhost:5174` في متصفح يدعم الكاميرا وWeb Workers.
4. أدخل عنوان خادم رفد، ثم اضغط «تحميل المناطق» واختر منطقة موجودة. عيّن معرّف الكاميرا وحد التنبيه، ثم شغّل الكاميرا وامنح الإذن.
5. افتح لوحة العمليات بجانب صفحة الكاميرا. مصدر تحديثات الخلفية هو STOMP الحالي؛ يجب أن تكون اللوحة متصلة به. لم تراجع هذه المهمة كود الواجهة React المصاحبة.

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments="--camera-demo.enabled=true --spring.jpa.hibernate.ddl-auto=update"
python3 -m http.server 5174 --bind 127.0.0.1 --directory camera-demo
```

الافتراضات القابلة للتعديل: ٣ أشخاص، واستمرار ١٫٥ ثانية، وفاصل ١٥ ثانية، وثقة كشف ٠٫٥٥. يعاد تفعيل التنبيه بعد انخفاض العدد لمدة ثانيتين وانتهاء الفاصل. يحتفظ العرض باللقطة المعلقة للمحاولة مجددًا بالمعرّف نفسه، ولا يرسل أحداثًا جديدة أثناء تعليقها. تحفظ إعادة المحاولة النتيجة السابقة في عملية خلفية واحدة ما دامت سجلات البلاغ موجودة؛ هذا ليس ضمانًا عبر عدة نسخ متزامنة من الخادم.

الكشف من إطارات منفردة، وليس تتبعًا لهويات الأشخاص. حد ١٠٠ صندوق لكل إطار ليس قياسًا معتمدًا لسعة الحشود. يعتمد الأداء على الجهاز والمتصفح؛ لا توجد نتيجة FPS أو دقة مقاسة لهذه الإضافة بعد. تخفي الصفحة المربعات والعدد القديم، وتوقف الإرسال التلقائي أثناء إخفاء التبويب. البيانات التجريبية والتفعيل اليدوي مطلوبان، ولا يلزم استخدام خدمات AI الخارجية لهذا المسار. يحتفظ رفد باللقطات وفق سياسة الوسائط الموجودة؛ استخدمها في العرض بموافقة المشاركين.

التحقق في 2026-10-16: راجعت صياغة JavaScript دون تنفيذه، وبنية Python ومرجعيات HTML وتسميات اللغتين. نُزّلت ملفات العرض العشرة، بحجم إجمالي 20,063,700 بايت، وتحققت من أحجامها وبصمات SHA256 المسجلة ووجود أجزاء النموذج الخمسة. نجحت الاختبارات المستهدفة الأربعة عشر باستخدام Maven 3.9.16 المخزن محليًا في الوضع غير المتصل `-o` وJDK 21، مع ترجمة المشروع إلى Java 17. تعذرت تهيئة وكيل Mockito داخل البيئة المعزولة أولًا، ثم نجحت إعادة الاختبارات خارجها. لم تُشغّل خدمات أو كاميرا، ولم يُتحقق من العرض المباشر أو أدائه أو تحديث لوحة العمليات في المتصفح. إذا كانت `CORS_ORIGINS` مقيدة، أضف عنوان صفحة العرض إلى إعدادها قبل التشغيل. يبقى الوصول إلى المرفق تحت صلاحيات لوحة العمليات عند تفعيل الأمان.

## English

A standalone page shows a live camera, person boxes, and a counter. COCO-SSD and TensorFlow.js run on the device in a worker. A sustained count threshold sends an original JPEG frame and an alert to Rifd. The interface includes an Arabic/English switch.

The alert has source `CCTV` and category `CROWD`. A `SYSTEM` count rule creates it; ExpoGuard and Gemini do not judge this demo path. Confidence is unmeasured and remains `null`. Severity starts at `MEDIUM`; existing incident rules may adjust it. Existing merging, recommendations, dispatch approval, and task updates continue. Other report endpoints retain their behavior.

Setup, after download and execution approval:

1. From the repository root, run `python3 camera-demo/prepare-assets.py` once with internet access. It downloads pinned libraries, model files, and licenses into this folder without installing packages. `assets-manifest.json` records sources, sizes, and SHA256 values of received files; these are reproducibility records, not publisher-signature verification.
2. Enable `camera-demo.enabled=true` when starting the backend. The endpoint is absent by default. Use a dedicated demo database; the original `create-drop` default recreates data, so the command above explicitly selects `update`.
3. Start the local static server using the command above and open `http://localhost:5174` in a browser supporting camera access and Web Workers.
4. Enter the Rifd server URL, load zones, select an existing zone, set the camera ID and threshold, then start the camera and grant permission.
5. Keep the operations dashboard visible beside the camera page. The backend uses its existing STOMP updates; the dashboard must subscribe to them. This task has not reviewed the companion React code.

Editable defaults: 3 people, a 1.5-second hold, a 15-second cooldown, and 0.55 person-detection confidence. Re-arming requires 2 seconds below threshold and expiry of the cooldown. A failed upload retains its snapshot and event ID for retry, blocking new events meanwhile. Retries recover a previous result within one backend process while its alert record remains; this is not a multi-instance exactly-once guarantee.

Detection is per-frame, without identity tracking. The 100-box limit is not a validated crowd capacity. Speed depends on the device and browser; no measured FPS or accuracy is claimed for this implementation. Stale boxes/counts are hidden, and automatic submissions pause while the tab is hidden. Demo data and explicit enablement are required; this path needs no external AI service. Snapshots follow Rifd's existing media retention behavior; use the demo with participants' consent.

Verified on 2026-10-16: JavaScript syntax parsed without executing the scripts; Python syntax, HTML references, and bilingual labels inspected. All 10 assets were downloaded (20,063,700 bytes total); their sizes and recorded SHA256 values were checked, including the presence of all five model shards. All 14 targeted tests passed using cached Maven 3.9.16 in offline mode (`-o`) and JDK 21, compiling the project for Java 17. Mockito's agent could not initialize inside the sandbox on the first attempt; the rerun outside it passed. No services or camera were started; live browser behavior, performance, and dashboard updates remain unverified. If `CORS_ORIGINS` is restricted, include the demo origin before running. Existing dashboard permissions still protect attachment access when security is enabled.

## Technical contract

Sources: [controller](../src/main/java/com/example/rifd/Controller/CameraDemoController.java), [validation and attachment storage](../src/main/java/com/example/rifd/Service/CameraDemoService.java), and [existing alert pipeline](../src/main/java/com/example/rifd/Service/AlertService.java).

`POST /api/v1/demo/camera-alerts`, `multipart/form-data`:

| Field | Requirement |
|---|---|
| `photo` | Decodable JPEG, at most 5 MiB and 12 million pixels |
| `cameraId` | 1–64 ASCII letters, digits, `_` or `-` |
| `zoneCode` | Existing zone, matched after trimming and uppercasing |
| `peopleCount` | Integer, 1–100, at least `threshold` |
| `threshold` | Integer, 1–100; demo rule configured in the page |
| `eventId` | UUID, reused for retries of the same frame |

Returns the existing `AlertDTOOut`. The photo is stored in `MediaFile` and linked as `up:<id>`. `GET /api/v1/alerts/{id}/attachment` serves it using existing authorization. A repeated completed event returns its previous alert. A committed `NEW` alert after a processing failure returns an error directing the operator to inspect the dashboard. No general-purpose category override was added to public alert DTOs.

`CameraDemoController` and `CameraDemoService` are conditional on `camera-demo.enabled=true`. The public POST rule follows existing external-source ingestion; keep the flag off outside the local showcase. No schema migration, Java dependency, frontend package installation, or React build is required.

Targeted test command (wrapper equivalent of the approved cached-Maven run):

```sh
./mvnw -o -Dtest=CameraDemoServiceTest,CameraDemoIngestionTest,CameraDemoControllerTest test
```

The wrapper and dependencies must already be cached for an offline run. Existing ignore rules may exclude new files under `src/test/`; no Git state or root ignore rule is changed by this implementation.

Official references: [COCO-SSD API and box format](https://github.com/tensorflow/tfjs-models/tree/master/coco-ssd), [TensorFlow.js browser execution](https://www.tensorflow.org/js/guide/platform_environment), [camera permissions and localhost](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).
