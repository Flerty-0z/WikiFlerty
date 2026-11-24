#!/usr/bin/env bash
set -e
REPO="$1"
mkdir -p app/src/main/assets/fonts
grep -v '^#' app/fonts.txt | while read -r name path; do
  [ -z "$name" ] && continue
  url="https://github.com/google/fonts/raw/main/$(echo "$path" | sed 's/\[/%5B/g; s/\]/%5D/g')"
  curl -fsSL --retry 3 -o "app/src/main/assets/fonts/$name" "$url" || rm -f "app/src/main/assets/fonts/$name"
done

pip install --quiet pillow
mkdir -p fonts
curl -fsSL --retry 3 -o fonts/Oswald.ttf "https://github.com/google/fonts/raw/main/ofl/oswald/Oswald%5Bwght%5D.ttf"
curl -fsSL --retry 3 -o fonts/Playfair.ttf "https://github.com/google/fonts/raw/main/ofl/playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf"
curl -fsSL --retry 3 -o fonts/Lobster-Regular.ttf "https://github.com/google/fonts/raw/main/ofl/lobster/Lobster-Regular.ttf"
A=app/src/studio/assets/studio
mkdir -p $A/library $A/files $A/scenarios
id=$(gh api "repos/$REPO/releases?per_page=100" -q '[.[] | select(.tag_name=="demo-library")][0].assets[0].id' || true)
if [ -n "$id" ]; then
  gh api -H "Accept: application/octet-stream" "repos/$REPO/releases/assets/$id" > library.zip && unzip -oq library.zip -d $A/library
else
  python3 tools/record/library.py $A/library fonts
fi
fid=$(gh api "repos/$REPO/releases?per_page=100" -q '[.[] | select(.tag_name=="studio-files")][0].assets[0].id' || true)
if [ -n "$fid" ]; then
  gh api -H "Accept: application/octet-stream" "repos/$REPO/releases/assets/$fid" > files.zip && unzip -oq files.zip -d $A/files
fi
if [ "$(ls $A/files | wc -l)" -lt 5 ]; then
  rm -rf $A/files && mkdir -p $A/files
  mkdir -p shelf && cp $A/library/*.epub shelf/
  python3 tools/studio/makefiles.py $A/files fonts shelf
  if [ "$(ls $A/files | wc -l)" -ge 5 ]; then
    (cd $A/files && zip -q ../../../../../../files.zip *)
    gh release delete studio-files --cleanup-tag -y --repo "$REPO" 2>/dev/null || true
    gh release create studio-files files.zip --draft --title studio-files --notes "" --repo "$REPO"
  fi
fi
rm -f $A/library/03.epub $A/library/06.epub $A/library/08.epub
python3 tools/studio/litres_like.py $A/library/01.epub $A/library/11.epub || true
cp tools/studio/scenarios/*.json $A/scenarios/
mkdir -p $A/audio
if command -v espeak-ng >/dev/null 2>&1 || sudo apt-get install -y -qq espeak-ng ffmpeg >/dev/null 2>&1; then
  i=1
  for t in "Глава первая. Мой дядя самых честных правил, когда не в шутку занемог, он уважать себя заставил и лучше выдумать не мог." "Глава вторая. Деревня, где скучал Евгений, была прелестный уголок. Там друг невинных наслаждений благословить бы небо мог." "Глава третья. Она была девушка, она была влюблена. Пора пришла, она влюбилась. Так в землю падшее зерно весны огнём оживлено."; do
    espeak-ng -v ru -s 150 -w a.wav "$t $t $t" && ffmpeg -v error -y -i a.wav -metadata album="Тестовая аудиокнига" -metadata artist="WikiFlerty" -metadata title="Глава $i" -b:a 64k "$A/audio/0$i.mp3" || true
    i=$((i+1))
  done
  rm -f a.wav
fi
ls -la $A/audio || true
mkdir -p $A/audio2
if curl -fsSL -o probe.wav https://huggingface.co/alphacep/vosk-model-small-ru/resolve/main/test.wav; then
  ffmpeg -v error -y -i probe.wav -metadata album="Золото (отрывок)" -metadata artist="Мамин-Сибиряк" -metadata title="Отрывок" -b:a 64k "$A/audio2/01.mp3" || true
  rm -f probe.wav
fi
python3 - "$A/library/90.epub" <<'PY'
import sys, zipfile
t = ("<p>Глава первая.</p><p>В шахте было темно и сыро. Старик долго молчал и смотрел на огонёк лампы.</p>"
     "<p>Родион Потапыч высчитывал каждый новый вершок углубления и давно определил про себя, сколько ещё осталось работы.</p>"
     "<p>Рабочие переглядывались и ждали, что скажет старик.</p>")
x = f'<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ru"><head><title>Золото</title></head><body>{t}</body></html>'
opf = ('<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
       '<dc:identifier id="id">wf-zoloto</dc:identifier><dc:title>Золото (отрывок)</dc:title><dc:creator>Мамин-Сибиряк</dc:creator><dc:language>ru</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata>'
       '<manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c"/></spine></package>')
nav = '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>nav</title></head><body><nav epub:type="toc"><ol><li><a href="c.xhtml">Глава первая</a></li></ol></nav></body></html>'
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip")
    z.writestr("META-INF/container.xml", '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="o.opf" media-type="application/oebps-package+xml"/></rootfiles></container>', zipfile.ZIP_DEFLATED)
    z.writestr("o.opf", opf, zipfile.ZIP_DEFLATED); z.writestr("c.xhtml", x, zipfile.ZIP_DEFLATED); z.writestr("nav.xhtml", nav, zipfile.ZIP_DEFLATED)
PY
ls -la $A/audio2 $A/library/90.epub || true
ls -la $A/library $A/files $A/scenarios
