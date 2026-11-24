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
ls -la $A/library $A/files $A/scenarios
