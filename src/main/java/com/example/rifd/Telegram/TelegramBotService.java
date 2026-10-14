package com.example.rifd.Telegram;

import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOOut.AlertDTOOut;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.TelegramReporter;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Enum.AlertSource;
import com.example.rifd.Enum.ReporterTrust;
import com.example.rifd.Repository.IncidentRepository;
import com.example.rifd.Repository.ZoneRepository;
import com.example.rifd.Service.AlertService;
import com.example.rifd.Telegram.GeminiGatekeeper.ImageInput;
import com.example.rifd.Telegram.GeminiGatekeeper.Verdict;
import com.example.rifd.Telegram.GeminiGatekeeper.ZoneOption;
import com.example.rifd.Telegram.TelegramClient.Button;
import com.example.rifd.Telegram.TelegramClient.CallbackQuery;
import com.example.rifd.Telegram.TelegramClient.Contact;
import com.example.rifd.Telegram.TelegramClient.ImageRef;
import com.example.rifd.Telegram.TelegramClient.InlineKeyboard;
import com.example.rifd.Telegram.TelegramClient.KeyboardButton;
import com.example.rifd.Telegram.TelegramClient.Message;
import com.example.rifd.Telegram.TelegramClient.RemoveKeyboard;
import com.example.rifd.Telegram.TelegramClient.ReplyKeyboard;
import com.example.rifd.Telegram.TelegramClient.TgDownload;
import com.example.rifd.Telegram.TelegramClient.Update;
import com.example.rifd.Telegram.TelegramClient.User;
import com.example.rifd.Telegram.TelegramClient.Voice;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
public class TelegramBotService {

    // the gatekeeper must be this sure a message is NOT a report before we drop it
    private static final double NOT_REPORT_MIN_CONFIDENCE = 0.8;
    // no zone chosen in time -> the report goes out without a zone (never lost)
    private static final long ZONE_CHOICE_TIMEOUT_SECONDS = 120;
    // messages queued while the app was down: older ones are not turned into alerts
    private static final long MAX_MESSAGE_AGE_SECONDS = 15 * 60;
    private static final int MAX_TEXT_LENGTH = 2000;
    // longer voice messages are not transcribed (time + free-tier limits): voice fallback
    private static final int MAX_VOICE_SECONDS = 60;
    // Telegram getFile limit
    private static final long MAX_VOICE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    // an album arrives as separate messages: wait this long to collect them
    private static final long ALBUM_WAIT_MS = 1500;
    private static final int MAX_IMAGES = 3;
    private static final int PREVIEW_LENGTH = 500;
    private static final String ZONE_PREFIX = "z:";
    private static final String NO_ZONE = "-";

    // ---- replies ----
    private static final String WELCOME = """
            أهلًا بك في رفد 👋
            اكتب، أو أرسل رسالة صوتية أو صورة: وش صاير ووين، مثل: «شخص طايح عند البوابة الشرقية».
            بلاغك يوصل مباشرة لغرفة العمليات في الموقع.

            🚨 لو فيه خطر على الحياة اتصل على 911 أولًا.
            🔐 اختياري: /verify لتوثيق بلاغاتك برقم جوالك السعودي.

            Welcome to Rifd. Type, or send a voice message or a photo: what is happening and where.
            In a life-threatening emergency call 911 first.""";
    private static final String UNSUPPORTED = "نستقبل البلاغات المكتوبة والرسائل الصوتية والصور فقط ✍️🎙️📷\n"
            + "Text, voice and photo reports only.";
    private static final String NOT_A_REPORT = "حياك الله 🌿 لو عندك بلاغ اكتب وش صاير ووين، ونوصله لفريق الموقع فورًا.\n"
            + "If you have a report, describe what is happening and where.";

    // abuse protection
    private static final String RATE_LIMITED = "⏳ وصلتنا رسائل كثيرة منك في وقت قصير. انتظر شوي وأرسل مرة ثانية. "
            + "للطوارئ اتصل على 911.\n"
            + "Too many messages in a short time. Please wait a few minutes. In an emergency call 911.";
    // the blocked user is told plainly: never let someone believe help is coming when it isn't
    private static final String BLOCKED = "⛔ حسابك موقوف عن إرسال البلاغات عبر البوت. "
            + "للحالات الطارئة اتصل على 911 أو كلّم أقرب موظف في الموقع.\n"
            + "Your account can't send reports through this bot. In an emergency call 911 or talk to the nearest staff member.";

    // verification
    private static final String VERIFY_PROMPT = "🔐 توثيق البلاغات (اختياري)\n"
            + "شارك رقم جوالك السعودي بالزر تحت، وبلاغاتك تظهر لغرفة العمليات كبلاغات موثّقة. "
            + "رقمك ينحفظ بسرية ويُستخدم للتوثيق فقط.\n\n"
            + "Optional: share your Saudi mobile number to mark your reports as verified.";
    private static final String VERIFY_BUTTON = "📱 مشاركة رقمي للتوثيق";
    private static final String VERIFIED_OK = "✅ تم توثيق حسابك. بلاغاتك الجاية بتظهر كبلاغات موثّقة.\nYour account is verified.";
    private static final String ALREADY_VERIFIED = "حسابك موثّق مسبقًا ✅\nAlready verified.";
    private static final String VERIFY_SAUDI_ONLY = "التوثيق متاح لأرقام الجوال السعودية فقط. تقدر تبلّغ عادي بدون توثيق.\n"
            + "Verification is for Saudi mobile numbers only; you can still report.";
    private static final String VERIFY_OWN_ONLY = "شارك رقمك أنت بالزر، مو رقم شخص ثاني.\n"
            + "Please share your own number using the button.";
    private static final String VERIFY_PRIVATE_ONLY = "التوثيق يكون من المحادثة الخاصة مع البوت.\n"
            + "Verification works in a private chat with the bot.";
    private static final String VERIFIED_BADGE = "\n🔐 بلاغ موثّق";

    // voice
    private static final String LISTENING = "🎧 جاري الاستماع لرسالتك، لحظات...\nListening to your message, one moment...";
    private static final String HEARD = "🎙️ سمعناك تقول:\n";
    private static final String VOICE_FAILED = "🎙️ ما قدرنا نفهم الرسالة الصوتية بوضوح. وصلت لغرفة العمليات كما هي، "
            + "وعشان نخدمك أسرع اكتب لنا وش صاير ووين.\n"
            + "We couldn't make out your voice message. It was forwarded as is; please also type what is happening and where.";
    private static final String VOICE_TOO_LONG = "🎙️ الرسالة الصوتية أطول من دقيقة، فوصلت لغرفة العمليات كما هي بدون تفريغ. "
            + "اكتب لنا وش صاير ووين باختصار.\n"
            + "Voice message over 1 minute: forwarded as is. Please type a short description.";
    private static final String VOICE_FALLBACK_TEXT = "بلاغ صوتي من تيليجرام تعذّر تفريغه، الرجاء الرجوع للمرفق الصوتي";

    // images
    private static final String ANALYZING = "🔍 جاري تحليل الصورة، لحظات...\nLooking at your photo, one moment...";
    private static final String ANALYZING_MANY = "🔍 جاري تحليل الصور، لحظات...\nLooking at your photos, one moment...";
    private static final String SEEN = "🔍 شفنا في الصورة:\n";
    private static final String IMAGE_LABEL = "وصف الصور: ";
    private static final String CAPTION_LABEL = "تعليق المرسل: ";
    private static final String IMAGE_FAILED = "🔍 ما قدرنا نحلل الصورة. وصلت لغرفة العمليات كما هي، "
            + "وعشان نخدمك أسرع اكتب لنا وش صاير ووين.\n"
            + "We couldn't analyze your photo. It was forwarded as is; please also type what is happening and where.";
    private static final String IMAGE_FAILED_WITH_CAPTION = "🔍 ما قدرنا نحلل الصورة، فأرسلنا تعليقك مع الصورة لغرفة العمليات كما هي.\n"
            + "We couldn't analyze your photo; it was forwarded with your caption.";
    private static final String IMAGE_FALLBACK_TEXT = "بلاغ بالصور من تيليجرام تعذّر تحليله، الرجاء الرجوع للمرفقات";

    // zone + pipeline
    private static final String ASK_ZONE = "وين المكان بالضبط؟ 📍\nWhere is it?";
    private static final String UNKNOWN_ZONE_BUTTON = "ما أعرف / Not sure";
    private static final String RECEIVED = "✅ وصل بلاغك لغرفة العمليات\nReport received";
    private static final String TRACKING = "رقم المتابعة / Ref: ";
    private static final String FAILED = "⚠️ صار خلل وما قدرنا نسجل البلاغ. جرّب بعد شوي أو بلّغ أقرب موظف.\n"
            + "Something went wrong. Please try again or tell the nearest staff member.";
    private static final String STALE = "وصلتنا رسالتك متأخرة ⏳ لو البلاغ للحين قائم أرسله مرة ثانية.\n"
            + "Your message reached us late. If it is still happening, please send it again.";
    private static final String ALREADY_HANDLED = "تم استلام هذا البلاغ مسبقًا";
    private static final String NOT_YOUR_REPORT = "اختيار المكان لصاحب البلاغ";
    private static final String ZONE_TIMEOUT = "⏱ ما وصلنا اختيار المكان، أرسلنا البلاغ بدون تحديده";
    private static final String UNKNOWN_ZONE_LABEL = "📍 المكان: غير محدد";
    private static final String ZONE_LABEL = "📍 المكان: ";

    // a report on its way to the pipeline (may wait for the zone buttons), key = chatId:messageId
    private record PendingReport(long chatId, long messageId, Long userId, TelegramReporter reporter, String text,
                                 LocalDateTime receivedAt, String attachmentUrl, Long promptMessageId) {
        PendingReport withPrompt(Long id) {
            return new PendingReport(chatId, messageId, userId, reporter, text, receivedAt, attachmentUrl, id);
        }
    }

    private final boolean enabled;
    private final int pollTimeoutSeconds;
    private final TelegramClient client;
    private final GeminiGatekeeper gatekeeper;
    private final AlertService alertService;
    private final ZoneRepository zoneRepository;
    private final IncidentRepository incidentRepository;
    private final com.example.rifd.Telegram.TelegramReporterService reporterService;
    private final TelegramRateLimiter rateLimiter;

    private final Map<String, PendingReport> pending = new ConcurrentHashMap<>();
    // album messages being collected, key = chatId:mediaGroupId
    private final Map<String, List<Message>> albums = new ConcurrentHashMap<>();
    // a slow AI call for one message must not block polling or other chats
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean running;
    private Thread pollThread;

    public TelegramBotService(@Value("${telegram.enabled:false}") boolean enabled,
                              @Value("${telegram.poll-timeout-seconds:30}") int pollTimeoutSeconds,
                              TelegramClient client, GeminiGatekeeper gatekeeper, AlertService alertService,
                              ZoneRepository zoneRepository, IncidentRepository incidentRepository,
                              com.example.rifd.Telegram.TelegramReporterService reporterService, TelegramRateLimiter rateLimiter) {
        this.enabled = enabled;
        this.pollTimeoutSeconds = pollTimeoutSeconds;
        this.client = client;
        this.gatekeeper = gatekeeper;
        this.alertService = alertService;
        this.zoneRepository = zoneRepository;
        this.incidentRepository = incidentRepository;
        this.reporterService = reporterService;
        this.rateLimiter = rateLimiter;
    }

    // ---------------- lifecycle ----------------

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Telegram bot disabled (telegram.enabled=false)");
            return;
        }
        if (!client.isConfigured()) {
            log.warn("telegram.enabled=true but TELEGRAM_BOT_TOKEN is empty: bot not started");
            return;
        }
        running = true;
        pollThread = new Thread(this::pollLoop, "telegram-poll");
        pollThread.setDaemon(true);
        pollThread.start();
        log.info("Telegram bot started (gatekeeper {})",
                gatekeeper.isEnabled() ? "ON" : "OFF -> raw text, no voice/photo analysis");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        timer.shutdownNow();
        workers.shutdown();
        try {
            workers.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollLoop() {
        long offset = 0;
        int backoffSeconds = 1;

        while (running) {
            try {
                List<Update> updates = client.getUpdates(offset, pollTimeoutSeconds);
                backoffSeconds = 1;
                for (Update update : updates) {
                    offset = Math.max(offset, update.updateId() + 1);
                    workers.submit(() -> handle(update));
                }
            } catch (Exception e) {
                if (!running) break;
                log.warn("Telegram polling failed: {} (retry in {}s)", client.safeMessage(e), backoffSeconds);
                try {
                    Thread.sleep(backoffSeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffSeconds = Math.min(backoffSeconds * 2, 30);
            }
        }
        log.info("Telegram polling stopped");
    }

    private void handle(Update update) {
        try {
            if (update.message() != null) onMessage(update.message());
            else if (update.callbackQuery() != null) onCallback(update.callbackQuery());
        } catch (Exception e) {
            log.error("Telegram update #{} failed", update.updateId(), e);
        }
    }

    // ---------------- messages ----------------

    private void onMessage(Message message) {
        if (message.chat() == null) return;
        // no sender (anonymous group admin / channel): nobody to track or answer
        if (message.from() == null || message.from().isBot()) return;

        long chatId = message.chat().id();

        // the phone shared with the verify button
        if (message.contact() != null) {
            onContact(message);
            return;
        }

        // stage 2: voice notes and audio files
        Voice voice = message.voice() != null ? message.voice() : message.audio();
        if (voice != null) {
            onVoice(message, voice);
            return;
        }

        // stage 3: photos (single, album, or image sent as a file). The caption travels with them
        if (message.image() != null) {
            if (hasText(message.mediaGroupId())) collectAlbum(message);
            else onImages(List.of(message));
            return;
        }

        String text = message.text();

        if (hasText(text) && text.trim().startsWith("/")) {
            onCommand(message, text.trim());
            return;
        }

        if (!hasText(text)) {
            if (message.hasMedia()) client.sendMessage(chatId, UNSUPPORTED, message.messageId(), null);
            return;
        }

        if (isStale(message)) {
            client.sendMessage(chatId, STALE, message.messageId(), null);
            return;
        }

        TelegramReporter reporter = admit(message);
        if (reporter == null) return;

        processReport(message, truncate(text.trim()), null, reporter);
    }

    // ---------------- commands + verification ----------------

    private void onCommand(Message message, String command) {
        long chatId = message.chat().id();
        boolean verify = command.startsWith("/verify");

        // the share-contact button only works in a private chat
        if (!message.chat().isPrivate()) {
            client.sendMessage(chatId, verify ? VERIFY_PRIVATE_ONLY : WELCOME, message.messageId(), null);
            return;
        }

        TelegramReporter reporter = register(message.from());
        boolean verified = reporter.getPhoneVerifiedAt() != null;

        if (verify) {
            client.sendMessage(chatId, verified ? ALREADY_VERIFIED : VERIFY_PROMPT, null,
                    verified ? null : verifyKeyboard());
            return;
        }

        // /start, /help and any other command: welcome (+ the optional verify button)
        client.sendMessage(chatId, WELCOME, null, verified ? null : verifyKeyboard());
    }

    private void onContact(Message message) {
        long chatId = message.chat().id();
        User from = message.from();
        Contact contact = message.contact();

        // must be the sender's own number (a forwarded contact card is someone else's)
        if (contact.userId() == null || contact.userId() != from.id()) {
            client.sendMessage(chatId, VERIFY_OWN_ONLY, message.messageId(), new RemoveKeyboard(true));
            return;
        }

        register(from);
        Optional<String> phone = reporterService.verifyPhone(from.id(), contact.phoneNumber());
        client.sendMessage(chatId, phone.isPresent() ? VERIFIED_OK : VERIFY_SAUDI_ONLY,
                message.messageId(), new RemoveKeyboard(true));
    }

    private static ReplyKeyboard verifyKeyboard() {
        return new ReplyKeyboard(List.of(List.of(new KeyboardButton(VERIFY_BUTTON, true))), true, true);
    }

    // ---------------- abuse protection ----------------

    // reporter row + rate limit + block, before any AI call. null = stop here (the user was already told)
    private TelegramReporter admit(Message message) {
        long chatId = message.chat().id();
        User from = message.from();
        TelegramReporter reporter = register(from);

        switch (rateLimiter.hit(from.id())) {
            case LIMITED_NOTIFY -> {
                log.info("Telegram reporter #{} rate limited", reporter.getId());
                client.sendMessage(chatId, RATE_LIMITED, message.messageId(), null);
                return null;
            }
            case LIMITED_SILENT -> {
                return null;
            }
            default -> {
            }
        }

        if (!reporterService.mayReport(reporter.getId())) {
            log.info("Telegram reporter #{} is blocked: message ignored", reporter.getId());
            client.sendMessage(chatId, BLOCKED, message.messageId(), null);
            return null;
        }
        return reporter;
    }

    private TelegramReporter register(User from) {
        return reporterService.register(from.id(), from.firstName(), from.username());
    }

    // ---------------- voice ----------------

    private void onVoice(Message message, Voice voice) {
        long chatId = message.chat().id();

        if (isStale(message)) {
            client.sendMessage(chatId, STALE, message.messageId(), null);
            return;
        }

        TelegramReporter reporter = admit(message);
        if (reporter == null) return;

        // decision 72: only the file_id, never the download URL (it contains the token)
        String attachmentUrl = hasText(voice.fileId()) ? "tg:" + voice.fileId() : null;

        if (voice.duration() != null && voice.duration() > MAX_VOICE_SECONDS) {
            log.info("Telegram tg:{}:{} voice too long ({}s): fallback", chatId, message.messageId(), voice.duration());
            mediaFallback(message, VOICE_FALLBACK_TEXT, attachmentUrl, VOICE_TOO_LONG, null, reporter);
            return;
        }

        // instant feedback: transcription takes a few seconds. This message is edited in place afterwards
        Long placeholderId = client.sendMessage(chatId, LISTENING, message.messageId(), null);

        // call 1: download + transcribe
        TgDownload audio = hasText(voice.fileId()) ? client.downloadFile(voice.fileId(), MAX_VOICE_BYTES) : null;
        String mimeType = hasText(voice.mimeType()) ? voice.mimeType() : "audio/ogg";
        Optional<String> transcript = audio == null ? Optional.empty() : gatekeeper.transcribe(audio.bytes(), mimeType);

        if (transcript.isEmpty()) {
            log.info("Telegram tg:{}:{} voice not transcribed: fallback", chatId, message.messageId());
            mediaFallback(message, VOICE_FALLBACK_TEXT, attachmentUrl, VOICE_FAILED, placeholderId, reporter);
            return;
        }

        String text = truncate(transcript.get());
        log.info("Telegram tg:{}:{} voice transcript: '{}'", chatId, message.messageId(), text);
        showOrSend(chatId, placeholderId, HEARD + "«" + preview(text) + "»", message.messageId());

        // call 2: the same gatekeeper path as typed text
        processReport(message, text, attachmentUrl, reporter);
    }

    // ---------------- images ----------------

    private void collectAlbum(Message message) {
        String key = message.chat().id() + ":" + message.mediaGroupId();
        boolean[] first = {false};
        albums.compute(key, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
                first[0] = true;
            }
            list.add(message);
            return list;
        });
        // the first photo of the album starts the wait; the others just join
        if (first[0]) {
            timer.schedule(() -> workers.submit(() -> flushAlbum(key)), ALBUM_WAIT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void flushAlbum(String key) {
        List<Message> messages = albums.remove(key);
        if (messages == null || messages.isEmpty()) return;
        try {
            onImages(messages.stream().sorted(Comparator.comparingLong(Message::messageId)).toList());
        } catch (Exception e) {
            log.error("Telegram album {} failed", key, e);
        }
    }

    // one report for one photo or a whole album (first MAX_IMAGES photos). An album counts once for the rate limit
    private void onImages(List<Message> messages) {
        Message first = messages.get(0);
        long chatId = first.chat().id();

        if (isStale(first)) {
            client.sendMessage(chatId, STALE, first.messageId(), null);
            return;
        }

        TelegramReporter reporter = admit(first);
        if (reporter == null) return;

        if (messages.size() > MAX_IMAGES) {
            log.info("Telegram tg:{}:{} album of {} photos: only the first {} are used",
                    chatId, first.messageId(), messages.size(), MAX_IMAGES);
        }
        List<ImageRef> refs = messages.stream()
                .limit(MAX_IMAGES)
                .map(Message::image)
                .filter(r -> r != null && hasText(r.fileId()))
                .toList();

        // in an album the caption is usually on one photo only
        String caption = messages.stream()
                .map(Message::caption)
                .filter(c -> hasText(c))
                .map(String::trim)
                .collect(Collectors.joining("\n"));

        // decision 72: file_ids only
        String attachmentUrl = refs.isEmpty() ? null
                : "tg:" + refs.stream().map(ImageRef::fileId).collect(Collectors.joining(","));

        Long placeholderId = client.sendMessage(chatId, refs.size() > 1 ? ANALYZING_MANY : ANALYZING,
                first.messageId(), null);

        // call 1: download + describe (a photo that fails to download is skipped, the rest still count)
        List<ImageInput> images = new ArrayList<>();
        for (ImageRef ref : refs) {
            TgDownload file = client.downloadFile(ref.fileId(), MAX_IMAGE_BYTES);
            if (file != null) {
                images.add(new ImageInput(file.bytes(), hasText(ref.mimeType()) ? ref.mimeType() : "image/jpeg"));
            }
        }
        Optional<String> description = images.isEmpty() ? Optional.empty() : gatekeeper.describeImages(images);

        if (description.isEmpty()) {
            log.info("Telegram tg:{}:{} photo not analyzed: fallback", chatId, first.messageId());
            String text = hasText(caption) ? IMAGE_FALLBACK_TEXT + "\n" + CAPTION_LABEL + caption : IMAGE_FALLBACK_TEXT;
            mediaFallback(first, text, attachmentUrl,
                    hasText(caption) ? IMAGE_FAILED_WITH_CAPTION : IMAGE_FAILED, placeholderId, reporter);
            return;
        }

        String seen = description.get();
        log.info("Telegram tg:{}:{} photo description: '{}'", chatId, first.messageId(), seen);
        showOrSend(chatId, placeholderId, SEEN + "«" + preview(seen) + "»", first.messageId());

        // call 2: the gatekeeper sees the description and the caption together
        String combined = IMAGE_LABEL + seen + (hasText(caption) ? "\n" + CAPTION_LABEL + caption : "");
        processReport(first, truncate(combined), attachmentUrl, reporter);
    }

    // ---------------- shared ----------------

    // could not transcribe / analyze: the alert still goes in with the attachment, and the user is told
    private void mediaFallback(Message message, String alertText, String attachmentUrl, String reply,
                               Long placeholderId, TelegramReporter reporter) {
        showOrSend(message.chat().id(), placeholderId, reply, message.messageId());
        submit(newReport(message, truncate(alertText), attachmentUrl, reporter), null);
    }

    // edits the "listening / analyzing..." message in place, or sends a new one if there is none
    private void showOrSend(long chatId, Long placeholderId, String text, long replyToMessageId) {
        if (placeholderId != null) client.editMessageText(chatId, placeholderId, text);
        else client.sendMessage(chatId, text, replyToMessageId, null);
    }

    // shared by typed text, voice transcripts and photo descriptions: gatekeeper -> zone -> pipeline
    private void processReport(Message message, String original, String attachmentUrl, TelegramReporter reporter) {
        long chatId = message.chat().id();
        client.sendChatAction(chatId, "typing");

        List<ZoneOption> zones = loadZones();
        Optional<Verdict> verdict = gatekeeper.check(original, zones);

        if (verdict.isPresent() && !verdict.get().isReport()
                && verdict.get().confidence() >= NOT_REPORT_MIN_CONFIDENCE) {
            // in groups people chat a lot: stay quiet there
            if (message.chat().isPrivate()) client.sendMessage(chatId, NOT_A_REPORT, message.messageId(), null);
            return;
        }

        String reportText = verdict.map(Verdict::cleanText).filter(t -> hasText(t)).orElse(original);
        String zoneCode = verdict.map(Verdict::zoneCode).orElse(null);
        if (!reportText.equals(original)) {
            log.info("Telegram tg:{}:{} cleaned: '{}' -> '{}'", chatId, message.messageId(), original, reportText);
        }

        PendingReport report = newReport(message, reportText, attachmentUrl, reporter);

        if (zoneCode != null || zones.isEmpty()) submit(report, zoneCode);
        else askForZone(report, zones);
    }

    // ---------------- zone buttons ----------------

    private void askForZone(PendingReport report, List<ZoneOption> zones) {
        String key = key(report.chatId(), report.messageId());
        pending.put(key, report);

        List<List<Button>> rows = new ArrayList<>();
        for (int i = 0; i < zones.size(); i += 2) {
            rows.add(zones.subList(i, Math.min(i + 2, zones.size())).stream()
                    .map(z -> new Button(z.name(), ZONE_PREFIX + report.messageId() + ":" + z.code()))
                    .toList());
        }
        rows.add(List.of(new Button(UNKNOWN_ZONE_BUTTON, ZONE_PREFIX + report.messageId() + ":" + NO_ZONE)));

        Long promptId = client.sendMessage(report.chatId(), ASK_ZONE, report.messageId(), new InlineKeyboard(rows));
        if (promptId == null) {
            // could not show the buttons: send it now without a zone rather than lose it
            PendingReport waiting = pending.remove(key);
            if (waiting != null) submit(waiting, null);
            return;
        }

        pending.computeIfPresent(key, (k, v) -> v.withPrompt(promptId));
        timer.schedule(() -> workers.submit(() -> expire(key)), ZONE_CHOICE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void onCallback(CallbackQuery query) {
        String data = query.data();
        if (data == null || !data.startsWith(ZONE_PREFIX) || query.message() == null || query.message().chat() == null) {
            client.answerCallbackQuery(query.id(), null);
            return;
        }

        String[] parts = data.substring(ZONE_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            client.answerCallbackQuery(query.id(), null);
            return;
        }

        String key = query.message().chat().id() + ":" + parts[0];
        PendingReport report = pending.remove(key);   // remove = only one click (or the timeout) wins
        if (report == null) {
            client.answerCallbackQuery(query.id(), ALREADY_HANDLED);
            return;
        }

        // in groups only the sender picks the zone
        if (report.userId() != null && query.from() != null && report.userId() != query.from().id()) {
            pending.putIfAbsent(key, report);
            client.answerCallbackQuery(query.id(), NOT_YOUR_REPORT);
            return;
        }

        client.answerCallbackQuery(query.id(), null);
        submit(report, NO_ZONE.equals(parts[1]) ? null : parts[1]);
    }

    private void expire(String key) {
        PendingReport report = pending.remove(key);
        if (report == null) return;   // already chosen

        if (report.promptMessageId() != null) {
            client.editMessageText(report.chatId(), report.promptMessageId(), ZONE_TIMEOUT);
        }
        submit(report.withPrompt(null), null);
    }

    // ---------------- pipeline ----------------

    private void submit(PendingReport report, String zoneCode) {
        client.sendChatAction(report.chatId(), "typing");

        if (report.promptMessageId() != null) {
            client.editMessageText(report.chatId(), report.promptMessageId(), zoneLabel(zoneCode));
        }

        AlertDTOIn dto = new AlertDTOIn(
                AlertSource.TELEGRAM,
                "tg:" + report.chatId() + ":" + report.messageId(),
                report.text(),
                null,                    // language: detected by AlertService
                report.attachmentUrl(),  // tg:<file_id>[,...] for voice/photos, null for text
                null, null,              // x/y: from the zone center
                zoneCode,
                report.receivedAt());

        try {
            // trust as it is right now, saved on the alert for the operator
            ReporterTrust trust = report.reporter() == null ? null
                    : reporterService.standing(report.reporter().getId()).trust();

            AlertDTOOut alert = alertService.ingest(dto, report.reporter(), trust);
            String code = incidentCode(alert.incidentId());

            String reply = RECEIVED
                    + (trust == ReporterTrust.VERIFIED ? VERIFIED_BADGE : "")
                    + (code == null ? "" : "\n" + TRACKING + code);
            client.sendMessage(report.chatId(), reply, report.messageId(), null);
        } catch (Exception e) {
            log.error("Telegram report tg:{}:{} could not be ingested", report.chatId(), report.messageId(), e);
            client.sendMessage(report.chatId(), FAILED, report.messageId(), null);
        }
    }

    // ---------------- helpers ----------------

    private PendingReport newReport(Message message, String text, String attachmentUrl, TelegramReporter reporter) {
        return new PendingReport(message.chat().id(), message.messageId(),
                message.from() == null ? null : message.from().id(), reporter,
                text, toLocalDateTime(message.date()), attachmentUrl, null);
    }

    private List<ZoneOption> loadZones() {
        return zoneRepository.findAllByOrderByCodeAsc().stream()
                .map(z -> new ZoneOption(z.getCode(), z.getName()))
                .toList();
    }

    private String zoneLabel(String zoneCode) {
        if (zoneCode == null) return UNKNOWN_ZONE_LABEL;
        return ZONE_LABEL + zoneRepository.findByCode(zoneCode).map(Zone::getName).orElse(zoneCode);
    }

    private String incidentCode(Long incidentId) {
        if (incidentId == null) return null;
        try {
            return incidentRepository.findById(incidentId).map(Incident::getCode).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isStale(Message message) {
        return Instant.now().getEpochSecond() - message.date() > MAX_MESSAGE_AGE_SECONDS;
    }

    private static String key(long chatId, long messageId) {
        return chatId + ":" + messageId;
    }

    private static LocalDateTime toLocalDateTime(long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    private static String preview(String text) {
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static String truncate(String text) {
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }
}