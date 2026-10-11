# Handoff — مشروع تحدي إكسبو 2030 الرياض
# 🟦 المطوّر 1: الاستقبال والحوادث (Intake & Incidents)

> هذا الملف لـ **المطوّر 1 (باك إند)** في فريق من 5. يلخص المشروع كامل + الجزء المسؤول عنه بالتفصيل + عقد التكامل مع المطوّر 2.
> المستخدم يتواصل بالعربي (لهجة سعودية)، ويفضّل المحتوى الجاهز للنسخ واللصق (كل عنصر أو ملف كود في بوكس كود منفصل).
> **عندك ملف شقيق** عند المطوّر 2 (الاستجابة وذوي الإعاقة). الأقسام 1–14 متطابقة في الملفين حرفيًا، والقسم 15 خاص بك.
> **لا تقترح تعديل** الكلاسات أو الـ Enums أو عقد التكامل (القسم 9) بدون تنبيه المستخدم إنه لازم يتفق مع المطوّر 2 أول.
---

## 1. معلومات التحدي (من الدليل الإرشادي)

- **التحدي:** 15 – 17 أكتوبر 2026، أكاديمية طويق، الرياض. الحضور إلزامي للجميع.
- **اليوم 1 (الخميس 15):** الانطلاق وجولات الإرشاد.
- **اليوم 2 و 3 (16 – 17):** التسليم النهائي على المنصة + عرض أمام لجنة التحكيم.
- **الحفل الختامي:** السبت 17 أكتوبر.
- **التسليم المتأخر = استبعاد الفريق.**
- **المطلوب:** نموذج أولي (Prototype) يثبت الجدوى + ملف مشروع + عرض تقديمي **5 دقائق**. لا يُشترط اكتمال المنتج.
- **معايير التحكيم:** الابتكار 25%، الجدوى وقابلية التطبيق 25%، الجودة التقنية وتكامل التخصصات 20%، الارتباط بمستهدفات إكسبو 20%، جودة العرض 10%.
- قناة التواصل الرسمية: ديسكورد. (نقطة مفتوحة: هل يُسمح بكتابة الكود قبل الانطلاق.)

---

## 2. الفكرة

**التحدي المختار: 3.6** (المسار 3: تشغيل المعرض وسلامته وأمنه)
> مركز عمليات المعرض يصعب عليه تمييز ما يستدعي تدخلًا فوريًا أثناء فعالية مباشرة، عندما ترد مئات التنبيهات والرسائل اللاسلكية وطلبات الخدمة وإشعارات الأنظمة من جهات منفصلة.

**الحل:** منصة ذكية لمركز العمليات:
- توحّد التنبيهات من المصادر المختلفة في شاشة واحدة.
- الذكاء الاصطناعي يفرزها حسب الخطورة والنوع.
- يدمج المتكرر في حادثة واحدة على خريطة المعرض.
- يقترح الإجراء المناسب من الـ Playbooks.

**ميزتان إضافيتان:**
- **(3.3)** إرسال المهمة لأقرب موظف **معتمد** ومتاح حسب **تخصصه**، ومتابعة التنفيذ.
- **(3.5)** تنبيه فريق ذوي الإعاقة وتحديث المسارات المعروضة للزوار إذا تأثرت المسارات المهيأة.

**الفريق (5):** 2 باك إند (Spring Boot) ويشتغلون فرونت كمان، 1 UI/UX، 2 علم بيانات وذكاء اصطناعي (AI 1: التصنيف واقتراح الإجراء، AI 2: مولّد البيانات والدمج).

**رسالة العرض:** "من 40 تنبيه إلى 3 حوادث".

---

## 3. القرارات المتخذة

1. **حماية النطاق:** الجوهر (3.6) يشتغل فعليًا من البداية للنهاية. الميزتان الإضافيتان تُنفذان كمسار واحد يعمل داخل سيناريو العرض.
2. **سيناريو العرض يُكتب أولًا** وكل شيء يخدمه (التفاصيل في القسم 12).
3. **البيانات اصطناعية:** مولّد تنبيهات واقعية (عربي/إنجليزي، تكرارات مقصودة) + Seed Data.
4. **بديل بالقواعد (rule-based fallback)** لو انقطع الإنترنت أو API الذكاء الاصطناعي.
5. **الخريطة تخيّلية** بإحداثيات x, y (ليست خطوط طول وعرض). نعتبر الوحدة = متر.
6. **لا يوجد Certification:** بداله `specialty` + `level` (CERTIFIED / ASSISTANT) داخل Staff. المساعد يُرسل في الحوادث البسيطة أو مرافقًا للمعتمد.
7. **ActivityLog** للشفافية، عام لكل الكيانات، ويميّز بين قرارات AI والإنسان والنظام.
8. **Recommendation** كلاس مستقل **تجريبي** (ممكن يُدمج لاحقًا في Incident).
9. مسار ذوي الإعاقة يُحسب بـ **Dijkstra** وليس AI، فلا يُقدَّم للجنة كذكاء اصطناعي.

### ما تم حذفه أو تحويله إلى attributes
| المقترح سابقًا | المصير |
|---|---|
| Certification | حُذف، بداله specialty + level |
| Shift | `shiftStart`, `shiftEnd` داخل Staff |
| IncidentReview | `actualSeverity`, `resolutionNotes` داخل Incident |
| Team | حُذف (مكرر مع specialty) |
| User | دُمج في Staff عبر `role` |
| EscalationRule | إعدادات في application.properties |
| Attachment | `attachmentUrl` داخل Alert |
| Device | غير مضاف، بداله `sourceReference` داخل Alert |
| OccupancyReading | غير مضاف، فقط لو قررنا التنبؤ بالازدحام |

---

## 4. تقسيم الشغل بين المطوّرَين

التقسيم **حسب مراحل سير الحادثة**، كل مطوّر يملك ميزته كاملة (Entity → Repository → Service → Controller → DTO).

| | المطوّر 1: الاستقبال والحوادث | المطوّر 2: الاستجابة وذوي الإعاقة |
|---|---|---|
| **الكلاسات** | Zone, Event, Alert, Incident, ActivityLog | Staff, Playbook, Recommendation, Task, Notification, AccessibilityAsset, AccessibleRoute |
| **الباكجات** | `intake`, `incident`, `activitylog` | `response`, `staff`, `accessibility`, `notification` |
| **المهام** | استقبال التنبيهات، ربط AI للتصنيف والدمج، الـ fallback، إنشاء/دمج الحوادث، أثر الفعاليات، APIs الحوادث، إعداد WebSocket، ActivityLogService، محاكي السيناريو، إحصائيات الشاشة | توليد Recommendation، القبول/الرفض، إسناد أقرب موظف معتمد، دورة المهمة، التصعيد، مسار ذوي الإعاقة و Dijkstra، الإشعارات، تسجيل الدخول |
| **Seed Data** | المناطق والفعاليات (أولًا) | الموظفين، Playbooks، الأصول، المسارات |

### قواعد العمل المشتركة
- **لا أحد يعدّل باكجات الثاني.** لو تحتاج شي من عند صاحبك، استخدم الـ Service العامة اللي يوفرها (القسم 9) أو اطلبها منه.
- **`common/`** (BaseEntity، Enums، الإعدادات، WebSocketConfig) تُكتب سوا في البداية، وبعدها **أي تعديل عليها باتفاق الاثنين** لأنه يكسر شغل الثاني.
- قراءة Repository من باكج الثاني مسموحة **للقراءة فقط** إذا ما فيه Service توفرها، والكتابة دائمًا عن طريق Service صاحب الباكج.
- **Git:** فرع `main` محمي، كل ميزة في فرع (`feature/intake-merge`، `feature/response-assign` ...). PR صغير ومتكرر، ودمج يومي على الأقل.
- الـ DTOs داخل باكج صاحبها. لا ترجع Entity مباشرة من Controller (تجنب حلقات JSON بين العلاقات).

---

## 5. التقنيات والاتفاقيات (مقترحة)

- Java 17، Spring Boot 3، Spring Data JPA، Spring Web، Spring WebSocket (STOMP)، Spring Security، Validation، Lombok.
- قاعدة البيانات: PostgreSQL (أو H2 للتطوير السريع). `ddl-auto=update` في التطوير.
- خدمة الذكاء الاصطناعي: Python (FastAPI) منفصلة، الباك إند يناديها عبر HTTP (`RestClient` أو `WebClient`) مع timeout.
- الوقت: `LocalDateTime` بتوقيت الرياض.
- كل الـ Enums تُخزّن `@Enumerated(EnumType.STRING)`.
- العلاقات `FetchType.LAZY` افتراضيًا.

### هيكل الباكجات
```
com.expo.ops
├── common/        ← BaseEntity, enums, config, WebSocketConfig, exceptions (مشترك)
├── intake/        ← Alert, AiClient, RuleBasedClassifier, MergeService, Simulator  (مطوّر 1)
├── incident/      ← Incident, Zone, Event, IncidentService, events                 (مطوّر 1)
├── activitylog/   ← ActivityLog, ActivityLogService                                 (مطوّر 1)
├── response/      ← Recommendation, Playbook, Task, AssignmentService, Escalation  (مطوّر 2)
├── staff/         ← Staff, auth                                                     (مطوّر 2)
├── accessibility/ ← AccessibilityAsset, AccessibleRoute, RouteFinder (Dijkstra)     (مطوّر 2)
└── notification/  ← Notification, NotificationService                               (مطوّر 2)
```

### BaseEntity
```java
@MappedSuperclass
@Getter @Setter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### إعدادات application.properties المقترحة
```properties
# AI
ai.base-url=http://localhost:8000
ai.timeout-ms=3000
ai.enabled=true

# الدمج (مطوّر 1)
merge.threshold=0.6
merge.max-distance=80
merge.time-window-minutes=15

# الفعاليات (مطوّر 1)
event.severity-boost-radius=100

# الإسناد والتصعيد (مطوّر 2)
assignment.walking-speed-mps=1.3
escalation.accept-timeout-seconds=120
escalation.check-interval-ms=30000

# ذوي الإعاقة (مطوّر 2)
accessibility.asset-match-radius=40
```

---

## 6. نموذج البيانات (12 كلاس + BaseEntity)

كل الكلاسات ترث من **BaseEntity**. المالك مكتوب بجانب كل كلاس.

### Zone — مناطق المعرض (مطوّر 1)
```
- code: String
- name: String
- type: ZoneType
- polygonCoordinates: String
- centerX: Double
- centerY: Double
- capacity: Integer
- currentOccupancy: Integer
```

### Alert — التنبيه الخام كما وصل (مطوّر 1)
```
- source: AlertSource
- sourceReference: String
- rawText: String
- language: String
- attachmentUrl: String
- x: Double
- y: Double
- zone: Zone
- receivedAt: LocalDateTime
- processedAt: LocalDateTime
- aiSeverity: Severity
- aiCategory: IncidentCategory
- aiConfidence: Double
- status: AlertStatus
- incident: Incident
- mergeScore: Double
```

### Incident — الحادثة الناتجة عن الدمج، قلب النظام (مطوّر 1)
```
- code: String
- title: String
- summary: String
- category: IncidentCategory
- severity: Severity
- status: IncidentStatus
- zone: Zone
- event: Event
- x: Double
- y: Double
- alertCount: Integer
- affectsAccessibility: Boolean
- accessibilityAsset: AccessibilityAsset
- acknowledgedAt: LocalDateTime
- resolvedAt: LocalDateTime
- resolutionNotes: String
- actualSeverity: Severity
```

### Event — الفعاليات المباشرة (مطوّر 1)
```
- name: String
- type: EventType
- zone: Zone
- startTime: LocalDateTime
- endTime: LocalDateTime
- expectedAttendance: Integer
```

### ActivityLog — سجل كل تغيير (مطوّر 1)
```
- entityType: EntityType
- entityId: Long
- incident: Incident
- action: ActionType
- actorType: ActorType
- actor: Staff
- oldValue: String
- newValue: String
- details: String
- timestamp: LocalDateTime
```

### Recommendation — اقتراح AI وقرار الإنسان (مطوّر 2)
```
- incident: Incident
- playbook: Playbook
- actionText: String
- reasoning: String
- requiredSpecialty: StaffSpecialty
- requiredLevel: StaffLevel
- staffCount: Integer
- confidence: Double
- status: RecommendationStatus
- rejectionReason: String
- decidedBy: Staff
- decidedAt: LocalDateTime
```

### Staff — الموظفون والمشغّلون (مطوّر 2)
```
- employeeNumber: String
- name: String
- phone: String
- role: StaffRole
- specialty: StaffSpecialty
- level: StaffLevel
- status: StaffStatus
- homeZone: Zone
- currentX: Double
- currentY: Double
- lastLocationUpdate: LocalDateTime
- shiftStart: LocalTime
- shiftEnd: LocalTime
- username: String
- passwordHash: String
- version: Long        (@Version)
```

### Task — المهمة ومتابعتها (مطوّر 2)
```
- incident: Incident
- staff: Staff
- recommendation: Recommendation
- assignedBy: Staff
- status: TaskStatus
- distanceMeters: Double
- estimatedArrivalMinutes: Integer
- assignedAt: LocalDateTime
- acceptedAt: LocalDateTime
- arrivedAt: LocalDateTime
- completedAt: LocalDateTime
- rejectionReason: String
- isEscalated: Boolean
- notes: String
- version: Long        (@Version)
```

### Playbook — الإجراءات المعتمدة (مطوّر 2)
```
- title: String
- category: IncidentCategory
- minSeverity: Severity
- steps: String
- requiredSpecialty: StaffSpecialty
- requiredLevel: StaffLevel
- recommendedStaffCount: Integer
- targetResponseMinutes: Integer
- isActive: Boolean
```

### AccessibilityAsset — المصاعد والمنحدرات والمداخل المهيأة (مطوّر 2)
```
- name: String
- type: AssetType
- zone: Zone
- x: Double
- y: Double
- status: AssetStatus
- lastStatusChange: LocalDateTime
- expectedRestoreTime: LocalDateTime
```

### AccessibleRoute — المسارات المهيأة للزوار (مطوّر 2)
```
- name: String
- fromZone: Zone
- toZone: Zone
- pathCoordinates: String
- lengthMeters: Double
- estimatedMinutes: Integer
- status: RouteStatus
- assets: List<AccessibilityAsset>      (@ManyToMany)
- alternativeRoute: AccessibleRoute     (ذاتية)
- publicMessageAr: String
- publicMessageEn: String
- lastUpdated: LocalDateTime
```

### Notification — إيصال المعلومة (مطوّر 2)
```
- incident: Incident
- type: NotificationType
- recipientStaff: Staff
- recipientSpecialty: StaffSpecialty
- message: String
- priority: Severity
- sentAt: LocalDateTime
- readAt: LocalDateTime
```

> **ملاحظة مهمة على الاعتماد المتبادل:** Incident (مطوّر 1) فيه حقل `accessibilityAsset` (كلاس مطوّر 2)، و ActivityLog (مطوّر 1) فيه `actor: Staff` (كلاس مطوّر 2). لذلك **كل Entities المشروع تُنشأ في اليوم الأول قبل أي منطق**، حتى لو فاضية من المنطق، عشان الكود يبني عند الاثنين.

> `version` في Staff و Task لـ **Optimistic Locking** يمنع مشغّلَين من إسناد نفس الموظف في نفس اللحظة.

---

## 7. الـ Enums (20) — في `common/enums`

```
ZoneType:             PAVILION, GATE, PLAZA, TRANSPORT_HUB, SERVICE_AREA
AlertSource:          CCTV, RADIO, VISITOR_APP, IOT_SENSOR, SERVICE_REQUEST
AlertStatus:          NEW, PROCESSED, MERGED, DISMISSED
IncidentCategory:     CROWD, MEDICAL, FIRE, SECURITY, TECHNICAL_FAILURE, ACCESSIBILITY, LOST_PERSON, CLEANLINESS, OTHER
Severity:             LOW, MEDIUM, HIGH, CRITICAL
IncidentStatus:       OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, FALSE_ALARM
RecommendationStatus: PENDING, ACCEPTED, REJECTED, SUPERSEDED
StaffRole:            FIELD, OPERATOR, SUPERVISOR
StaffSpecialty:       MEDICAL, CROWD_MANAGEMENT, SECURITY, FIRE_SAFETY, ACCESSIBILITY, TECHNICAL, VISITOR_SERVICES
StaffLevel:           CERTIFIED, ASSISTANT
StaffStatus:          AVAILABLE, BUSY, OFF_DUTY
TaskStatus:           PENDING, ACCEPTED, EN_ROUTE, ON_SITE, DONE, REJECTED
EventType:            CEREMONY, CONCERT, PARADE, TALK, EXHIBITION
AssetType:            ELEVATOR, RAMP, ACCESSIBLE_ENTRANCE, ACCESSIBLE_TOILET, SHUTTLE
AssetStatus:          OPERATIONAL, OUT_OF_SERVICE, MAINTENANCE
RouteStatus:          ACTIVE, DISRUPTED
NotificationType:     NEW_TASK, ACCESSIBILITY_ALERT, ESCALATION, ROUTE_CHANGE
EntityType:           INCIDENT, ALERT, TASK, RECOMMENDATION, ROUTE, ASSET
ActorType:            SYSTEM, AI, HUMAN
ActionType:           CREATED, MERGED, SEVERITY_CHANGED, STATUS_CHANGED, ASSIGNED, ACCEPTED, REJECTED, ESCALATED, RESOLVED
```

`IncidentCategory` و `Severity` هي العقد المشترك مع فريق الذكاء الاصطناعي: النموذج يُخرج نفس القيم حرفيًا.

---

## 8. العلاقات (22 + الوراثة)

| من | التعدد | إلى | النوع | ملاحظة |
|---|---|---|---|---|
| Zone | 1 —— * | Alert | Association | |
| Zone | 1 —— * | Incident | Association | |
| Zone | 1 —— * | Event | Association | |
| Zone | 1 ◆—— * | AccessibilityAsset | Composition | |
| Zone | 0..1 —— * | Staff | Association | homeZone |
| Zone | 1 —— * | AccessibleRoute | Association | fromZone |
| Zone | 1 —— * | AccessibleRoute | Association | toZone |
| Incident | 0..1 ◇—— 1..* | Alert | Aggregation | التنبيه موجود قبل الحادثة |
| Incident | 1 ◆—— * | Recommendation | Composition | |
| Incident | 1 ◆—— * | Task | Composition | |
| Event | 0..1 —— * | Incident | Association | |
| AccessibilityAsset | 0..1 —— * | Incident | Association | |
| Playbook | 0..1 —— * | Recommendation | Association | |
| Staff | 0..1 —— * | Recommendation | Association | decidedBy |
| Recommendation | 0..1 —— * | Task | Association | |
| Staff | 1 —— * | Task | Association | الموظف المكلف |
| Staff | 0..1 —— * | Task | Association | assignedBy |
| AccessibleRoute | * —— * | AccessibilityAsset | Association | |
| AccessibleRoute | 0..1 —— * | AccessibleRoute | ذاتية | alternativeRoute |
| Incident | 0..1 —— * | Notification | Association | |
| Staff | 0..1 —— * | Notification | Association | recipientStaff |
| Incident | 0..1 —— * | ActivityLog | Association | |
| Staff | 0..1 —— * | ActivityLog | Association | actor |

> الـ Composition (Incident ← Recommendation/Task) في JPA: العلاقة مملوكة من طرف الابن (`@ManyToOne` في Recommendation و Task). **لا تضيفون `@OneToMany(cascade=ALL)` في Incident** عشان مطوّر 1 ما يحتاج يلمس كلاسات مطوّر 2، والاستعلام يكون عبر Repository الابن.

---

## 9. عقد التكامل بين المطوّرَين (أهم قسم)

### 9.1 الأحداث (Spring Application Events) — في `incident/events`
مطوّر 1 **ينشرها**، مطوّر 2 **يستمع لها**.

```java
public record IncidentCreatedEvent(Long incidentId) {}

public record IncidentSeverityChangedEvent(Long incidentId, Severity oldSeverity, Severity newSeverity) {}

public record IncidentResolvedEvent(Long incidentId, IncidentStatus finalStatus) {}  // RESOLVED أو FALSE_ALARM
```

- النشر: `applicationEventPublisher.publishEvent(...)` داخل الـ transaction.
- الاستماع: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` + `@Async` (فعّلوا `@EnableAsync`) عشان البيانات تكون محفوظة ولا يبطّئ الاستقبال.
- **أثناء الانتظار:** مطوّر 2 يقدر يختبر بنشر الحدث يدويًا من endpoint تجريبي.

### 9.2 الـ Services العامة اللي يستخدمها الطرف الثاني

**يوفرها مطوّر 1:**
```java
// activitylog
void log(EntityType entityType, Long entityId, Long incidentId,
         ActionType action, ActorType actorType, Long actorStaffId,
         String oldValue, String newValue, String details);

// incident
IncidentStatus changeStatus(Long incidentId, IncidentStatus newStatus, ActorType actorType, Long actorStaffId);
Incident getIncident(Long incidentId);   // قراءة
```
قواعد تغيير الحالة اللي يطلبها مطوّر 2:
- عند إنشاء أول Task → `ASSIGNED`
- عند وصول أي Task إلى `EN_ROUTE` أو `ON_SITE` → `IN_PROGRESS`
- الإغلاق `RESOLVED` يكون **يدويًا من المشغّل** عبر endpoint مطوّر 1 (وليس تلقائيًا عند انتهاء المهام).

**يوفرها مطوّر 2:**
```java
// accessibility — يستخدمها مطوّر 1 عند إنشاء الحادثة
Optional<AccessibilityAsset> findNearestAsset(Double x, Double y, Long zoneId, double radius);

// notification
void notifyStaff(Long staffId, Long incidentId, NotificationType type, String message, Severity priority);
void notifySpecialty(StaffSpecialty specialty, Long incidentId, NotificationType type, String message, Severity priority);
```

### 9.3 WebSocket (STOMP) — الإعداد عند مطوّر 1 في `common/config`
Endpoint: `/ws` — Broker: `/topic`

| Topic | المرسل | المحتوى |
|---|---|---|
| `/topic/alerts` | مطوّر 1 | تنبيه جديد (للعدّاد والقائمة الحية) |
| `/topic/incidents` | مطوّر 1 | حادثة جديدة/محدثة (للخريطة) |
| `/topic/stats` | مطوّر 1 | عدد التنبيهات مقابل عدد الحوادث |
| `/topic/recommendations` | مطوّر 2 | توصية جديدة/محدثة |
| `/topic/tasks` | مطوّر 2 | تحديث مهمة |
| `/topic/staff` | مطوّر 2 | موقع/حالة موظف |
| `/topic/routes` | مطوّر 2 | تغير مسار ذوي الإعاقة |
| `/topic/notifications` | مطوّر 2 | إشعار جديد |

كل رسالة شكلها:
```json
{ "type": "INCIDENT_UPDATED", "payload": { ... }, "timestamp": "2026-10-16T14:02:11" }
```

---

## 10. قائمة الـ APIs كاملة (مع المالك)

البادئة `/api`. الرد دائمًا DTO. الأخطاء بشكل موحد `{ "error": "...", "message": "..." }`.

### مطوّر 1
| Method | Path | الوصف |
|---|---|---|
| POST | `/alerts` | استقبال تنبيه واحد |
| POST | `/alerts/batch` | استقبال مجموعة تنبيهات |
| GET | `/alerts?status=&incidentId=` | قائمة التنبيهات |
| GET | `/zones` | المناطق (للخريطة) |
| GET | `/events?active=true` | الفعاليات |
| GET | `/incidents?status=&severity=&category=` | قائمة الحوادث |
| GET | `/incidents/{id}` | تفاصيل الحادثة + تنبيهاتها |
| POST | `/incidents/{id}/acknowledge` | المشغّل اطلع عليها |
| PATCH | `/incidents/{id}/severity` | تعديل يدوي للخطورة (HUMAN) |
| PATCH | `/incidents/{id}/status` | تغيير الحالة |
| POST | `/incidents/{id}/resolve` | إغلاق مع `resolutionNotes` و `actualSeverity` |
| POST | `/incidents/{id}/false-alarm` | إنذار كاذب |
| GET | `/incidents/{id}/activity` | سجل الحادثة |
| GET | `/activity?limit=50` | آخر النشاطات |
| GET | `/dashboard/stats` | عدد التنبيهات، الحوادث المفتوحة، حسب الخطورة |
| POST | `/simulation/start` | تشغيل سيناريو العرض |
| POST | `/simulation/reset` | تصفير البيانات للبروفة |

### مطوّر 2
| Method | Path | الوصف |
|---|---|---|
| POST | `/auth/login` | تسجيل الدخول |
| GET | `/staff?status=&specialty=&role=` | الموظفون |
| GET | `/staff/{id}` | موظف |
| PATCH | `/staff/{id}/location` | تحديث الموقع |
| PATCH | `/staff/{id}/status` | تحديث الحالة |
| GET | `/playbooks` | الإجراءات المعتمدة |
| GET | `/incidents/{id}/recommendations` | توصيات الحادثة |
| POST | `/recommendations/{id}/accept` | قبول ← إسناد تلقائي |
| POST | `/recommendations/{id}/reject` | رفض مع سبب |
| GET | `/incidents/{id}/candidates` | معاينة أقرب الموظفين المناسبين |
| GET | `/incidents/{id}/tasks` | مهام الحادثة |
| POST | `/tasks` | إسناد يدوي لموظف محدد |
| GET | `/tasks?staffId=&status=` | المهام |
| POST | `/tasks/{id}/accept` | الموظف قبل |
| POST | `/tasks/{id}/reject` | الموظف رفض مع سبب ← إعادة إسناد |
| PATCH | `/tasks/{id}/status` | EN_ROUTE / ON_SITE / DONE |
| GET | `/accessibility/assets` | الأصول |
| PATCH | `/accessibility/assets/{id}/status` | تغيير حالة أصل (يدوي/للعرض) |
| GET | `/accessibility/routes` | المسارات وحالتها |
| GET | `/accessibility/routes/{id}/alternative` | المسار البديل المحسوب |
| GET | `/notifications?staffId=&specialty=&unread=true` | الإشعارات |
| PATCH | `/notifications/{id}/read` | تعليم كمقروء |

---

## 11. عقد خدمة الذكاء الاصطناعي (مقترح، يُتفق عليه مع فريق AI)

كل نداء: timeout 3 ثواني. أي فشل أو `ai.enabled=false` ← الـ fallback بالقواعد. ونسجل في ActivityLog هل القرار من `AI` أو `SYSTEM`.

### التصنيف — يناديه مطوّر 1
```
POST {ai.base-url}/classify
```
```json
{
  "alertId": 101,
  "text": "ازدحام شديد عند البوابة 3",
  "language": "ar",
  "source": "RADIO",
  "x": 120.5, "y": 340.0,
  "zoneCode": "G3",
  "receivedAt": "2026-10-16T14:02:11"
}
```
```json
{ "severity": "HIGH", "category": "CROWD", "confidence": 0.91, "summary": "Severe crowding at Gate 3" }
```

### درجة الدمج — يناديه مطوّر 1
```
POST {ai.base-url}/merge-score
```
```json
{
  "alert": { "alertId": 101, "text": "...", "category": "CROWD", "x": 120.5, "y": 340.0, "receivedAt": "..." },
  "candidates": [
    { "incidentId": 7, "title": "...", "summary": "...", "category": "CROWD", "x": 118.0, "y": 335.0, "lastAlertAt": "..." }
  ]
}
```
```json
{ "bestIncidentId": 7, "score": 0.83 }
```

### اقتراح الإجراء (اختياري، RAG) — يناديه مطوّر 2
```
POST {ai.base-url}/recommend
```
```json
{
  "incident": { "id": 7, "title": "...", "summary": "...", "category": "CROWD", "severity": "HIGH" },
  "playbooks": [ { "id": 2, "title": "...", "steps": "..." } ]
}
```
```json
{ "playbookId": 2, "actionText": "...", "reasoning": "...", "confidence": 0.8 }
```

### رسالة الزوار عند تغير المسار (اختياري) — يناديه مطوّر 2
```
POST {ai.base-url}/route-message
```
```json
{ "brokenAsset": "مصعد الجناح B", "fromZone": "...", "toZone": "...", "alternativeName": "..." }
```
```json
{ "ar": "...", "en": "..." }
```

---

## 12. سيناريو العرض (كل شيء يخدمه)

1. فعالية مباشرة (حفل) في الساحة الرئيسية.
2. `POST /simulation/start` يرسل ~40 تنبيه خلال دقيقتين من مصادر مختلفة (لاسلكي، كاميرات، تطبيق الزوار، حساسات) بالعربي والإنجليزي، فيها تكرار مقصود.
3. الشاشة: عدّاد التنبيهات يرتفع، والحوادث على الخريطة تبقى **3**:
   - **ازدحام حرج** قرب الحفل (خطورته ارتفعت بسبب الفعالية) ← توصية Playbook ← المشغّل يقبل ← تُسند لأقرب **مشرف حشود معتمد** ← الموظف يتحرك على الخريطة.
   - **تعطل مصعد** ← حادثة `affectsAccessibility` ← إشعار لفريق ذوي الإعاقة ← المسار المهيأ يصير DISRUPTED ← Dijkstra يحسب البديل ← رسالة للزوار بالعربي والإنجليزي.
   - **حادثة بسيطة** (نظافة أو مفقودات) ← تُسند لمساعد.
4. سجل النشاط يوضح أي قرار من AI وأي قرار من إنسان.
5. الرسالة: **"من 40 تنبيه إلى 3 حوادث"**.

---

## 13. الخطة الزمنية

| المرحلة | المطوّر 1 | المطوّر 2 |
|---|---|---|
| **قبل 15 أكتوبر (سوا)** | إنشاء المشروع، `common/`، BaseEntity، الـ 20 Enum، **كل الـ 12 Entity بدون منطق**، WebSocketConfig، الاتفاق على JSON مع فريق AI والفرونت | نفس الجلسة |
| **قبل 15 أكتوبر** | Repositories + Seed المناطق والفعاليات، AiClient (مع Mock)، Fallback | Repositories + Seed الموظفين والـ Playbooks والأصول والمسارات |
| **اليوم 1** | تنبيه يدخل ← يتصنف ← حادثة ← تظهر على الخريطة لحظيًا | Recommendation من الـ Playbook + إسناد أقرب موظف معتمد |
| **اليوم 2** | الدمج + أثر الفعاليات + ActivityLog كامل + المحاكي + الإحصائيات | دورة المهمة + التصعيد + مسار ذوي الإعاقة و Dijkstra + الإشعارات |
| **اليوم 3 صباحًا** | تجميد الكود، تشغيل السيناريو كامل مرتين، فيديو احتياطي | نفس الشي |

لو أحد خلص بدري: أفضل مكان يساعد فيه هو **الدمج والـ fallback** لأن العرض كله يعتمد عليهم.

---

## 14. قرارات مفتوحة

- هل يبقى Recommendation كلاسًا أم يُدمج في Incident؟
- اختيار إضافات الذكاء الاصطناعي (أقوى مرشح: تحويل رسائل اللاسلكي الصوتية لنص بـ Whisper). يحدد هل نحتاج Device و OccupancyReading.
- هل يُسمح بالبرمجة قبل الانطلاق (السؤال في الديسكورد).
- PostgreSQL أو H2 للعرض.
- نوع تسجيل الدخول (JWT أو بسيط) — مطوّر 2 يقرر.

---

## 15. 🟦 شغلك بالتفصيل (المطوّر 1)

### 15.1 ملكيتك
- **الكلاسات:** Zone, Event, Alert, Incident, ActivityLog
- **الباكجات:** `intake`, `incident`, `activitylog` + إعداد `WebSocketConfig` في `common/config`
- **الأحداث اللي تنشرها:** IncidentCreatedEvent, IncidentSeverityChangedEvent, IncidentResolvedEvent
- **الـ Services اللي توفرها للمطوّر 2:** `ActivityLogService.log(...)`, `IncidentService.changeStatus(...)`, `IncidentService.getIncident(...)`
- **تستخدم من المطوّر 2:** `AccessibilityService.findNearestAsset(...)` فقط

### 15.2 خط سير التنبيه (Pipeline)
```
POST /api/alerts
  1. حفظ Alert بحالة NEW (receivedAt = الآن لو ما وصل)
  2. تحديد Zone: من zoneCode لو موجود، وإلا أقرب Zone لـ (x, y) عن طريق centerX/centerY
  3. بث /topic/alerts
  4. التصنيف: AiClient.classify()  ← فشل/timeout ← RuleBasedClassifier
     حفظ aiSeverity, aiCategory, aiConfidence, processedAt ، الحالة PROCESSED
     ActivityLog(ALERT, CREATED, actorType = AI أو SYSTEM)
  5. الدمج: MergeService.findMatch(alert)
     - لقى حادثة ← ربط، status = MERGED، mergeScore، تحديث الحادثة (5-أ)
     - ما لقى ← إنشاء حادثة جديدة (5-ب)
  6. بث /topic/incidents و /topic/stats
```

**(5-أ) تحديث حادثة موجودة:**
- `alertCount++`
- الخطورة = أعلى خطورة بين تنبيهاتها (بعد أثر الفعالية). لو تغيرت ← `IncidentSeverityChangedEvent` + ActivityLog(SEVERITY_CHANGED)
- x, y = متوسط مواقع التنبيهات (اختياري، يعطي شكل حلو على الخريطة)
- ActivityLog(INCIDENT, MERGED, details = "alert #101 merged, score 0.83")

**(5-ب) إنشاء حادثة جديدة:**
- `code` بصيغة `INC-0001` (عدّاد تسلسلي)
- title/summary من `summary` حق الـ AI، أو أول 80 حرف من rawText في الـ fallback
- category و severity من التنبيه، `status = OPEN`, `alertCount = 1`
- **الفعالية:** لو فيه Event نشطة (`startTime <= الآن <= endTime`) في نفس الـ Zone أو مركزها ضمن `event.severity-boost-radius` ← اربط `event` وارفع الخطورة درجة وحدة (حد أقصى CRITICAL) للفئات CROWD, MEDICAL, SECURITY, FIRE فقط. اكتب السبب في ActivityLog.
- **ذوي الإعاقة:** لو category = ACCESSIBILITY أو TECHNICAL_FAILURE ← نادِ `findNearestAsset(x, y, zoneId, accessibility.asset-match-radius)`؛ لو رجع أصل ← `affectsAccessibility = true` و `accessibilityAsset = الأصل`. غير كذا `false`.
- حفظ ← ActivityLog(INCIDENT, CREATED) ← نشر `IncidentCreatedEvent`

### 15.3 منطق الدمج (MergeService)
```
المرشحون = حوادث حالتها ليست RESOLVED/FALSE_ALARM
           ونفس category
           والمسافة بين التنبيه والحادثة <= merge.max-distance
           وآخر تنبيه فيها خلال merge.time-window-minutes

لو ما فيه مرشحين ← حادثة جديدة

لو AI متاح ← POST /merge-score ← لو score >= merge.threshold ادمج مع bestIncidentId

Fallback (قواعد):
  score = 0.5 * textSimilarity + 0.3 * proximity + 0.2 * recency
  textSimilarity = Jaccard على الكلمات (بعد تنظيف بسيط) بين rawText والعنوان/الملخص
  proximity      = 1 - (distance / max-distance)
  recency        = 1 - (minutesSinceLastAlert / time-window)
  اختر الأعلى، ادمج لو >= merge.threshold
```
> تنبيه: التنبيهات العربية والإنجليزية لنفس الحدث نصّها مختلف، فالـ Jaccard ضعيف بينها. عشان كذا الفئة + القرب المكاني والزمني لازم يكفون للدمج في الـ fallback (اضبط الأوزان على بيانات السيناريو).

### 15.4 الـ Fallback بالقواعد (RuleBasedClassifier)
قاموس كلمات مفتاحية عربي + إنجليزي لكل فئة، مع كلمات تصعيد للخطورة. أمثلة للبداية:
```
FIRE:              حريق، دخان، نار، fire, smoke, burning
MEDICAL:           إغماء، مصاب، نزيف، اسعاف، سقط، fainted, injured, ambulance, bleeding
CROWD:             ازدحام، زحمة، تدافع، crowd, crowded, stampede, overcrowding
SECURITY:          شجار، سرقة، مشبوه، fight, theft, suspicious
TECHNICAL_FAILURE: عطل، تعطل، انقطاع، broken, outage, malfunction, not working
ACCESSIBILITY:     مصعد، كرسي متحرك، منحدر، elevator, wheelchair, ramp
LOST_PERSON:       طفل مفقود، تائه، lost child, missing
CLEANLINESS:       انسكاب، نظافة، قمامة، spill, trash, dirty
غير ذلك ← OTHER

الخطورة الافتراضية لكل فئة:
FIRE=HIGH, MEDICAL=HIGH, CROWD=MEDIUM, SECURITY=MEDIUM, TECHNICAL_FAILURE=MEDIUM,
ACCESSIBILITY=MEDIUM, LOST_PERSON=MEDIUM, CLEANLINESS=LOW, OTHER=LOW

كلمات ترفع درجة: شديد، خطير، عاجل، تدافع، فاقد الوعي، severe, urgent, critical, unconscious
confidence في الـ fallback = 0.5 ثابت
```
> ملاحظة: كلمة "مصعد" تطابق ACCESSIBILITY و TECHNICAL_FAILURE، الاثنين يشغلون مسار ذوي الإعاقة فما يفرق.

### 15.5 الإغلاق
- `POST /incidents/{id}/resolve` (`resolutionNotes`, `actualSeverity`) ← `RESOLVED`, `resolvedAt` ← ActivityLog(RESOLVED, HUMAN) ← `IncidentResolvedEvent(id, RESOLVED)`
- `POST /incidents/{id}/false-alarm` ← `FALSE_ALARM` ← نفس الحدث بحالة FALSE_ALARM
- المطوّر 2 يستمع للحدث عشان يرجّع الأصل والمسار لوضعهم الطبيعي ويحرر الموظفين.

### 15.6 IncidentService.changeStatus (للمطوّر 2)
- تتحقق من صحة الانتقال: `OPEN → ASSIGNED → IN_PROGRESS → RESOLVED`، و `FALSE_ALARM` من أي حالة مفتوحة. لا ترجع للخلف (مثلًا IN_PROGRESS لا ترجع ASSIGNED لو جا طلب متأخر، تجاهله بهدوء).
- تسجل ActivityLog(STATUS_CHANGED) وتبث `/topic/incidents`.

### 15.7 المحاكي (SimulationService)
- يقرأ ملف السيناريو من `resources/scenario/demo.json` (يجهزه AI 2 من مولّد البيانات): قائمة تنبيهات مع `offsetSeconds`.
- `POST /simulation/start` يرسلها بالتوقيت (عبر `TaskScheduler`) لنفس pipeline الـ `/alerts` (نداء داخلي للـ Service، ليس HTTP).
- باراميتر اختياري `speed` (مثلًا 2 = ضعف السرعة) عشان تضبطون العرض على 5 دقائق.
- `POST /simulation/reset`: يحذف التنبيهات والحوادث والسجل **ويطلب من المطوّر 2** تصفير المهام والتوصيات والإشعارات ورجوع الموظفين والأصول والمسارات للحالة الأصلية (اتفقوا: إما حدث `SimulationResetEvent` يستمع له، أو كل واحد عنده reset endpoint).

صيغة ملف السيناريو المقترحة:
```json
[
  { "offsetSeconds": 0,  "source": "RADIO",       "text": "ازدحام عند البوابة 3", "language": "ar", "x": 120, "y": 340, "zoneCode": "G3" },
  { "offsetSeconds": 3,  "source": "CCTV",        "text": "Crowd density high at Gate 3", "language": "en", "x": 122, "y": 338 },
  { "offsetSeconds": 20, "source": "IOT_SENSOR",  "text": "Elevator B2 fault code E14", "language": "en", "x": 410, "y": 200, "zoneCode": "PB" }
]
```

### 15.8 الإحصائيات (`GET /dashboard/stats` و `/topic/stats`)
```json
{
  "totalAlerts": 40,
  "mergedAlerts": 37,
  "openIncidents": 3,
  "bySeverity": { "CRITICAL": 1, "HIGH": 1, "MEDIUM": 0, "LOW": 1 },
  "aiDecisions": 38,
  "fallbackDecisions": 2
}
```
هذي هي أرقام "من 40 إلى 3" في العرض، فلازم تكون دقيقة.

### 15.9 ActivityLogService
- تحفظ `timestamp = الآن`. `actor` يُحمّل بـ `getReferenceById` (بدون استعلام).
- `oldValue`/`newValue` نصوص قصيرة (مثلًا `"MEDIUM"` → `"HIGH"`).
- `GET /incidents/{id}/activity` مرتب زمنيًا، وكل سطر فيه أيقونة/علامة AI أو HUMAN أو SYSTEM للفرونت.

### 15.10 Seed Data حقك (`@Order(1)` — لازم يشتغل قبل seed المطوّر 2)
- ~10 مناطق متنوعة الأنواع (بوابات، أجنحة، ساحة رئيسية، محطة نقل، منطقة خدمات) بـ `polygonCoordinates` (JSON نصي لمصفوفة نقاط) و center و capacity.
- 2–3 فعاليات: **وحدة منها نشطة وقت العرض** (احسب `startTime = الآن - 30 دقيقة` و `endTime = الآن + 3 ساعات` عشان تكون نشطة دائمًا وقت البروفة).
- أكواد المناطق ثابتة ومشتركة مع المطوّر 2 وفريق AI (مثلًا `G1, G3, MP, PA, PB, TH, SV`).

### 15.11 ما تحتاجه من المطوّر 2 وكيف تشتغل قبله
- `findNearestAsset`: قبل ما يجهز، سوِّ Stub يرجع `Optional.empty()`.
- `Staff` في ActivityLog: خليه `null` لقرارات AI/SYSTEM.

### 15.12 ✅ قائمة التحقق (لا تنساها)
- [ ] كل الـ Entities الـ 12 مبنية وتشتغل عند الاثنين (اليوم الأول، سوا)
- [ ] `@EnableAsync` + `WebSocketConfig` + CORS للفرونت
- [ ] Pipeline التنبيه كامل مع بث لحظي
- [ ] AiClient بـ timeout + fallback يشتغل لما `ai.enabled=false` (**جرّبه وانت فاصل الإنترنت**)
- [ ] الدمج يحوّل تنبيهات السيناريو الـ 40 إلى 3 حوادث بالضبط
- [ ] رفع الخطورة بسبب الفعالية مع سبب واضح في السجل
- [ ] ربط الأصل وتعيين `affectsAccessibility`
- [ ] الأحداث الثلاث تنتشر بعد الـ commit
- [ ] `changeStatus` بانتقالات صحيحة
- [ ] المحاكي + reset للبروفات
- [ ] الإحصائيات دقيقة
- [ ] ActivityLog يميّز AI / HUMAN / SYSTEM
