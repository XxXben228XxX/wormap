# Wormap — News Map

Веб-сервіс, який автоматично збирає новини з ukr.net, визначає місце події з тексту
і показує новини маркерами на інтерактивній карті. Клік по маркеру відкриває назву новини,
локацію та посилання на оригінальну статтю.

Курсова робота «Геопросторове розміщення новин на інтерактивній карті з використанням Spring Boot»
з предмету «Конструювання програмного забезпечення JAVA».

## Можливості

- Автоматичний збір новин з ukr.net (Jsoup), повторний запуск за розкладом
- Визначення місця події в тексті: лематизація українських слів + модель OpenNLP
- Геокодування через Nominatim (OpenStreetMap): назва місця → координати
- Невеликий випадковий зсув координат для міст без точної адреси, щоб маркери не накладалися
- Інтерактивна карта Leaflet з кількома стилями (OpenStreetMap, Water Color, Terrain)
- Пошук по карті за назвою місця або заголовком; якщо нічого не знайдено, пошук через Nominatim
- Кнопка «Новини без координат»: список новин, для яких місце визначити не вдалося
- Автоматичне оновлення даних на карті кожні 10 хвилин
- Дані зберігаються у форматі GeoJSON; вже оброблені посилання не обробляються повторно

## Як це працює

```
ukr.net ──► NewsParser (Jsoup)
               │
               ├─► GeminiGeoLocator ── лематизація + OpenNLP ──► назва місця
               │        ▲
               │        └── training_data.txt (створює GeoNamesToOpenNLP зі списків міст)
               │
               ├─► GeoCoder ── Nominatim API ──► широта, довгота
               │
               ├─► data/news.json                  (новини з координатами, GeoJSON)
               ├─► processed_links.txt             (вже оброблені посилання)
               └─► news_without_coordinates.txt    (новини без визначеної локації)

Браузер ◄── Leaflet UI ◄── /api/news-data
```

Основні класи:

| Клас                | Призначення                                                                 |
|---------------------|-----------------------------------------------------------------------------|
| `NewsParser`        | Головний компонент: збір новин, виклик геолокатора і геокодера, збереження  |
| `GeminiGeoLocator`  | Пошук міст у тексті: спершу за лемами, потім моделлю OpenNLP. Назва історична, сервіс Google Gemini тут не використовується |
| `GeoNamesToOpenNLP` | Збирає назви міст з файлів (GG.txt, UA*.txt, coordinates.json) у `training_data.txt` |
| `GeoCoder`          | Пряме і зворотне геокодування через Nominatim                               |

## Технології

- Java, Spring Boot, Maven
- Jsoup, slf4j
- Apache OpenNLP (`en-ner-location`, `en-pos-maxent`, `opennlp-uk-ud-iu-lemmas`), LanguageTool (українська лематизація)
- Nominatim API (OpenStreetMap), дані GeoNames
- Leaflet, Leaflet Search, Maptiler / Stadia Maps (тайли)
- Docker, Docker Compose
- Розгортання: render.com

## Структура репозиторію

```
wormap/
├── Dockerfile
├── docker-compose.yml
└── IdeaProjects/
    └── demo1/            # Maven-проєкт Spring Boot
        ├── pom.xml
        └── src/
```

## Запуск

### Вимоги

- JDK 21 (саме він використовується в `Dockerfile`)
- Maven 3.9+
- Docker (для запуску в контейнері)

### Локально через Maven

```
cd IdeaProjects/demo1
mvn clean install -DskipTests
java -Dserver.port=8080 -jar target/*.jar
```

Карта буде доступна на `http://localhost:8080`.

### Через Docker

Збірка двоетапна: Maven збирає jar, потім він копіюється в образ `eclipse-temurin:21-jdk-jammy`.
Порт береться зі змінної `PORT` (на Render вона задається автоматично).

```
docker build -t wormap .
docker run -p 8080:8080 -e PORT=8080 wormap
```

### Через Docker Compose

```
docker compose up --build
```

Перед запуском відкрий `docker-compose.yml` і перевір змінні середовища:
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`.

> Не зберігай реальні паролі та ключі в `docker-compose.yml` у репозиторії.
> Винеси їх у файл `.env` (він додається до `.gitignore`) і підставляй як `${DB_PASSWORD}`.

## Конфігурація

| Налаштування / змінна          | Призначення                                                    |
|--------------------------------|----------------------------------------------------------------|
| `PORT`                         | Порт сервера (використовується в `Dockerfile`)                 |
| `nominatim.api.url`            | Адреса Nominatim, наприклад `https://nominatim.openstreetmap.org/search?q=` |
| `SPRING_DATASOURCE_URL`        | Рядок підключення до бази даних                                |
| `SPRING_DATASOURCE_USERNAME`   | Користувач бази даних                                          |
| `SPRING_DATASOURCE_PASSWORD`   | Пароль бази даних                                              |
| `JWT_SECRET`                   | Ключ підпису JWT                                               |
| `JAVA_OPTS`                    | Параметри JVM (за замовчуванням `-Xmx512m -Xms256m`)           |

Публічний Nominatim має обмеження частоти запитів (приблизно 1 запит на секунду),
тому не змінюй інтервал парсера на дуже малий.

## Обмеження та плани

- Парсер залежить від розмітки ukr.net: якщо сайт змінить структуру, парсер треба оновити
- Місце визначається лише для міст зі списку в `training_data.txt`; неоднозначні назви можуть давати хибний збіг
- Плани: підтримка інших мов і джерел новин, кластеризація схожих новин, фільтри за часом і ключовими словами, сповіщення за регіоном
