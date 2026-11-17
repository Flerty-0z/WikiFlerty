rec_start explore 170
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
tap "Позже" 90; snap update
tap "Пропустить" 40; snap library_empty
tap "Найти на телефоне" 40; sleep 3; snap scan_sheet
tap "Выбрать папку" 30; sleep 6; snap picker
tap "Показать корни|Show roots" 15; sleep 3; snap roots
tap "sdk_gphone|Android SDK|Внутренняя|Internal|SDK" 15; sleep 4; snap storage
tap "Books" 20; sleep 4; snap books_folder
tap "Использовать эту папку|Use this folder|ИСПОЛЬЗОВАТЬ" 20; sleep 3; snap use
tap "Разрешить|Allow|РАЗРЕШИТЬ" 20; sleep 8; snap scanned
tap "Добавить" 30; sleep 25; snap added
rec_stop explore
rec_start browse 170
swipe 360 1200 360 500 2500; sleep 6; snap scrolled
swipe 360 500 360 1200 2500; sleep 6
adb shell input tap 180 700; sleep 12; snap tapped_book
adb shell input keyevent KEYCODE_BACK; sleep 6
tap "Словарь" 20; sleep 6; snap dictionary
tap "Заметки" 20; sleep 6; snap notes
tap "Итоги" 20; sleep 6; snap stats
tap "Полка" 20; sleep 6; snap shelf
rec_stop browse
