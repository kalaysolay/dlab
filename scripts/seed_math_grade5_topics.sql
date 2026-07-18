-- Наполнение topics: математика, 5 класс (экспорт из локальной БД).
-- Идемпотентно: повторный запуск безопасен (not exists).
--
-- Запуск (пример):
--   psql "host=... port=5432 dbname=damulab user=... sslmode=require" -v ON_ERROR_STOP=1 -f scripts/seed_math_grade5_topics.sql
--
-- После выполнения проверка:
--   select count(*) from topics t
--   join subjects s on s.id = t.subject_id
--   join grades g on g.id = t.grade_id
--   where s.code = 'math' and g.grade_no = 5;
--   -- ожидается 84

-- ========== Корневые темы ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'обыкновенные-дроби-продолжение',
       'Обыкновенные дроби (продолжение)',
       'Обыкновенные дроби (продолжение)'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'обыкновенные-дроби-продолжение'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'десятичные-дроби-действия-над-десятичными-дробями',
       'Десятичные дроби. Действия над десятичными дробями',
       'Десятичные дроби. Действия над десятичными дробями'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'множества',
       'Множества',
       'Множества'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'множества'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'проценты',
       'Проценты',
       'Проценты'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'проценты'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'углы-многоугольники',
       'Углы. Многоугольники',
       'Углы. Многоугольники'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'углы-многоугольники'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'диаграммы',
       'Диаграммы',
       'Диаграммы'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'диаграммы'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'развертки-пространственных-фигур',
       'Развертки пространственных фигур',
       'Развертки пространственных фигур'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'развертки-пространственных-фигур'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'натуральные-числа-и-нуль',
       'Натуральные числа и нуль',
       'Натуральные числа и нуль'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'натуральные-числа-и-нуль'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'делимость-натуральных-чисел',
       'Делимость натуральных чисел',
       'Делимость натуральных чисел'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'делимость-натуральных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select s.id, g.id, null,
       'обыкновенные-дроби-и-действия-над-ними',
       'Обыкновенные дроби и действия над ними',
       'Обыкновенные дроби и действия над ними'
from subjects s
join grades g on g.grade_no = 5
where s.code = 'math'
  and not exists (
      select 1 from topics t
      where t.subject_id = s.id and t.grade_id = g.id
        and t.parent_id is null
        and t.code = 'обыкновенные-дроби-и-действия-над-ними'
  );

-- ========== Дочерние: обыкновенные-дроби-продолжение ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'нахождение-дроби-от-числа-нахождение-числа-по-его-дроби',
       'Нахождение дроби от числа. Нахождение числа по его дроби',
       'Нахождение дроби от числа. Нахождение числа по его дроби'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-продолжение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'нахождение-дроби-от-числа-нахождение-числа-по-его-дроби'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-совместную-работу',
       'Задачи на совместную работу',
       'Задачи на совместную работу'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-продолжение'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-совместную-работу'
  );

-- ========== Дочерние: десятичные-дроби-действия-над-десятичными-дробями ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'десятичная-дробь-чтение-и-запись-десятичных-дробей',
       'Десятичная дробь. Чтение и запись десятичных дробей',
       'Десятичная дробь. Чтение и запись десятичных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'десятичная-дробь-чтение-и-запись-десятичных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'перевод-десятичной-дроби-в-обыкновенную-обыкновенной-дроби-в-десятичную',
       'Перевод десятичной дроби в обыкновенную, обыкновенной дроби в десятичную',
       'Перевод десятичной дроби в обыкновенную, обыкновенной дроби в десятичную'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'перевод-десятичной-дроби-в-обыкновенную-обыкновенной-дроби-в-десятичную'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'изображение-десятичной-дроби-на-координатном-луче-сравнение-десятичных-дробей',
       'Изображение десятичной дроби на координатном луче. Сравнение десятичных дробей',
       'Изображение десятичной дроби на координатном луче. Сравнение десятичных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'изображение-десятичной-дроби-на-координатном-луче-сравнение-десятичных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сложение-и-вычитание-десятичных-дробей',
       'Сложение и вычитание десятичных дробей',
       'Сложение и вычитание десятичных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сложение-и-вычитание-десятичных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-десятичной-дроби-на-натуральное-число',
       'Умножение десятичной дроби на натуральное число',
       'Умножение десятичной дроби на натуральное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-десятичной-дроби-на-натуральное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-десятичных-дробей',
       'Умножение десятичных дробей',
       'Умножение десятичных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-десятичных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'деление-десятичной-дроби-на-натуральное-число',
       'Деление десятичной дроби на натуральное число',
       'Деление десятичной дроби на натуральное число'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'деление-десятичной-дроби-на-натуральное-число'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'деление-десятичной-дроби-на-десятичную-дробь',
       'Деление десятичной дроби на десятичную дробь',
       'Деление десятичной дроби на десятичную дробь'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'деление-десятичной-дроби-на-десятичную-дробь'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-и-деление-десятичной-дроби-на-10-100-1000-и-на-0-1-0-01-0-001',
       'Умножение и деление десятичной дроби на 10, 100, 1000, … и на 0,1; 0,01; 0,001; …',
       'Умножение и деление десятичной дроби на 10, 100, 1000, … и на 0,1; 0,01; 0,001; …'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-и-деление-десятичной-дроби-на-10-100-1000-и-на-0-1-0-01-0-001'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'арифметические-действия-над-обыкновенными-и-десятичными-дробями-упражнения-для-повторения-главы-iv',
       'Арифметические действия над обыкновенными и десятичными дробями. Упражнения для повторения главы IV',
       'Арифметические действия над обыкновенными и десятичными дробями. Упражнения для повторения главы IV'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'арифметические-действия-над-обыкновенными-и-десятичными-дробями-упражнения-для-повторения-главы-iv'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'округление-десятичных-дробей',
       'Округление десятичных дробей',
       'Округление десятичных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'округление-десятичных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'числовые-последовательности-составленные-из-дробей',
       'Числовые последовательности, составленные из дробей',
       'Числовые последовательности, составленные из дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'десятичные-дроби-действия-над-десятичными-дробями'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'числовые-последовательности-составленные-из-дробей'
  );

-- ========== Дочерние: множества ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'множество-элементы-множества-изображения-множеств',
       'Множество. Элементы множества. Изображения множеств',
       'Множество. Элементы множества. Изображения множеств'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'множества'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'множество-элементы-множества-изображения-множеств'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'подмножество',
       'Подмножество',
       'Подмножество'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'множества'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'подмножество'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'пересечение-множеств-объединение-множеств',
       'Пересечение множеств. Объединение множеств',
       'Пересечение множеств. Объединение множеств'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'множества'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'пересечение-множеств-объединение-множеств'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'задачи-на-множества',
       'Задачи на множества',
       'Задачи на множества'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'множества'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'задачи-на-множества'
  );

-- ========== Дочерние: проценты ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'нахождение-процентов-от-данного-числа',
       'Нахождение процентов от данного числа',
       'Нахождение процентов от данного числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'проценты'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'нахождение-процентов-от-данного-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'нахождение-числа-по-его-процентам',
       'Нахождение числа по его процентам',
       'Нахождение числа по его процентам'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'проценты'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'нахождение-числа-по-его-процентам'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'упражнения-для-повторения-главы-vi-задачи-на-проценты',
       'Упражнения для повторения главы VI. Задачи на проценты',
       'Упражнения для повторения главы VI. Задачи на проценты'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'проценты'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'упражнения-для-повторения-главы-vi-задачи-на-проценты'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'проценты',
       'Проценты',
       'Проценты'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'проценты'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'проценты'
  );

-- ========== Дочерние: углы-многоугольники ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'угол-градусная-мера-угла',
       'Угол. Градусная мера угла',
       'Угол. Градусная мера угла'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'углы-многоугольники'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'угол-градусная-мера-угла'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'транспортир-измерение-и-построение-углов',
       'Транспортир. Измерение и построение углов',
       'Транспортир. Измерение и построение углов'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'углы-многоугольники'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'транспортир-измерение-и-построение-углов'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сравнение-углов-виды-углов-чертежный-треугольник',
       'Сравнение углов. Виды углов. Чертежный треугольник',
       'Сравнение углов. Виды углов. Чертежный треугольник'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'углы-многоугольники'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сравнение-углов-виды-углов-чертежный-треугольник'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'многоугольники',
       'Многоугольники',
       'Многоугольники'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'углы-многоугольники'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'многоугольники'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'упражнения-для-повторения-главы-vii',
       'Упражнения для повторения главы VII',
       'Упражнения для повторения главы VII'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'углы-многоугольники'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'упражнения-для-повторения-главы-vii'
  );

-- ========== Дочерние: диаграммы ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'окружность-круг',
       'Окружность. Круг',
       'Окружность. Круг'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'диаграммы'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'окружность-круг'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'круговой-сектор',
       'Круговой сектор',
       'Круговой сектор'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'диаграммы'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'круговой-сектор'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'способы-представления-статистических-данных-столбчатые-линейные-круговые-и-графические-диаграммы-таблицы',
       'Способы представления статистических данных. Столбчатые, линейные, круговые и графические диаграммы. Таблицы',
       'Способы представления статистических данных. Столбчатые, линейные, круговые и графические диаграммы. Таблицы'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'диаграммы'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'способы-представления-статистических-данных-столбчатые-линейные-круговые-и-графические-диаграммы-таблицы'
  );

-- ========== Дочерние: развертки-пространственных-фигур ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'прямоугольный-параллелепипед-и-его-развертка',
       'Прямоугольный параллелепипед и его развертка',
       'Прямоугольный параллелепипед и его развертка'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'развертки-пространственных-фигур'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'прямоугольный-параллелепипед-и-его-развертка'
  );

-- ========== Дочерние: натуральные-числа-и-нуль ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'натуральные-числа-и-нуль',
       'Натуральные числа и нуль',
       'Натуральные числа и нуль'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'натуральные-числа-и-нуль'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'координатный-луч-изображение-натуральных-чисел-и-числа-нуль-на-координатном-луче',
       'Координатный луч. Изображение натуральных чисел и числа нуль на координатном луче',
       'Координатный луч. Изображение натуральных чисел и числа нуль на координатном луче'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'координатный-луч-изображение-натуральных-чисел-и-числа-нуль-на-координатном-луче'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сравнение-натуральных-чисел-двойное-неравенство',
       'Сравнение натуральных чисел. Двойное неравенство',
       'Сравнение натуральных чисел. Двойное неравенство'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сравнение-натуральных-чисел-двойное-неравенство'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'исторические-сведения-о-системах-счисления-и-записи-чисел',
       'Исторические сведения о системах счисления и записи чисел',
       'Исторические сведения о системах счисления и записи чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'исторические-сведения-о-системах-счисления-и-записи-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сложение-и-вычитание-натуральных-чисел',
       'Сложение и вычитание натуральных чисел',
       'Сложение и вычитание натуральных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сложение-и-вычитание-натуральных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-и-деление-натуральных-чисел-основное-свойство-частного',
       'Умножение и деление натуральных чисел. Основное свойство частного',
       'Умножение и деление натуральных чисел. Основное свойство частного'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-и-деление-натуральных-чисел-основное-свойство-частного'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'история-возникновения-арифметических-действий-знаков-равенства-и-неравенства',
       'История возникновения арифметических действий, знаков равенства и неравенства',
       'История возникновения арифметических действий, знаков равенства и неравенства'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'история-возникновения-арифметических-действий-знаков-равенства-и-неравенства'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'свойства-арифметических-действий',
       'Свойства арифметических действий',
       'Свойства арифметических действий'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'свойства-арифметических-действий'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'способ-сложения-гаусса',
       'Способ сложения Гаусса',
       'Способ сложения Гаусса'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'способ-сложения-гаусса'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'арифметические-действия-над-натуральными-числами',
       'Арифметические действия над натуральными числами',
       'Арифметические действия над натуральными числами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'арифметические-действия-над-натуральными-числами'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'числовые-выражения-буквенные-выражения',
       'Числовые выражения. Буквенные выражения',
       'Числовые выражения. Буквенные выражения'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'числовые-выражения-буквенные-выражения'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'упрощение-выражений',
       'Упрощение выражений',
       'Упрощение выражений'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'упрощение-выражений'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'уравнение',
       'Уравнение',
       'Уравнение'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'уравнение'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'формулы-вычисление-по-формулам',
       'Формулы. Вычисление по формулам',
       'Формулы. Вычисление по формулам'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'формулы-вычисление-по-формулам'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'числовые-последовательности',
       'Числовые последовательности',
       'Числовые последовательности'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'числовые-последовательности'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'упражнения-для-повторения-главы-i',
       'Упражнения для повторения главы I',
       'Упражнения для повторения главы I'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'натуральные-числа-и-нуль'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'упражнения-для-повторения-главы-i'
  );

-- ========== Дочерние: делимость-натуральных-чисел ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'делители-натурального-числа-кратные-натурального-числа',
       'Делители натурального числа. Кратные натурального числа',
       'Делители натурального числа. Кратные натурального числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'делители-натурального-числа-кратные-натурального-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'нахождение-натурального-корня-уравнения-способом-перебора-пар-делителей-числа',
       'Нахождение натурального корня уравнения способом перебора пар делителей числа',
       'Нахождение натурального корня уравнения способом перебора пар делителей числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'нахождение-натурального-корня-уравнения-способом-перебора-пар-делителей-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'простые-числа-составные-числа',
       'Простые числа. Составные числа',
       'Простые числа. Составные числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'простые-числа-составные-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'решето-эратосфена',
       'Решето Эратосфена',
       'Решето Эратосфена'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'решето-эратосфена'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'основные-свойства-делимости',
       'Основные свойства делимости',
       'Основные свойства делимости'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'основные-свойства-делимости'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'признаки-делимости-натуральных-чисел-на-2-5-и-10',
       'Признаки делимости натуральных чисел на 2, 5 и 10',
       'Признаки делимости натуральных чисел на 2, 5 и 10'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'признаки-делимости-натуральных-чисел-на-2-5-и-10'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'признаки-делимости-натуральных-чисел-на-3-и-на-9',
       'Признаки делимости натуральных чисел на 3 и на 9',
       'Признаки делимости натуральных чисел на 3 и на 9'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'признаки-делимости-натуральных-чисел-на-3-и-на-9'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'степень-числа',
       'Степень числа',
       'Степень числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'степень-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'разложение-составных-чисел-на-простые-множители',
       'Разложение составных чисел на простые множители',
       'Разложение составных чисел на простые множители'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'разложение-составных-чисел-на-простые-множители'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'наибольший-общий-делитель-взаимно-простые-числа',
       'Наибольший общий делитель. Взаимно простые числа',
       'Наибольший общий делитель. Взаимно простые числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'наибольший-общий-делитель-взаимно-простые-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'наименьшее-общее-кратное',
       'Наименьшее общее кратное',
       'Наименьшее общее кратное'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'наименьшее-общее-кратное'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'упражнения-для-повторения-главы-ii',
       'Упражнения для повторения главы II',
       'Упражнения для повторения главы II'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'упражнения-для-повторения-главы-ii'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'движение-по-реке',
       'Движение по реке',
       'Движение по реке'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'делимость-натуральных-чисел'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'движение-по-реке'
  );

-- ========== Дочерние: обыкновенные-дроби-и-действия-над-ними ==========

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'обыкновенная-дробь-чтение-и-запись-обыкновенных-дробей',
       'Обыкновенная дробь. Чтение и запись обыкновенных дробей',
       'Обыкновенная дробь. Чтение и запись обыкновенных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'обыкновенная-дробь-чтение-и-запись-обыкновенных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'основное-свойство-дроби-сокращение-дробей',
       'Основное свойство дроби. Сокращение дробей',
       'Основное свойство дроби. Сокращение дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'основное-свойство-дроби-сокращение-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'правильные-дроби-неправильные-дроби',
       'Правильные дроби. Неправильные дроби',
       'Правильные дроби. Неправильные дроби'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'правильные-дроби-неправильные-дроби'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'смешанные-числа',
       'Смешанные числа',
       'Смешанные числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'смешанные-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'изображение-обыкновенных-дробей-и-смешанных-чисел-на-координатном-луче',
       'Изображение обыкновенных дробей и смешанных чисел на координатном луче',
       'Изображение обыкновенных дробей и смешанных чисел на координатном луче'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'изображение-обыкновенных-дробей-и-смешанных-чисел-на-координатном-луче'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'приведение-обыкновенных-дробей-и-смешанных-чисел-к-наименьшему-общему-знаменателю',
       'Приведение обыкновенных дробей и смешанных чисел к наименьшему общему знаменателю',
       'Приведение обыкновенных дробей и смешанных чисел к наименьшему общему знаменателю'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'приведение-обыкновенных-дробей-и-смешанных-чисел-к-наименьшему-общему-знаменателю'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сравнение-обыкновенных-дробей-сравнение-смешанных-чисел',
       'Сравнение обыкновенных дробей. Сравнение смешанных чисел',
       'Сравнение обыкновенных дробей. Сравнение смешанных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сравнение-обыкновенных-дробей-сравнение-смешанных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сложение-и-вычитание-обыкновенных-дробей',
       'Сложение и вычитание обыкновенных дробей',
       'Сложение и вычитание обыкновенных дробей'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сложение-и-вычитание-обыкновенных-дробей'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'сложение-и-вычитание-смешанных-чисел',
       'Сложение и вычитание смешанных чисел',
       'Сложение и вычитание смешанных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'сложение-и-вычитание-смешанных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'аликвотные-дроби',
       'Аликвотные дроби',
       'Аликвотные дроби'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'аликвотные-дроби'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'умножение-обыкновенных-дробей-и-смешанных-чисел',
       'Умножение обыкновенных дробей и смешанных чисел',
       'Умножение обыкновенных дробей и смешанных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'умножение-обыкновенных-дробей-и-смешанных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'взаимно-обратные-числа',
       'Взаимно обратные числа',
       'Взаимно обратные числа'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'взаимно-обратные-числа'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'деление-обыкновенных-дробей-и-смешанных-чисел',
       'Деление обыкновенных дробей и смешанных чисел',
       'Деление обыкновенных дробей и смешанных чисел'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'деление-обыкновенных-дробей-и-смешанных-чисел'
  );

insert into topics (subject_id, grade_id, parent_id, code, title_ru, title_kk)
select root.subject_id, root.grade_id, root.id,
       'арифметические-действия-над-обыкновенными-дробями-и-смешанными-числами',
       'Арифметические действия над обыкновенными дробями и смешанными числами',
       'Арифметические действия над обыкновенными дробями и смешанными числами'
from topics root
join subjects s on s.id = root.subject_id and s.code = 'math'
join grades g on g.id = root.grade_id and g.grade_no = 5
where root.code = 'обыкновенные-дроби-и-действия-над-ними'
  and root.parent_id is null
  and not exists (
      select 1 from topics t
      where t.subject_id = root.subject_id and t.grade_id = root.grade_id
        and t.parent_id = root.id
        and t.code = 'арифметические-действия-над-обыкновенными-дробями-и-смешанными-числами'
  );
