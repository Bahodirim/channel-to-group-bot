# Channel → Faculty Channels/Groups Bot (V3, Java + Spring Boot)

Telegram kanalga post joylanganda, undagi **hashtag** orqali qaysi
fakultetga tegishli ekanligi aniqlanadi va post FAQAT o'sha fakultetga
biriktirilgan **kanal(lar)** va/yoki **guruh(lar)**ga avtomatik jo'natiladi.

V3'da bot endi manzil sifatida ham **kanallarni**, ham **guruhlarni**
qo'llab-quvvatlaydi:
- **Guruh**: bot admin qilinganda avtomatik ro'yxatga qo'shiladi, fakultet
  biriktirish o'sha guruhning o'zida `/fakultet <raqam>` orqali qilinadi
  (eski, V2'dagi tartib — o'zgarmagan).
- **Kanal**: bot admin qilinganda avtomatik ro'yxatga qo'shiladi, LEKIN
  fakultet biriktirish botning **shaxsiy chatida** (`/fakultet @kanal_username
  <raqam>`) qilinadi. Buning sababi: kanal postlarida Telegram
  yuboruvchining shaxsini ko'rsatmaydi, shuning uchun "kim admin ekanligini"
  faqat shaxsiy chatda (u yerda sizning haqiqiy user ID'ingiz ma'lum) tekshira olamiz.

## Texnologiyalar

- Java 17
- Maven 3.9.16
- Spring Boot 3.2.5
- [org.telegram:telegrambots](https://github.com/rubenlagus/TelegramBots) 6.9.7.1 (long-polling)
- Jackson (ro'yxatni `groups.json` fayliga saqlash uchun)

## Loyiha tuzilishi

```
channel-to-group-bot/
├── pom.xml
├── README.md
└── src/main/
    ├── java/uz/example/channeltogroupbot/
    │   ├── TelegramBotApplication.java      # Spring Boot kirish nuqtasi
    │   ├── bot/ChannelToGroupBot.java       # Asosiy bot logikasi
    │   ├── config/BotInitializer.java       # Botni ro'yxatdan o'tkazish
    │   ├── model/Faculty.java               # 13 ta fakultet ro'yxati
    │   ├── model/GroupInfo.java             # Kanal/guruh + biriktirilgan fakultet
    │   └── service/GroupStorageService.java # Ro'yxatni saqlash/boshqarish
    └── resources/application.properties     # Sozlamalar (token va h.k.)
```

> Eslatma: fayl/klass nomlarida hali ham "Group" so'zi bor (tarixiy sabab —
> loyiha dastlab faqat guruhlar bilan boshlangan), lekin funksional jihatdan
> ular endi kanallarni ham to'liq qo'llab-quvvatlaydi.

## Fakultetlar ro'yxati va hashtaglari

| # | Fakultet | Kanal postida yoziladigan hashtag |
|---|----------|-----------------------------------|
| 1 | Matematika | `#matematika` |
| 2 | Amaliy matematika va intelektual texnologiyalar | `#amit` |
| 3 | Fizika | `#fizika` |
| 4 | Kimyo | `#kimyo` |
| 5 | Biologiya | `#biologiya` |
| 6 | Geologiya va muhandislik geologiyasi | `#geologiya` |
| 7 | Geografiya va geoaxborot tizimlari | `#geografiya` |
| 8 | Iqtisodiyot | `#iqtisodiyot` |
| 9 | Tarix | `#tarix` |
| 10 | Ijtimoiy fanlar | `#ijtimoiy` |
| 11 | Xorijiy filologiya | `#filologiya` |
| 12 | Taekwondo va sport faoliyati | `#sport` |
| 13 | Jurnalistika va o'zbek filologiyasi | `#jurnalistika` |

Bundan tashqari, **`#barchasi`** (yoki `#hammasi`, `#barcha`) hashtagi
postni **barcha** ro'yxatdagi kanal/guruhlarga (fakultetidan qat'iy nazar)
jo'natadi — umumiy e'lonlar uchun qulay. Bitta postda bir nechta hashtag
bo'lishi ham mumkin: `#matematika #fizika`.

> Fakultet nomlarini yoki hashtaglarni `model/Faculty.java` faylida
> xohlagancha o'zgartirishingiz yoki qo'shishingiz mumkin.

## Bot buyruqlari

| Buyruq | Qayerda | Tavsif |
|--------|---------|--------|
| `/fakultet <raqam yoki nomi>` | **Guruh ichida** | Joriy guruhni ko'rsatilgan fakultetga biriktiradi (faqat guruh adminlari) |
| `/fakultet <@username yoki ID> <raqam yoki nomi>` | **Shaxsiy chat** | Ko'rsatilgan kanal (yoki guruh)ni fakultetga biriktiradi (faqat o'sha kanal/guruhning adminlari) |
| `/kanallarim` | **Shaxsiy chat** | Siz admin bo'lgan va ro'yxatdagi barcha kanal/guruhlarni, ID/username va joriy fakultetlari bilan ko'rsatadi |
| `/holat` | **Guruh ichida** | Joriy guruh qaysi fakultetga biriktirilganini ko'rsatadi |
| `/holat <@username yoki ID>` | **Shaxsiy chat** | Ko'rsatilgan kanal/guruhning holatini ko'rsatadi |
| `/fakultetlar` | Har qayerda | Barcha fakultetlar va ularning hashtaglari ro'yxatini ko'rsatadi |
| `/start` | Har qayerda | Qisqacha yordam matni |

## 1-qadam: Bot yaratish

1. Telegram'da [@BotFather](https://t.me/BotFather) bilan suhbatlashing.
2. `/newbot` buyrug'ini yuboring va ko'rsatmalarga amal qiling.
3. Sizga bot **tokeni** va **username** beriladi — ularni saqlab qo'ying.

> Guruhlar uchun privacy mode haqida qayg'urmang: bot guruhda **admin**
> bo'lgani uchun, Telegram admin-botlarga guruhdagi barcha xabarlarni
> privacy mode yoqiqligidan qat'iy nazar yetkazib beradi. Shaxsiy chatdagi
> buyruqlar esa (kanal biriktirish uchun) har doim to'g'ridan-to'g'ri keladi.

## 2-qadam: Sozlamalarni kiritish

`src/main/resources/application.properties` faylini oching va to'ldiring:

```properties
telegram.bot.token=SIZNING_TOKENINGIZ
telegram.bot.username=SIZNING_BOT_USERNAME
telegram.channel.id=@manba_kanal_username_yoki_id
```

`telegram.channel.id` — bu vakansiyalar **birinchi marta joylanadigan**
yagona "manba" kanal (masalan universitet rasmiy e'lonlar kanali):
- Ochiq (public) bo'lsa: `@kanal_username`
- Yopiq (private) bo'lsa: raqamli ID, masalan `-1001234567890`
  (buni bilish uchun kanalga biror xabar forward qiling [@userinfobot](https://t.me/userinfobot)ga)

## 3-qadam: Botni manba kanalga qo'shish

Botni **manba** kanalingizga (2-qadamda ko'rsatilgan) **admin** qilib
qo'shing (kamida "Post joylash" huquqi bilan) — shundan postlar o'qiladi.

## 4-qadam: Fakultet kanal/guruhlarini qo'shish va biriktirish

### Guruh uchun (eski tartib)

1. Botni guruhga qo'shing va **admin** qilib tayinlang — bot avtomatik
   ro'yxatga qo'shiladi va guruhga yo'riqnoma yuboradi.
2. O'sha guruhda (guruh admini sifatida):
   ```
   /fakultet 1
   ```

### Kanal uchun (yangi, V3)

1. Botni fakultet kanaliga qo'shing va **admin** qilib tayinlang
   (kamida "Post joylash" huquqi bilan). Bot avtomatik ravishda ro'yxatga
   qo'shiladi (kanalga hech qanday xabar yozmaydi — rasmiy kanalni
   tozalikda saqlash uchun). Konsolda ko'rinadi:
   ```
   >>> Yangi chat ro'yxatga qo'shildi. ID: -1009876543210, nomi: "Matematika kanali", username: matematika_kanal, turi: channel (hali fakultet biriktirilmagan)
   ```
2. Botga **SHAXSIY chatda** (`/start` bosib boshlang) yozing:
   ```
   /fakultet @matematika_kanal 1
   ```
   Kanal username'i yo'q (yopiq) bo'lsa, raqamli ID orqali:
   ```
   /fakultet -1009876543210 1
   ```
   Bot sizning shu kanalda admin ekanligingizni avtomatik tekshiradi
   (`GetChatMember` orqali) va faqat shundan keyin biriktiradi.
3. Qaysi kanal/guruhlarga adminlik huquqingiz borligini va ularning
   joriy holatini tez ko'rish uchun:
   ```
   /kanallarim
   ```

Shu tartibda barcha 13 ta fakultet kanali/guruhini qo'shib chiqing.

Agar bot kanal/guruhdan chiqarilsa yoki adminlikdan tushirilsa, u avtomatik
ravishda ro'yxatdan ham o'chiriladi (fakultet biriktiruvi bilan birga).

## 5-qadam: Manba kanalga post joylash

Manba kanalga post yozganda, oxiriga tegishli fakultet hashtagini qo'shing:

```
Dasturchi (Backend, Java/Spring) lavozimiga vakansiya ochiq!
Talablar: ...

#matematika
```

Bu post FAQAT "Matematika" fakultetiga biriktirilgan kanal/guruh(lar)ga
boradi. Bir nechta fakultetga tegishli bo'lsa: `#matematika #fizika`.
Barcha kanal/guruhlarga yuborish uchun: `#barchasi`.

## 6-qadam: Loyihani build qilish va ishga tushirish

```bash
cd channel-to-group-bot
mvn clean package
java -jar target/channel-to-group-bot.jar
```

Yoki to'g'ridan-to'g'ri: `mvn spring-boot:run`

## Qanday ishlaydi

1. **`ChannelToGroupBot`** — `TelegramLongPollingBot`ni kengaytiradi va
   Telegram serveridan doimiy ravishda yangilanishlarni (`Update`) oladi.
2. **`my_chat_member`** yangilanishi kelganda (botning chatdagi holati
   o'zgarganda) va chat turi guruh/supergruppa/**kanal** bo'lsa va yangi
   holat `administrator` bo'lsa — chat `GroupStorageService.registerGroup(...)`
   orqali (fakultetsiz) ro'yxatga qo'shiladi.
3. `/fakultet ...` buyrug'i:
   - **Guruhda** kelsa — joriy guruhning o'zini biriktiradi.
   - **Shaxsiy chatda** kelsa — birinchi argumentda ko'rsatilgan kanal/guruh
     ID'sini `GroupStorageService.resolveChatId(...)` orqali topadi.
   - Ikkala holatda ham yuboruvchi avval `GetChatMember` orqali o'sha
     kanal/guruhning admini ekanligi tekshiriladi, so'ng
     `GroupStorageService.assignFaculty(...)` chaqiriladi.
4. Manba kanaldan **`channel_post`** yoki **`edited_channel_post`**
   kelganda — post matni/caption'idagi hashtaglar (`#so'z`) ajratib olinadi,
   ular `Faculty` ro'yxati bilan solishtiriladi, va mos kanal/guruh(lar)ning
   ID ro'yxati hisoblanadi. So'ng `CopyMessage` orqali (forward emas,
   "nusxa" sifatida) shu kanal/guruhlarga jo'natiladi — bu mexanizm
   kanal va guruh uchun bir xil ishlaydi.
5. **`GroupStorageService`** barcha kanal/guruh + fakultet biriktiruvlarini
   (shu jumladan username'larini, kanal/guruh qidiruv uchun) xotirada va
   `groups.json` faylida saqlaydi.

## Nosozliklarni bartaraf etish

- **Post hech qanday kanal/guruhga bormayapti**: konsolda
  `"Ushbu post hech qanday fakultet guruhiga mos kelmadi..."`
  ogohlantirishini qidiring — bu postda hashtag yo'qligini yoki shu
  fakultetga hali hech qanday kanal/guruh biriktirilmaganini bildiradi.
- **`/fakultet @username ...` "topilmadi" deb javob beryapti**: botni
  o'sha kanalga ADMIN qilib qo'shganingizga va kanal username'ini to'g'ri
  yozganingizga ishonch hosil qiling (yoki raqamli ID bilan urinib ko'ring
  — `/kanallarim` orqali aniq ID'ni topasiz).
- **"Faqat shu kanal/guruhning adminlari..." xabari chiqyapti**: siz
  botga yuborayotgan Telegram akkauntingiz o'sha aniq kanal/guruhda admin
  emas (yoki bot hali sizni admin sifatida "ko'rmayapti" — Telegram kesh
  qilib turadi, bir necha soniya kutib qayta urinib ko'ring).
- **Rasm/video + caption bilan post ketmayapti**: kod `edited_channel_post`
  ni ham kuzatadi (Telegram ba'zan captionli media postlarni shu tarzda
  yuboradi), shuning uchun bu holat allaqachon hisobga olingan.
- Har bir kelgan `Update` konsolda `[UPDATE] id=..., channel_post=...` kabi
  ko'rinishda log qilinadi — muammoni tezroq aniqlash uchun shu qatorlarni
  kuzating.
- Agar `sendCopyToGroup`da xatolik chiqsa (masalan `CHAT_RESTRICTED`),
  demak manba kanalda **"Content Protection"** yoqilgan yoki media boshqa
  himoyalangan kanaldan olingan — bunday postlarni Telegram umuman
  nusxalashga ruxsat bermaydi.

## Muhim eslatmalar

- Bot **manba kanalda**, va **har bir fakultet kanali/guruhida** ham
  albatta admin bo'lishi kerak (kanallarda kamida "Post joylash" huquqi bilan).
- `groups.json` fayli ilova ishga tushirilgan papkada avtomatik yaratiladi;
  boshqa joyga saqlash uchun `telegram.storage.file` sozlamasini o'zgartiring.
- Eski (V2) `groups.json` formatidan V3'ga o'tayotgan bo'lsangiz, faylni
  o'chirib qayta boshlang — chunki yangi formatda `username` va `chatType`
  maydonlari ham qo'shildi.
- Yangi fakultet qo'shish yoki hashtagni o'zgartirish uchun
  `model/Faculty.java` faylini tahrirlang va qayta build qiling.
- Botni productionda doimiy ishlashi uchun uni server/VPS'da `systemd`
  xizmati yoki Docker konteyner sifatida ishga tushirish tavsiya etiladi.
- Barcha Telegram Bot API klass va metod nomlari `telegrambots` 6.9.7.1
  versiyasining rasmiy manba kodi asosida tasdiqlangan.
