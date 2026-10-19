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

- Java 21, Spring Boot, Spring Data JPA (Hibernate), Maven
- PostgreSQL (на Render), Redis
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

Параметри підключення до бази, `JWT_SECRET` і `REDIS_*` передаються змінними середовища
(див. таблицю нижче). Тримай їх у файлі `.env` поруч із `docker-compose.yml`
і не додавай цей файл у репозиторій.

## Конфігурація

Основні налаштування лежать в `application.properties`. Секрети підставляються зі змінних середовища.

| Змінна середовища             | Призначення                                              | За замовчуванням |
|-------------------------------|----------------------------------------------------------|------------------|
| `RENDER_POSTGRESQL_USER`      | Користувач PostgreSQL                                    | немає, обов'язкова |
| `RENDER_POSTGRESQL_PASSWORD`  | Пароль PostgreSQL                                        | немає, обов'язкова |
| `JWT_SECRET`                  | Ключ підпису JWT                                         | у коді є запасне значення, обов'язково задай власний |
| `REDIS_HOST` / `REDIS_PORT`   | Підключення до Redis                                     | `localhost` / `6379` |
| `PORT`                        | Порт сервера (використовується в `Dockerfile`)           | немає |
| `JAVA_OPTS`                   | Параметри JVM                                            | `-Xmx512m -Xms256m` |

Інші параметри в `application.properties`:

- `spring.datasource.url`: за замовчуванням вказує на PostgreSQL на Render (`sslmode=require`).
  Для локальної бази перевизнач її змінною `SPRING_DATASOURCE_URL`, наприклад
  `jdbc:postgresql://localhost:5432/wormap`
- `spring.jpa.hibernate.ddl-auto=update`: Hibernate сам створює і оновлює таблиці
- `nominatim.api.url=https://nominatim.openstreetmap.org/search?q=`: адреса геокодера
- логи пишуться в `./logs/my-app.log`

Публічний Nominatim має обмеження частоти запитів (приблизно 1 запит на секунду),
тому не став інтервал парсера дуже малим.

## Обмеження та плани

- Парсер залежить від розмітки ukr.net: якщо сайт змінить структуру, парсер треба оновити
- Місце визначається лише для міст зі списку в `training_data.txt`; неоднозначні назви можуть давати хибний збіг
- Плани: підтримка інших мов і джерел новин, кластеризація схожих новин, фільтри за часом і ключовими словами, сповіщення за регіоном
