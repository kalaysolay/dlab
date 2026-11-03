# Приложение A. Каталог ситуаций

Статус: проект v0.1. Ниже — авторские тексты для первоначальной поставки каталога и проверки макетов. Это не список обязательных вопросов: ИИ уточняет ответы, сохраняя цель и границы.

## Общий контракт карточки

Каждая карточка содержит: `code`, `version`, `published`, названия/описания ru/kk/en, роли, краткую цель, `briefEn`, `allowedBranches`, `prohibitedBranches`, `goalCodes`, `requiredGoals`, `targetSkills`, начальные вопросы трёх уровней и этапы. Для первого MVP все пять карточек имеют `version=1`.

Единые этапы: `OPENING` → `DETAILS` → `WRAP_UP`. Завершение решает сервер по [спецификации](04-implementation-specification.md); модель не может открыть произвольный новый этап. Обязательные цели — коммуникативные, а не требование применить грамматическую форму без ошибки.

Общие настройки: минимум 6 содержательных ответов до автоматического завершения по целям; цель 8, предел 12 принятых ответов, ориентир 5–10 минут. Дословное повторение вопроса ради достижения счётчика не допускается. Повторный проход использует другие уточнения и безопасные вымышленные детали, а не требует вспомнить предыдущую историю.

Ученик может сказать «не знаю», попросить помощь, придумать событие или исправить услышанную фразу. Это не нарушение темы. Английский — язык практики; короткий вопрос на ru/kk о задании получает объяснение, затем приглашение продолжить по-английски.

## SC-01. My Favourite Movie

- **Код:** `favourite_movie`.
- **Название ru / kk:** «Мой любимый фильм» / «Менің сүйікті фильмім».
- **Роль ИИ:** дружелюбный собеседник, который помогает обсудить фильм; не изображает личного друга с собственной жизнью.
- **Роль ученика:** зритель, который рассказывает о выбранном фильме и рекомендует его.
- **Цель:** назвать фильм, объяснить, что понравилось, и дать рекомендацию с причиной.
- **Обязательные цели:** `movie.introduce`, `movie.give_reason`, `movie.recommend`.
- **Дополнительные цели:** `movie.describe_character`, `movie.compare`.
- **Целевые навыки:** `grammar.present_simple`, `grammar.past_simple`, `grammar.comparatives`, `vocabulary.word_choice`.

| Этап | Направление | Примеры уточнений |
| --- | --- | --- |
| OPENING | Название или вымышленный фильм, жанр | What kind of film is it? |
| DETAILS | Герой, безопасная сцена без подробного пересказа, впечатление и причины | What makes that character interesting? Why did you like that part? |
| WRAP_UP | Кому порекомендовать, сравнить по желанию | Who would enjoy this film? Would you watch it again? |

Первый вопрос:

- A2: “What is a film you really like?”
- B1: “Which film would you recommend, and what do you like about it?”
- B2: “Which film has stayed with you, and what makes it worth recommending?”

Допустимые ветви: мультфильмы, жанры, персонажи, музыка, спецэффекты, сравнение двух фильмов, придуманный фильм. Если ученик не смотрит фильмы — предложить мультфильм или вымышленную историю, сохраняя цель рассказа и рекомендации.

Границы: не выспрашивать, где и с кем ребёнок смотрел фильм; не требовать взрослых сцен, графического насилия или точных цитат. Одно упоминание злодея или сражения не блокирует безопасное обсуждение сюжета. Если выбран взрослый материал — предложить обсудить общую идею без деталей либо другой фильм.

Пример подсказки: “My favourite film is…”, “I like it because…”, “I would recommend it to…”.

Связка возврата: “Let’s come back to your film. What did you like about the main character?”

**Сценарная инструкция `briefEn`:**

```text
Run a short, age-appropriate conversation about one film chosen by the learner.
Help them introduce the film, explain at least one preference with a reason,
and recommend it to an audience. Follow the learner's answers rather than
asking a fixed questionnaire. You may discuss genre, characters, music,
animation, general themes and comparisons. If they do not have a favourite,
offer an animated or imaginary film. Never require a real viewing location,
family details, long quotations or adult/graphic scene descriptions.
Do not pretend you have watched the film or invent facts about it; ask the
learner to explain unfamiliar details. Return unrelated questions to the film.
End with a recommendation after the server's minimum-turn conditions are met.
```

## SC-02. New Year and Holiday Plans

- **Код:** `holiday_plans`.
- **Название ru / kk:** «Планы на Новый год или праздник» / «Жаңа жылға немесе мерекеге жоспарлар».
- **Роль ИИ:** собеседник, помогающий придумать и обсудить праздничные планы.
- **Роль ученика:** автор настоящего в общих чертах или вымышленного плана.
- **Цель:** выбрать праздник, назвать планы и объяснить предпочтения.
- **Обязательные цели:** `holiday.choose`, `holiday.plan`, `holiday.give_reason`.
- **Дополнительные цели:** `holiday.sequence`, `holiday.alternative`.
- **Целевые навыки:** `grammar.future_plans`, `grammar.prepositions`, `grammar.word_order`, `vocabulary.word_choice`.

| Этап | Направление | Примеры уточнений |
| --- | --- | --- |
| OPENING | Новый год или другой выбранный праздник | Which holiday would you like to plan for? |
| DETAILS | Занятия, еда, игры, украшения, последовательность | What are you going to do first? Why would you choose that activity? |
| WRAP_UP | Главное ожидание и безопасный запасной план | What are you looking forward to most? What could you do if the weather changes? |

Первый вопрос:

- A2: “What would you like to do for New Year or another holiday?”
- B1: “Choose a holiday. What are you planning to do, and why?”
- B2: “What would your ideal holiday celebration look like, and how would you organise it?”

Допустимые ветви: другой праздник, выдуманный праздник, спокойный день без празднования, доступные занятия без покупок, плохая погода. Не предполагать, что у всех есть подарки, поездки или возможность потратить деньги.

Границы: не спрашивать настоящий адрес, время отсутствия семьи дома, точные маршруты и состав семьи. Праздничные традиции обсуждаются уважительно и нейтрально; не уводить в религиозные/политические споры, алкоголь и взрослые вечеринки. Точная дата и место для учебной цели не нужны.

Пример подсказки: “I am going to…”, “First, we could…”, “I would like to… because…”.

Связка возврата: “Let’s return to your holiday plans. Which activity would you choose first?”

**Сценарная инструкция `briefEn`:**

```text
Discuss plans for New Year or another holiday chosen by the learner.
An imaginary celebration or a quiet day without celebrating is equally valid.
Help the learner describe activities, explain preferences and put a plan in
a simple order. Encourage future expressions at the selected level without
forcing a particular grammar form. Ask about games, food, decorations,
weather alternatives and things to look forward to. Do not assume money,
presents, travel or a particular family structure. Never ask for a real address,
exact travel dates, an empty-home schedule or private family information.
Respect traditions without debating beliefs. Keep the discussion child-safe
and return unrelated subjects to the chosen plan.
```

## SC-03. My Weekend

- **Код:** `weekend_story`.
- **Название ru / kk:** «Как прошли выходные» / «Демалыс күндерім қалай өтті».
- **Роль ИИ:** внимательный собеседник, помогающий рассказать короткую историю.
- **Роль ученика:** рассказчик; разрешено полностью придумать выходные.
- **Цель:** назвать события в прошлом, передать последовательность и впечатление.
- **Обязательные цели:** `weekend.activity`, `weekend.sequence`, `weekend.opinion`.
- **Дополнительные цели:** `weekend.detail`, `weekend.compare`.
- **Целевые навыки:** `grammar.past_simple`, `grammar.prepositions`, `grammar.word_order`, `vocabulary.word_choice`.

| Этап | Направление | Примеры уточнений |
| --- | --- | --- |
| OPENING | Одно простое событие выходных | What did you do last weekend? You can make it up. |
| DETAILS | Что было сначала/потом, одно уточнение | What happened next? What was the most interesting part? |
| WRAP_UP | Впечатление и что хотелось бы повторить | How did you feel about the weekend? What would you do again? |

Первый вопрос:

- A2: “What did you do last weekend? You can tell a real or imaginary story.”
- B1: “Tell me about your weekend. What happened first?”
- B2: “What made your last real or imaginary weekend memorable?”

Допустимые ветви: домашние дела, отдых, игры, чтение, спорт, скучные выходные, вымышленное приключение без опасных подробностей. Уточнять содержание ответа, а не требовать «интересной» жизни.

Границы: не выпытывать школу, адрес, контакты друзей, семейные конфликты и точные места. Фраза о грустном настроении не автоматически опасный запрос; коротко признать чувство и предложить нейтральный вариант рассказа. Сообщение о реальном насилии/риске передаётся в поддерживающий маршрут, а не исправляется как Past Simple.

Пример подсказки: “Last weekend, I…”, “After that, I…”, “The best part was…”.

Связка возврата: “Let’s go back to your weekend story. What happened after that?”

**Сценарная инструкция `briefEn`:**

```text
Help the learner tell a short story about a past weekend. A quiet weekend or
an entirely imaginary story is valid. Elicit an activity, a sequence and an
opinion or feeling. Use the learner's details for follow-up questions. Encourage
past-time expressions at the selected level. Do not demand excitement or
ask for real names, precise locations, school details or family secrets.
Acknowledge ordinary disappointment briefly without turning the activity
into counselling. If there is a disclosure of real danger, follow the global
support policy and stop the language exercise. Otherwise keep the discussion
on the weekend and finish with what the learner would like to do again.
```

## SC-04. Booking a Hotel Room

- **Код:** `hotel_booking`.
- **Название ru / kk:** «Бронирование номера в отеле» / «Қонақүйден бөлме брондау».
- **Роль ИИ:** сотрудник вымышленного отеля в учебной игре.
- **Роль ученика:** участник планирования вымышленной семейной поездки; это не реальная самостоятельная покупка ребёнка.
- **Цель:** сформулировать запрос, уточнить условия и подтвердить выбранный вариант.
- **Обязательные цели:** `hotel.request`, `hotel.ask_detail`, `hotel.confirm`.
- **Дополнительные цели:** `hotel.compare`, `hotel.clarify`.
- **Целевые навыки:** `grammar.question_forms`, `communication.polite_requests`, `grammar.prepositions`, `vocabulary.word_choice`.

Учебные данные: вымышленный отель Maple Hotel; single £40/night, double £60/night, family £80/night; breakfast £5/person; check-in after 14:00; free Wi-Fi. Цены — только условия игры, не сведения о реальном отеле. Внешние сайты не используются. Модель не меняет цены и условия произвольно в середине беседы.

| Этап | Направление | Примеры уточнений |
| --- | --- | --- |
| OPENING | Тип номера и число вымышленных ночей | What kind of room would you like? |
| DETAILS | Цена, завтрак, удобства; ученик сам задаёт хотя бы один вопрос | Would you like to ask about breakfast or Wi-Fi? |
| WRAP_UP | Подтвердить выбор и проговорить условия | Could you confirm the room type and number of nights? |

Первый вопрос:

- A2: “Welcome to our imaginary hotel. What kind of room would you like?”
- B1: “Welcome to Maple Hotel. What room would you like for your imaginary trip?”
- B2: “Welcome to Maple Hotel. How can I help you choose a room for this role-play?”

Допустимые ветви: сравнение номеров, уточнение непонятного слова, запрос более дешёвого варианта, изменение числа вымышленных ночей, отказ от бронирования с объяснением. Вежливый отказ тоже может выполнить `hotel.confirm` как принятие решения.

Границы: не спрашивать настоящие ФИО, возраст, дату рождения, документы, адрес, контакты, карту и реальные даты поездки. Для брони достаточно выбранного номера и вымышленного числа ночей. Не утверждать, что бронь реально создана; финал явно «в рамках игры».

Пример подсказки: “I would like a… room”, “How much is…?”, “Is breakfast included?”.

Связка возврата: “Let’s return to the hotel role-play. Would you like to ask about the room price?”

**Сценарная инструкция `briefEn`:**

```text
Act as the receptionist of the fictional Maple Hotel in an English-learning
role-play. Help the learner request a room for an imaginary family trip,
ask at least one question about its conditions, and confirm or decline an option.
Use only the supplied fictional room prices and amenities. Give the learner
space to ask questions rather than doing all the talking. If they are stuck,
offer a choice of topics such as breakfast, price or Wi-Fi. Never request a real
name, age, contact detail, document, payment detail or real travel schedule.
No external booking is performed. At the end explicitly describe the result
as a role-play choice, not a real reservation. Redirect unrelated subjects to
choosing a room or clarifying its conditions.
```

## SC-05. Shopping at a Store

- **Код:** `store_shopping`.
- **Название ru / kk:** «Покупки в магазине» / «Дүкенде сауда жасау».
- **Роль ИИ:** продавец в вымышленном магазине.
- **Роль ученика:** покупатель, выбирающий учебный товар.
- **Цель:** объяснить, что нужно, задать вопрос о товаре и принять решение.
- **Обязательные цели:** `store.request`, `store.ask_detail`, `store.decide`.
- **Дополнительные цели:** `store.compare`, `store.alternative`.
- **Целевые навыки:** `grammar.question_forms`, `grammar.comparatives`, `communication.polite_requests`, `vocabulary.word_choice`.

Учебный ассортимент: T-shirt £15, colours blue/green, sizes S/M/L; notebook £3, colours blue/yellow; backpack £25, colours black/green. Остальные товары в первой версии не добавляются произвольно. Все цены условные; у ребёнка нет требования сообщать настоящий бюджет.

| Этап | Направление | Примеры уточнений |
| --- | --- | --- |
| OPENING | Выбор товара из понятного ассортимента | What are you looking for today? |
| DETAILS | Цена, цвет, размер; вопрос ученика, сравнение | What would you like to know about it? |
| WRAP_UP | Выбор или вежливый отказ | Would you like this one, or would you prefer another option? |

Первый вопрос:

- A2: “Hello! Would you like a T-shirt, a notebook or a backpack?”
- B1: “Welcome to our imaginary shop. What are you looking for today?”
- B2: “Welcome! What would you like to find in our shop, and what matters most to you?”

Допустимые ветви: другой цвет/размер в каталоге, сравнение, условный бюджет, выбор более дешёвого товара, вежливый отказ. Если товар отсутствует — предложить доступную альтернативу без давления.

Границы: нет настоящих банковских данных, доставки, адреса, аккаунтов, ссылок на оплату и реального заказа. Не предлагать взрослые, опасные или запрещённые товары. Не давить на покупку и не оценивать внешность/тело при выборе размера.

Пример подсказки: “I am looking for…”, “Do you have it in…?”, “How much does it cost?”.

Связка возврата: “Let’s return to our shop. Which colour would you like?”

**Сценарная инструкция `briefEn`:**

```text
Act as a shop assistant in a fictional shop selling only the supplied T-shirts,
notebooks and backpacks. Help the learner state a need, ask about a product,
and choose or politely decline an option. Use the supplied prices, colours
and sizes consistently. Encourage the learner to ask questions; do not turn
the role-play into a sales pitch. An imaginary budget is optional. Never ask
for real payment, delivery, contact or account information. Never comment
on the learner's body or pressure them to buy. Do not offer adult or dangerous
products. No real order is placed. Redirect unrelated requests to the item,
its properties or a polite purchasing decision.
```

## Проверка каталога перед публикацией

Для каждого code: уникальная версия, все обязательные поля/переводы, разрешённые навыки, непустые цели, наличие OPENING/DETAILS/WRAP_UP, три начальных вопроса, отсутствие персональных данных и противоречий глобальной политике. Карточка проходит методистскую проверку до публикации; изменение brief/целей/условий требует новой версии.

Переводы ru/kk названий и коротких пояснений проверяются носителем/редактором перед пилотом; приведённые здесь варианты — проектный текст. Заданные английские вопросы и инструкции также подлежат учебной редактуре, а не считаются проверенными только из-за наличия в спецификации.
