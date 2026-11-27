
alter table public.skins
  drop constraint if exists skins_skin_key_check;

alter table public.skins
  add constraint skins_skin_key_check
  check (char_length(skin_key) between 1 and 255 and length(btrim(skin_key)) > 0);
