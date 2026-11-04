-- Пилот истории относится к 6 классу. Его нельзя подменять 5 классом только
-- потому, что исходный демонстрационный набор содержал классы 1–5.
insert into grades (grade_no, title_ru, title_kk)
select 6, '6 класс', '6 сынып'
where not exists (select 1 from grades where grade_no = 6);
