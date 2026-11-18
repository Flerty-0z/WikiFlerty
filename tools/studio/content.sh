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
cp tools/studio/scenarios/*.json $A/scenarios/
ls -la $A/library $A/files $A/scenarios
