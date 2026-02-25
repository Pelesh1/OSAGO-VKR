OSAGO-VKR

Веб-приложение для страхования автомобилей: расчет ОСАГО, оформление полиса, подача и обработка страховых случаев, личный кабинет клиента и страхового агента, уведомления, чат, отчеты.

## 1. Стек проекта

- Java 17
- Spring Boot
- PostgreSQL
- Flyway (миграции БД)
- Maven Wrapper (`mvnw`, `mvnw.cmd`)
- Frontend: HTML/CSS/JavaScript (static resources)


## 2. Структура репозитория

- `src/main/java` — backend (контроллеры, сервисы, workflow)
- `src/main/resources/static` — frontend страницы и скрипты
- `src/main/resources/db/migration` — миграции Flyway
- `db/` — SQL-файлы для ручного развертывания БД (если используются)
- `diagrams/` — диаграммы классов (PlantUML)
- `load/` — сценарии и артефакты нагрузочного тестирования

## 3. Требования для локального запуска

- Установлен JDK 17
- Установлен PostgreSQL 14+ (рекомендуется 15+)
- Доступен `psql` в системе (или использовать полный путь до `psql.exe`)
- Свободен порт `8080` (или изменить порт приложения)

## Тестовые учетные записи

После импорта тестовых данных доступны аккаунты:

- Клиент: `podryadov89@mail.ru` / `123456`
- Агент: `new.agent@mail.ru` / `Agent12345!`

Если учетные записи не появились, выполните импорт `db/seed.sql`.

Либо можно создать самостоятельно 

Тесты


Запуск тестов:
.\mvnw.cmd test


Нагрузочное тестирование (JMeter)


В репозитории есть материалы в load/.


Пример CLI-запуска:


jmeter -n -t C:\path\to\osago\load\osago_load_500.jmx -l C:\path\to\osago\load\results_500.jtl -e -o C:\path\to\osago\load\report_500
После выполнения HTML-отчет открывать из:
load/report_500/index.html (пример)
