# CHECKLIST --- Proje Durumu

> Kutuları geliştirme sürecinde güncelleyebilirsin. Mevcut başlangıç
> projesinin durumunu ve sıradaki işleri ayırır.

-   [x] Java 21 + Spring Boot başlangıç projesi oluşturuldu
-   [x] PostgreSQL için Docker Compose yapılandırması eklendi
-   [x] Flyway başlangıç veritabanı şeması eklendi
-   [x] Çalışan ve randevu temel entity/repository katmanları eklendi
-   [x] Yönetim API'sinde çalışan listeleme/oluşturma ve randevu
    onaylama/reddetme uçları eklendi
-   [x] Slot hesaplayıcı başlangıcı ve birim testleri eklendi --- kapsam
    sınırlı
-   [ ] Maven derleme ve tüm testleri çalıştırıp doğrulama
-   [ ] Çalışma saatleri, birden fazla mola, bloklu zamanlar ve mevcut
    randevularla tam müsaitlik hesabı
-   [ ] 7 günlük rezervasyon ufku ve geçmiş saatleri eleme
-   [ ] 40 dakikalık slot kuralını API/service katmanında doğrulama
-   [ ] Çalışan bazlı kimlik doğrulama/yetkilendirme ve yönetim API'si
    güvenliği
-   [ ] Telegram bot akışı, kalıcı konuşma durumu ve bildirimler
-   [ ] Yarışan rezervasyonlar için entegrasyon/concurrency testleri
-   [ ] Loglama, hata yanıtları, yedekleme, gizlilik ve dağıtım ayarları

# İŞ AKIŞI

1.  Müşteri Telegram üzerinden botu açar ve randevu almak istediğini
    belirtir. **Not:** Bot entegrasyonu henüz eklenmedi.
2.  Sistem aktif çalışanları ve seçilen çalışanın çalışma saatlerini
    değerlendirir.
3.  Uygun slotlar hesaplanır: iş günü başlangıcından itibaren 40
    dakikalık sabit ritim korunur; molayla çakışan slotlar gösterilmez.
    Mola sonrasında slot ritmi yeniden başlatılmaz.
4.  Kullanıcı çalışanı, tarihi ve uygun saati seçer; ad/telefon gibi
    müşteri bilgileri alınır.
5.  Randevu `PENDING` durumunda oluşturulur. Mevcut başlangıç sürümünde
    oluşturma uç noktası yönetim API'sidir; halka açık müşteri akışı
    değildir.
6.  Aynı çalışan için `PENDING` veya `CONFIRMED` durumundaki başka bir
    randevuyla çakışma engellenmelidir. Veritabanındaki exclusion
    constraint bu çakışmayı engellemeyi hedefler.
7.  Çalışan veya yetkili yönetici randevuyu inceler. Onay verilirse
    `CONFIRMED`, reddedilirse `REJECTED` durumuna geçer.
8.  Bot entegrasyonu tamamlandığında müşteriye ve çalışana sonuç
    bildirimi gönderilir. Bu bildirim adımı mevcut kodda henüz
    uygulanmadı.

# Teknik Dokümantasyon

**Proje:** Telegram öncelikli, çok çalışanlı randevu sistemi ---
başlangıç sürümü

**Dokümanın amacı:** Kaynak kodun mimarisini, veri modelini, API
uçlarını, yapılandırmasını ve üretime geçmeden önce tamamlanması gereken
işleri tek yerde toplamak.

**Durum notu:** Bu doküman başlangıç projesine dayanır. Proje
derleme/test çalıştırmasıyla doğrulanmış veya üretime hazır kabul
edilmemelidir.

## 1. Mimari ve Teknoloji Yığını

-   **Dil ve çalışma zamanı:** Java 21
-   **Uygulama çatısı:** Spring Boot 3.5.6 (Maven yapılandırmasındaki
    sürüm)
-   **HTTP API:** Spring Web
-   **Giriş doğrulama:** Jakarta Bean Validation
-   **Veri erişimi:** Spring Data JPA / Hibernate
-   **Veritabanı:** PostgreSQL 17 (Docker Compose servisinde)
-   **Şema yönetimi:** Flyway migration dosyaları
-   **Sağlık kontrolü:** Spring Boot Actuator, `/actuator/health`
-   **Test:** Spring Boot test altyapısı ve JUnit tabanlı
    `SlotCalculatorTest`
-   **Dağıtım başlangıcı:** `Dockerfile` ve `compose.yaml`
-   **Mimari yaklaşım:** Tek bir modüler Spring Boot uygulaması;
    mikroservislere bölünmüş değil.

## 2. Paketler ve Sorumluluklar

  ------------------------------------------------------------------------------
  Paket                       Sınıflar                   Sorumluluk
  --------------------------- -------------------------- -----------------------
  `com.example.appointment`   `AppointmentApplication`   Spring Boot
                                                         uygulamasının giriş
                                                         noktası.

  `config`                    `BookingProperties`        Slot süresi,
                                                         rezervasyon ufku ve
                                                         benzeri uygulama
                                                         ayarlarını bağlamak
                                                         için yapılandırma
                                                         sınıfı.

  `employee`                  `Employee`,                Çalışan bilgileri,
                              `EmployeeRepository`,      veritabanı erişimi ve
                              `EmployeeService`          çalışan yönetimi.

  `customer`                  `Customer`,                Müşteri temel bilgileri
                              `CustomerRepository`       ve müşteri veritabanı
                                                         erişimi.

  `appointment`               `Appointment`,             Randevu durumu, kayıt,
                              `AppointmentStatus`,       listeleme, onaylama ve
                              `AppointmentRepository`,   reddetme işlevleri.
                              `AppointmentService`       

  `availability`              `SlotCalculator`           Slot üretimi için
                                                         başlangıç
                                                         hesaplayıcısı; tam
                                                         müsaitlik kurallarının
                                                         tümü henüz tamamlanmış
                                                         değil.

  `api`                       `AdminController`          Çalışan ve randevu
                                                         yönetimi için REST API
                                                         uçları.
  ------------------------------------------------------------------------------

## 3. İş Kuralları ve Ürün Kararları

-   Birden fazla çalışan desteklenecek; çalışma saatleri, molalar ve
    bloklu dönemler çalışan bazında tanımlanır.
-   Slot süresi **40 dakikadır**. Örnek: `10:00`, `10:40`, `11:20`. Mola
    ile çakışan slot atlanır; mola sonrasında slot dizilimi sıfırlanmaz.
-   Gün sonu `24:00` olarak temsil edilebilir; dakika tabanlı alanlarda
    `1440` değeri kullanılır.
-   Rezervasyon ufku 7 takvim günüdür; önerilen kapsam bugün ve sonraki
    6 gündür. Geçmiş saatler sunulmamalıdır.
-   Yeni randevu `PENDING` durumunda başlar. Onayla `CONFIRMED`, reddet
    `REJECTED`; iptal için `CANCELLED` durumu tanımlıdır.
-   Çakışan `PENDING` randevular da slotu geçici olarak rezerve eder.
    PostgreSQL constraint aynı çalışan için `PENDING`/`CONFIRMED`
    çakışmasını engellemeyi amaçlar.
-   Telegram ilk kanal olarak düşünülür; müşteri-kanal eşlemesi
    `TELEGRAM` ve `WHATSAPP` değerlerine uygun tasarlanmıştır.

## 4. Veritabanı Şeması

  -----------------------------------------------------------------------
  Tablo                               Amaç
  ----------------------------------- -----------------------------------
  `employees`                         Çalışan adı, aktiflik durumu ve
                                      saat dilimi.

  `customers`                         Müşteri adı ve telefon numarası.

  `customer_channel_accounts`         Müşteriyi Telegram/WhatsApp gibi
                                      dış kanal kimliğiyle eşler; kanal +
                                      dış kullanıcı kimliği benzersizdir.

  `weekly_working_hours`              Çalışan bazında haftanın gününe
                                      göre başlangıç/bitiş dakikaları ve
                                      aktiflik.

  `breaks`                            Çalışan bazında haftalık mola
                                      aralıkları.

  `blocked_periods`                   Tarih/saat bazlı kapalı dönemler ve
                                      isteğe bağlı neden.

  `appointments`                      Çalışan, müşteri, başlangıç/bitiş,
                                      durum ve onay zamanı.

  `telegram_sessions`                 Telegram kullanıcı kimliği, konuşma
                                      durumu ve JSONB bağlamı; bot koduna
                                      henüz bağlanmamış.

  `booking_settings`                  Slot süresi, rezervasyon ufku ve
                                      saat dilimi; varsayılanlar 40
                                      dakika, 7 gün ve `Europe/Istanbul`.
  -----------------------------------------------------------------------

### Önemli veritabanı ayrıntıları

-   Zaman damgaları `TIMESTAMPTZ` olarak saklanır; uygulama iş kuralı
    saat dilimi `Europe/Istanbul` olarak yapılandırılmıştır.
-   `appointments_no_overlap` exclusion constraint için `btree_gist`
    PostgreSQL uzantısı etkinleştirilir. Yönetilen veritabanlarında bu
    uzantıyı açmak için ek izin gerekebilir.
-   Zaman aralıkları `[)` biçimindedir: başlangıç dahil, bitiş hariç.
    Böylece `10:00–10:40` ile `10:40–11:20` bitişik slotları çakışmaz.
-   Şema çalışma saatleri ve molaları tutacak tabloları içerir; mevcut
    API henüz bu ayarları yönetmez ve `SlotCalculator` tüm bu tabloları
    okuyup değerlendirmez.

## 5. REST API

  ---------------------------------------------------------------------------------------------
  Metot                   Uç                                            Açıklama
  ----------------------- --------------------------------------------- -----------------------
  `POST`                  `/api/v1/admin/employees`                     Çalışan oluşturur.

  `GET`                   `/api/v1/admin/employees?activeOnly=true`     Aktif çalışanları
                                                                        listeler; `activeOnly`
                                                                        parametresi ile
                                                                        pasifler de alınabilir.

  `POST`                  `/api/v1/admin/employees/{id}/deactivate`     Çalışanı pasif yapar
                                                                        (`active=false`).

  `POST`                  `/api/v1/admin/appointments`                  Başlangıç sürümünde
                                                                        yönetim tarafından
                                                                        `PENDING` randevu
                                                                        oluşturur.

  `GET`                   `/api/v1/admin/appointments?status=PENDING`   Randevuları veya
                                                                        belirtilen
                                                                        durumdakileri listeler.

  `POST`                  `/api/v1/admin/appointments/{id}/approve`     Randevuyu onaylar.

  `POST`                  `/api/v1/admin/appointments/{id}/reject`      Randevuyu reddeder.

  `POST`                  `/api/v1/admin/appointments/{id}/cancel`      `PENDING` veya
                                                                        `CONFIRMED`
                                                                        randevuyu iptal eder.
  ---------------------------------------------------------------------------------------------

### Örnek çalışan isteği

``` http
POST /api/v1/admin/employees
Content-Type: application/json
```

``` json
{
  "name": "Ahmet Yilmaz"
}
```

### Örnek randevu isteği

``` http
POST /api/v1/admin/appointments
Content-Type: application/json
```

``` json
{
  "employeeId": 1,
  "customerName": "Example Customer",
  "customerPhone": "+905xxxxxxxxx",
  "startAt": "2026-10-12T10:00:00+03:00",
  "endAt": "2026-10-12T10:40:00+03:00"
}
```

## 6. Yapılandırma ve Yerel Çalıştırma

**Gereksinimler:** JDK 21, Maven 3.9+, Docker Desktop veya Docker
Engine.

1.  `.env.example` dosyasını temel alarak yerel ortam değişkenlerini
    ayarla. Gerçek parola ve token'ları kaynak koduna yazma.

2.  Docker Compose ile tüm stack'i başlat:

    ``` bash
    docker compose up --build -d
    ```

3.  Sağlık kontrolü:

    `http://localhost:8080/actuator/health`

4.  Yalnızca veritabanını container'da çalıştırıp uygulamayı host'ta
    çalıştırmak istersen:

    ``` bash
    docker compose up -d postgres
    SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/appointments
    SPRING_DATASOURCE_USERNAME=appointments_app
    SPRING_DATASOURCE_PASSWORD=<yerel-parola>
    mvn spring-boot:run
    ```

`Dockerfile`, uygulamayı Maven ile derleyen bir aşama ve Temurin 21 JRE
tabanlı çalışma aşaması kullanır.

## 7. Test ve Doğrulama

-   `SlotCalculatorTest` içinde slot hesaplaması ve mola ile ilgili
    temel senaryolar yer alır.
-   Bu başlangıç sürümünde Maven build/test sonucu doğrulanmış değildir;
    ilk adım olarak `mvn test` ve `mvn package` çalıştırılmalıdır.
-   Ek testler: çalışma saatleri sınırları, birden fazla mola, mola ile
    kısmi çakışma, bloklu dönemler, mevcut randevular, 7 günlük ufuk,
    geçmiş slotlar ve saat dilimi/DST senaryoları.
-   Eşzamanlı rezervasyon testi eklenmeli: aynı çalışanın aynı saatine
    iki istek geldiğinde yalnızca biri başarılı olmalı.
-   Veritabanı exclusion constraint davranışı gerçek PostgreSQL üzerinde
    entegrasyon testiyle doğrulanmalıdır.

## 8. Bilinen Eksikler ve Güvenlik Notları

-   Yönetim API uçlarında kimlik doğrulama/yetkilendirme yoktur.
    İnternete açık sunucuya bu haliyle koyma.
-   Randevu oluşturma uç noktası son kullanıcı Telegram akışı değildir;
    yalnızca başlangıç/yönetim uç noktasıdır.
-   Çalışan bazlı giriş ve yalnızca kendi randevularını onaylama yetkisi
    henüz yoktur.
-   Telegram bot transport'u, komut/mesaj işleyicileri, konuşma akışı,
    müşteri hesabı eşlemesi ve bildirimler henüz bağlanmamıştır.
-   Servis katmanı şu an 40 dakika zorunluluğunu, 7 günlük ufku ve tüm
    müsaitlik kurallarını doğrulamıyor. Bunlar tamamlanmadan gerçek
    kullanıcı rezervasyonu açılmamalıdır.
-   Hata yönetimi, rate limiting, TLS, secret yönetimi, yedekleme,
    loglarda kişisel veri maskeleme ve veri saklama/silme politikaları
    üretim öncesinde ele alınmalıdır.

## 9. Önerilen Geliştirme Sırası

1.  Projeyi JDK 21/Maven ile derle; mevcut testleri çalıştır ve hataları
    gider.
2.  Çalışma saatleri, mola, bloklu dönem ve mevcut randevulara göre
    eksiksiz `AvailabilityService` geliştir.
3.  Slot süresi ve 7 günlük ufuk dahil tüm iş kurallarını servis
    katmanında doğrula; veritabanı çakışma kısıtını koru.
4.  Çalışan kimlik doğrulaması ve rol/yetki modelini ekle; admin
    API'sini güvenli hale getir.
5.  Telegram bot adapter'ını business service katmanından ayrı tasarla;
    böylece ileride WhatsApp adapter'ı eklenebilir.
6.  Onay/red sonrası müşteri ve çalışana bildirim gönder; tekrar deneme
    ve hata senaryolarını tasarla.
7.  Entegrasyon ve eşzamanlılık testlerini ekle; ardından Docker imajı
    ve dağıtım yapılandırmasını doğrula.

## 10. Proje Dosyaları

-   `pom.xml` --- Maven bağımlılıkları ve Java/Spring Boot sürümleri.
-   `compose.yaml` --- PostgreSQL + uygulama servisleri, kalıcı volume
    ve health check.
-   `Dockerfile` --- uygulama imajı için çok aşamalı build.
-   `src/main/resources/application.yml` --- datasource, JPA, Flyway,
    Actuator ve uygulama ayarları.
-   `src/main/resources/db/migration/V1__init_schema.sql` --- ilk
    veritabanı şeması.
-   `src/main/java/com/example/appointment` --- Spring Boot uygulama
    kodu.
-   `src/test/java/.../SlotCalculatorTest.java` --- slot hesaplayıcı
    testleri.
-   `README.md` --- kurulum ve başlangıç API bilgileri.

------------------------------------------------------------------------

**Doküman notu:** Bu dosya başlangıç kod tabanını açıklayan teknik
rehberdir; doğrulanmış derleme/test raporu veya üretime hazır olma
garantisi değildir.
