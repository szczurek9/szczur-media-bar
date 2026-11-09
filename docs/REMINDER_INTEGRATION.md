# Integracja ze Szczur Reminder (Media Bar 1.1.0, protokół v1)

Media Bar 1.1.0 może pokazywać przypomnienia z aplikacji **Szczur Reminder** (`com.szczurek9.reminder`).
Dotychczasowe funkcje paska muzycznego nie zostały zmienione.

## Jak to działa
- Reminder łączy się z serwisem `ReminderBridgeService` (Messenger, Bundle). Serwis jest chroniony uprawnieniem
  `com.szczurek9.mediabar.permission.REMINDER_BRIDGE` o poziomie **signature** – obie aplikacje muszą być podpisane tym samym kluczem
  (budowanie obu na tym samym komputerze kluczem debug wystarcza).
- Media Bar nie ma własnej logiki przypomnień: dostaje gotową kartę (tytuł, opis w Markdownie, etykieta terminu, czas pokazania,
  PendingIntent-y akcji) i ją wyświetla. Przyciski „Wykonane”, „Przypomnij później” i „Otwórz” uruchamiają PendingIntent-y
  przysłane przez Reminder (niewyeksportowane receivery Reminder), więc nikt trzeci nie może podrobić akcji.
- Karta pojawia się w **osobnym oknie** nakładki (`ReminderOverlayController`). Na czas jej pokazywania pasek muzyczny jest
  ukrywany (`MusicOverlayController.setReminderActive`) i wraca po jej zniknięciu.
- Działa bez grającej muzyki. Wymaga: uprawnienia „Wyświetlanie nad innymi aplikacjami”, aktywnego dostępu do powiadomień
  (listener musi być połączony) oraz włączonego przełącznika „Przyjmuj przypomnienia” w ustawieniach Media Bar.
- Gdy ekran jest zablokowany lub wyłączony, Media Bar odpowiada `UNAVAILABLE` i Reminder pokazuje zwykłe powiadomienie Androida.
- Dotknięcie kompaktowej karty rozwija ją; długie dotknięcie zamyka. Po upływie czasu (3–10 s, ustawia Reminder) kompaktowa karta znika,
  ale przypomnienie nie jest oznaczane jako wykonane – wpis zostaje w centrum powiadomień. Dotknięcie poza rozwiniętą kartą zamyka ją.
- Rozwijanie jest animowane tak jak w pasku muzycznym: okno systemowe zmienia rozmiar jednorazowo (na stały, duży), a sama animacja
  (położenie, rozmiar i zaokrąglenie karty) dzieje się w fazie rysowania Compose – bez zmian rozmiaru okna w każdej klatce.
- Karta przypomnienia jest rysowana wyraźniej niż pasek muzyczny: przezroczystość z ustawień jest podnoszona (88% → ok. 97%), żeby tekst był czytelny.
- Wygląd kompaktowy: forma Slim → wąska pastylka, pozostałe formy → standardowa karta. Rozmiar, motyw, przezroczystość i pozycja
  pochodzą z ustawień Media Bar.

## Protokół v1
| Wiadomość | Kierunek | Treść |
|---|---|---|
| `MSG_HELLO` (1) | Reminder → MB | pusta; odpowiedź `MSG_HELLO_REPLY` (2): `proto`, `mb_version`, `can_show`, `reason` |
| `MSG_SHOW` (3) | Reminder → MB | `id`, `title`, `description`, `when`, `display_sec`, `done_pi`, `open_pi`, `snooze_pis`, `snooze_labels`; odpowiedź `MSG_SHOW_RESULT` (4): `id`, `status` (0 pokazano, 1 niedostępne, 2 odrzucone), `reason` |
| `MSG_HIDE` (5) | Reminder → MB | `id` – schowaj pasek, jeśli pokazuje to przypomnienie |

Limity: tytuł 200 znaków, opis 4000, do 6 przycisków odłożenia, czas pokazania 3–10 s.
Powody `reason`: `disabled_in_media_bar`, `no_overlay_permission`, `locked`, `screen_off`, `listener_not_connected`, `window_add_failed`, `bad_payload`.

## Zgodność wersji
| Szczur Media Bar | Szczur Reminder | Uwagi |
|---|---|---|
| 1.0.0 | dowolna | brak mostu – Reminder działa przez zwykłe powiadomienia |
| 1.1.0 (versionCode 2) | 1.0.0 (versionCode 1) | pełna integracja, protokół v1 |

Zmiana protokołu zwiększy `PROTOCOL_VERSION`; przy niezgodności Reminder przechodzi na powiadomienia Androida i pokazuje to w Ustawieniach.

## Lista zmian względem 1.0.0
Numery linii dotyczą plików w wersji 1.1.0.

- ZMIENIONY: `app/build.gradle.kts` – linie dodane/zmienione (numeracja nowego pliku): 17–18; zastąpione linie starego pliku: 17–18
- ZMIENIONY: `app/src/main/AndroidManifest.xml` – linie dodane/zmienione (numeracja nowego pliku): 4–8, 60–69
- ZMIENIONY: `app/src/main/java/com/szczurek9/mediabar/MusicBarApp.kt` – linie dodane/zmienione (numeracja nowego pliku): 5, 15–18
- NOWY: `app/src/main/java/com/szczurek9/mediabar/integration/ReminderBridgeService.kt` (86 linii)
- NOWY: `app/src/main/java/com/szczurek9/mediabar/integration/ReminderProtocol.kt` (85 linii)
- ZMIENIONY: `app/src/main/java/com/szczurek9/mediabar/media/MusicNotificationListener.kt` – linie dodane/zmienione (numeracja nowego pliku): 7, 17, 28–33, 53–55
- ZMIENIONY: `app/src/main/java/com/szczurek9/mediabar/overlay/MusicOverlayController.kt` – linie dodane/zmienione (numeracja nowego pliku): 95–96, 167–173, 395; zastąpione linie starego pliku: 386
- NOWY: `app/src/main/java/com/szczurek9/mediabar/reminder/MarkdownText.kt` (169 linii)
- NOWY: `app/src/main/java/com/szczurek9/mediabar/reminder/ReminderBar.kt` (354 linii)
- NOWY: `app/src/main/java/com/szczurek9/mediabar/reminder/ReminderOverlayController.kt` (399 linii)
- ZMIENIONY: `app/src/main/java/com/szczurek9/mediabar/settings/BarSettings.kt` – linie dodane/zmienione (numeracja nowego pliku): 40–41
- ZMIENIONY: `app/src/main/java/com/szczurek9/mediabar/settings/MusicBarSettings.kt` – linie dodane/zmienione (numeracja nowego pliku): 178–190
- ZMIENIONY: `app/src/main/java/com/szczurek9/mediabar/settings/PreferencesRepository.kt` – linie dodane/zmienione (numeracja nowego pliku): 38, 95
- ZMIENIONY: `README.md` (opis, drzewo architektury, obsługa), NOWY: `docs/REMINDER_INTEGRATION.md`


Nic poza powyższym nie zostało zmienione; istniejące klasy muzyczne (`MusicSessionManager`, `MusicBar*`, kafelek) są nietknięte.

## Testy ręczne Media Bar po zmianach (regresja)
1. Muzyka gra → pasek pojawia się jak dawniej (formy Standard/Slim/Bubble, rozwinięcie, przeciąganie, blokada pozycji).
2. Pauza i „po zatrzymaniu” (ukryj / zostaw) działają jak dawniej; kafelek szybkich ustawień włącza/wyłącza pasek.
3. Przełącznik „Przyjmuj przypomnienia” wyłączony → Reminder pokazuje zwykłe powiadomienia.
4. Gra muzyka + przychodzi przypomnienie → pasek muzyczny znika, karta przypomnienia pojawia się, po zniknięciu wraca pasek muzyczny.
