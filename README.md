## Як запустити сервіс

Потрібні Java 17, Maven і Docker:

```bash
docker compose up -d --wait
mvn spring-boot:run
```

Сервіс стартує на http://localhost:8080, одразу після запуску є демо-дані:

- користувачі `alice`, `bob`, `carol`, `dave` і `operator`, пароль збігається з іменем;
- гаманці з ненульовим балансом: 1 і 5 - alice, 2 - bob, 3 - carol, 4 - dave;
- затверджені заявки: №1 - з гаманця 1 на 2 (пункти 1-3), №2 - з 2 на 3 (пункти 4-5), №3 - з 4 на 1 (пункт 6). Дата пунктів 1 і 6 уже настала, тож протягом хвилини сервіс сам створить для них платежі - це автоматичний режим.

## Як запустити тести

```bash
mvn test
```

Потрібен лише Docker.

## Приклади запитів до API

Кожну команду можна виконати в терміналі (bash, на Windows - Git Bash) або вставити в Postman прямо в рядок адреси: він сам заповнить метод, адресу, заголовки й тіло. У командах підтвердження замість `{id}` підстав `id` з відповіді на створення платежу.

**Токени.** Спершу токен alice:

```bash
curl http://localhost:8180/realms/wallet/protocol/openid-connect/token \
  -d grant_type=password -d client_id=wallet-cli -d username=alice -d password=alice
```

Скопіюй з відповіді `access_token`. У терміналі збережи його: `TOKEN=<access_token>`. У Postman підстав його замість `$TOKEN` у заголовку `Authorization`. Токен operator отримують тією самою командою з `username=operator -d password=operator` і зберігають як `OPERATOR_TOKEN`.

**Переказ.** Створити - гроші ще не рухаються:

```bash
curl -X POST http://localhost:8080/api/v1/payments/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: key-1" \
  -H "Content-Type: application/json" \
  -d '{"sourceWalletId": 1, "targetWalletId": 2, "amount": 250.00, "currency": "UAH"}'
```

Якщо повторити цю команду з тим самим `Idempotency-Key`, сервіс поверне той самий платіж, а не створить другий.

Підтвердити (лише тепер гроші переказуються) і подивитися баланс:

```bash
curl -X POST http://localhost:8080/api/v1/payments/{id}/confirm -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/wallets/1 -H "Authorization: Bearer $TOKEN"
```

**Поповнення.** Створює власник гаманця, а підтверджує лише operator:

```bash
curl -X POST http://localhost:8080/api/v1/payments/top-up \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: key-2" \
  -H "Content-Type: application/json" \
  -d '{"walletId": 1, "amount": 500.00, "currency": "UAH"}'
curl -X POST http://localhost:8080/api/v1/payments/{id}/confirm -H "Authorization: Bearer $OPERATOR_TOKEN"
```

**Списання і від'ємний баланс.** Списання підтверджує власник гаманця. На гаманці 5 лише 1 000 USD, тож це списання створиться, але підтвердження поверне `422` з кодом `INSUFFICIENT_FUNDS`:

```bash
curl -X POST http://localhost:8080/api/v1/payments/withdrawal \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: key-3" \
  -H "Content-Type: application/json" \
  -d '{"walletId": 5, "amount": 5000.00, "currency": "USD"}'
curl -X POST http://localhost:8080/api/v1/payments/{id}/confirm -H "Authorization: Bearer $TOKEN"
```

**Заявка на фінансування.** Переглянути графік і вручну створити платіж по пункту 2, ще до його дати:

```bash
curl http://localhost:8080/api/v1/funding-requests/1 -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/funding-requests/1/items/2/payment -H "Authorization: Bearer $TOKEN"
```

Цей платіж підтверджують, як і будь-який інший. Після цього пункт стає оплаченим, а заявка - частково оплаченою (`PARTIALLY_PAID`). Призупинити й відновити заявку може лише operator:

```bash
curl -X POST http://localhost:8080/api/v1/funding-requests/1/suspend -H "Authorization: Bearer $OPERATOR_TOKEN"
curl -X POST http://localhost:8080/api/v1/funding-requests/1/resume -H "Authorization: Bearer $OPERATOR_TOKEN"
```
