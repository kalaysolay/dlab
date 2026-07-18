-- Наполнение topics: математика, 4 класс (экспорт из локальной БД).
-- Идемпотентно: повторный запуск безопасен (not exists).
-- percent-basics / finding-percent-of-number уже в baseline — здесь не трогаем.
--
-- Запуск (пример):
--   psql "host=... port=5432 dbname=damulab user=... sslmode=require" -v ON_ERROR_STOP=1 -f scripts/seed_math_grade4_topics.sql
--
-- После выполнения проверка:
--   select count(*) from topics t
--   join subjects s on s.id = t.subject_id
--   join grades g on g.id = t.grade_id
--   where s.code = 'math' and g.grade_no = 4;
--   -- ожидается 51
-- ========== Корневые темы ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'нумерация-многозначных-чисел-и-действия-с-ними',
       'Нумерация многозначных чисел и действия с ними',
       'Нумерация многозначных чисел и действия с ними'
from subjects s
join grades g on g.grade_no = 4
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'умножение-и-деление-на-однозначное-число',
       'Умножение и деление на однозначное число',
       'Умножение и деление на однозначное число'
from subjects s
join grades g on g.grade_no = 4
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'умножение-и-деление-на-однозначное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'скорость-время-расстояние',
       'Скорость, время, расстояние',
       'Скорость, время, расстояние'
from subjects s
join grades g on g.grade_no = 4
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'скорость-время-расстояние'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'производительность-и-задачи-на-движение',
       'Производительность и задачи на движение',
       'Производительность и задачи на движение'
from subjects s
join grades g on g.grade_no = 4
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'производительность-и-задачи-на-движение'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'умножение-и-деление',
       'Умножение и деление',
       'Умножение и деление'
from subjects s
join grades g on g.grade_no = 4
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'умножение-и-деление'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'геометрические-фигуры',
       'Геометрические фигуры',
       'Геометрические фигуры'
from subjects s
join grades g on g.grade_no = 4
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'геометрические-фигуры'
  );

-- ========== Дочерние: нумерация-многозначных-чисел-и-действия-с-ними ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'повторение-пройденного-в-3-классе',
       'Повторение пройденного в 3 классе',
       'Повторение пройденного в 3 классе'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'повторение-пройденного-в-3-классе'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'многозначные-числа-последовательности-чисел',
       'Многозначные числа. Последовательности чисел',
       'Многозначные числа. Последовательности чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'многозначные-числа-последовательности-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'класс-миллионов-округление-чисел',
       'Класс миллионов. Округление чисел',
       'Класс миллионов. Округление чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'класс-миллионов-округление-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'арифметические-действия-с-многозначными-числами',
       'Арифметические действия с многозначными числами',
       'Арифметические действия с многозначными числами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'арифметические-действия-с-многозначными-числами'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-сложение-и-вычитание-многозначных-чисел',
       'Письменное сложение и вычитание многозначных чисел',
       'Письменное сложение и вычитание многозначных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-сложение-и-вычитание-многозначных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'миллиграмм',
       'Миллиграмм',
       'Миллиграмм'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'миллиграмм'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'пространственные-геометрические-фигуры-измерение-объема',
       'Пространственные геометрические фигуры. Измерение объема',
       'Пространственные геометрические фигуры. Измерение объема'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'пространственные-геометрические-фигуры-измерение-объема'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'ар-гектар-единицы-площади',
       'Ар, гектар - единицы площади',
       'Ар, гектар - единицы площади'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'ар-гектар-единицы-площади'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'преобразование-единиц-измерения-величин-и-действия-с-ними',
       'Преобразование единиц измерения величин и действия с ними',
       'Преобразование единиц измерения величин и действия с ними'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'преобразование-единиц-измерения-величин-и-действия-с-ними'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'доли-единиц-времени',
       'Доли единиц времени',
       'Доли единиц времени'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'доли-единиц-времени'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'евро-доллар-операции-с-валютами',
       'Евро (€), доллар ($). Операции с валютами',
       'Евро (€), доллар ($). Операции с валютами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'евро-доллар-операции-с-валютами'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'рубль-операции-с-валютами',
       'Рубль (₽). Операции с валютами',
       'Рубль (₽). Операции с валютами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'нумерация-многозначных-чисел-и-действия-с-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'рубль-операции-с-валютами'
  );

-- ========== Дочерние: умножение-и-деление-на-однозначное-число ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'устные-приемы-вычислений-с-многозначными-числами',
       'Устные приемы вычислений с многозначными числами',
       'Устные приемы вычислений с многозначными числами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление-на-однозначное-число'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'устные-приемы-вычислений-с-многозначными-числами'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-и-деление-многозначного-числа-на-однозначное',
       'Умножение и деление многозначного числа на однозначное',
       'Умножение и деление многозначного числа на однозначное'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление-на-однозначное-число'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-и-деление-многозначного-числа-на-однозначное'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'признаки-делимости-на-2-5-10',
       'Признаки делимости на 2, 5, 10',
       'Признаки делимости на 2, 5, 10'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление-на-однозначное-число'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'признаки-делимости-на-2-5-10'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-и-деление-на-10-100-1-000',
       'Умножение и деление на 10, 100, 1 000',
       'Умножение и деление на 10, 100, 1 000'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление-на-однозначное-число'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-и-деление-на-10-100-1-000'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'деление-с-остатком-на-10-100-1-000',
       'Деление с остатком на 10, 100, 1 000',
       'Деление с остатком на 10, 100, 1 000'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление-на-однозначное-число'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'деление-с-остатком-на-10-100-1-000'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'отношения-между-множествами',
       'Отношения между множествами',
       'Отношения между множествами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление-на-однозначное-число'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'отношения-между-множествами'
  );

-- ========== Дочерние: скорость-время-расстояние ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-движение',
       'Задачи на движение',
       'Задачи на движение'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'скорость-время-расстояние'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-движение'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'переместительное-и-сочетательное-свойства-объединения-и-пересечения-множеств-при-решении-задач',
       'Переместительное и сочетательное свойства объединения и пересечения множеств при решении задач',
       'Переместительное и сочетательное свойства объединения и пересечения множеств при решении задач'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'скорость-время-расстояние'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'переместительное-и-сочетательное-свойства-объединения-и-пересечения-множеств-при-решении-задач'
  );

-- ========== Дочерние: производительность-и-задачи-на-движение ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'производительность',
       'Производительность',
       'Производительность'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'производительность'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'производительность-совместная-работа',
       'Производительность. Совместная работа',
       'Производительность. Совместная работа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'производительность-совместная-работа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'совместная-работа',
       'Совместная работа',
       'Совместная работа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'совместная-работа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-пропорциональное-деление',
       'Задачи на пропорциональное деление',
       'Задачи на пропорциональное деление'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-пропорциональное-деление'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'пропорциональное-деление',
       'Пропорциональное деление',
       'Пропорциональное деление'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'пропорциональное-деление'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-нахождение-неизвестного-по-двум-разностям',
       'Задачи на нахождение неизвестного по двум разностям',
       'Задачи на нахождение неизвестного по двум разностям'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-нахождение-неизвестного-по-двум-разностям'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-встречное-движение',
       'Задачи на встречное движение',
       'Задачи на встречное движение'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-встречное-движение'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-движение-в-противоположных-направлениях',
       'Задачи на движение в противоположных направлениях',
       'Задачи на движение в противоположных направлениях'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'производительность-и-задачи-на-движение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-движение-в-противоположных-направлениях'
  );

-- ========== Дочерние: умножение-и-деление ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-и-деление-числа-на-произведение',
       'Умножение и деление числа на произведение',
       'Умножение и деление числа на произведение'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-и-деление-числа-на-произведение'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'устные-приемы-умножения-и-деления-чисел-оканчивающихся-нолями',
       'Устные приемы умножения и деления чисел, оканчивающихся нолями',
       'Устные приемы умножения и деления чисел, оканчивающихся нолями'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'устные-приемы-умножения-и-деления-чисел-оканчивающихся-нолями'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-умножение-многозначных-чисел-оканчивающихся-нолями',
       'Письменное умножение многозначных чисел, оканчивающихся нолями',
       'Письменное умножение многозначных чисел, оканчивающихся нолями'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-умножение-многозначных-чисел-оканчивающихся-нолями'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-деление-многозначных-чисел-оканчивающихся-нолями',
       'Письменное деление многозначных чисел, оканчивающихся нолями',
       'Письменное деление многозначных чисел, оканчивающихся нолями'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-деление-многозначных-чисел-оканчивающихся-нолями'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-умножение-на-двузначное-число',
       'Письменное умножение на двузначное число',
       'Письменное умножение на двузначное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-умножение-на-двузначное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-деление-многозначных-чисел-на-двузначное-число',
       'Письменное деление многозначных чисел на двузначное число',
       'Письменное деление многозначных чисел на двузначное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-деление-многозначных-чисел-на-двузначное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-деление-многозначных-чисел-на-двузначное-число-с-остатком',
       'Письменное деление многозначных чисел на двузначное число с остатком',
       'Письменное деление многозначных чисел на двузначное число с остатком'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-деление-многозначных-чисел-на-двузначное-число-с-остатком'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-деление-многозначных-чисел-на-двузначное-число-с-нулем-в-частном',
       'Письменное деление многозначных чисел на двузначное число с нулем в частном',
       'Письменное деление многозначных чисел на двузначное число с нулем в частном'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-деление-многозначных-чисел-на-двузначное-число-с-нулем-в-частном'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'письменное-деление-многозначных-чисел',
       'Письменное деление многозначных чисел',
       'Письменное деление многозначных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'письменное-деление-многозначных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'алгоритм-умножения-на-трехзначное-число',
       'Алгоритм умножения на трехзначное число',
       'Алгоритм умножения на трехзначное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'алгоритм-умножения-на-трехзначное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'алгоритм-деления-на-трехзначное-число',
       'Алгоритм деления на трехзначное число',
       'Алгоритм деления на трехзначное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'алгоритм-деления-на-трехзначное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'алгоритм-умножения-и-деления-на-трехзначное-число',
       'Алгоритм умножения и деления на трехзначное число',
       'Алгоритм умножения и деления на трехзначное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'умножение-и-деление'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'алгоритм-умножения-и-деления-на-трехзначное-число'
  );

-- ========== Дочерние: геометрические-фигуры ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'геометрические-фигуры-куб-прямоугольный-параллелепипед-нахождение-объема-прямоугольного-параллелепипеда',
       'Геометрические фигуры: куб, прямоугольный параллелепипед. Нахождение объема прямоугольного параллелепипеда',
       'Геометрические фигуры: куб, прямоугольный параллелепипед. Нахождение объема прямоугольного параллелепипеда'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'геометрические-фигуры'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'геометрические-фигуры-куб-прямоугольный-параллелепипед-нахождение-объема-прямоугольного-параллелепипеда'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'зависимость-между-величинами-при-решении-задач',
       'Зависимость между величинами при решении задач',
       'Зависимость между величинами при решении задач'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'геометрические-фигуры'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'зависимость-между-величинами-при-решении-задач'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'площадь-комбинированных-фигур',
       'Площадь комбинированных фигур',
       'Площадь комбинированных фигур'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 4
where root.code = 'геометрические-фигуры'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'площадь-комбинированных-фигур'
  );
