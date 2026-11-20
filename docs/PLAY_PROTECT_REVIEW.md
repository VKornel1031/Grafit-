# Play Protect-felülvizsgálat

Bejelentett tünet az 1.0.0-nál: **„This app can put your device at risk / Harmful App Blocked”**. A pontos Google-besorolási ok nem áll rendelkezésre. A build- és aláírás-ellenőrzés nem vírusvizsgálat, és nem jelent Play Protect-jóváhagyást.

Az 1.0.1 képességcsökkentő javítás: a saját APK-letöltő és -telepítő kód, az APK-telepítési engedély és az ahhoz tartozó fájlmegosztó komponens eltávolítva. A frissítési értesítés a hivatalos GitHub-oldalt nyitja meg a böngészőben. A csomagnév, az aláírókulcs és az R8-optimalizálás megmaradt.

## Tulajdonosi felülvizsgálat

Ha az új APK is blokkolva van, a készítő kérjen manuális vizsgálatot a [Google Play Protect hivatalos felülvizsgálati űrlapján](https://support.google.com/googleplay/android-developer/contact/protectappeals). A Play Protect maradjon bekapcsolva; a tiltás megkerülése nem része ennek az eljárásnak.

Az alábbiak szükségesek:

- Érintett csomag: `hu.grafit`.
- Bejelentett verzió: 1.0.0, verziókód 9. Vizsgálatra előkészített javítás: 1.0.1, verziókód 10.
- A ténylegesen vizsgálatra küldött APK: `grafit-1.0.1.apk`; SHA-256 értéke a csomag `SHA256SUMS.txt` és `release-manifest.json` fájljában található.
- Az állandó aláíró tanúsítvány nyilvános ujjlenyomata: [release-signing.sha256](release-signing.sha256).
- Forráskód: [Grafit](https://github.com/VKornel1031/Grafit-). Nyilvános APK-hivatkozást csak a megfelelő GitHub Release tényleges közzététele után adj meg.
- A telefon Android-verziója, a tiltás teljes szövege és a tulajdonos által készített képernyőkép, személyes adatok nélkül.

Ne küldd el a Google-űrlapon a privát kiadási kulcsot vagy jelszavát. Az APK és a nyilvános tanúsítvány elegendő az aláíró azonosításához.

## Előkészített angol leírás

> Please manually review the reported Play Protect classification for Grafit, package hu.grafit, an independent Hungarian school journal client. A user reported “This app can put your device at risk / Harmful App Blocked” for version 1.0.0. Version 1.0.1 removes the in-app APK downloader/installer, REQUEST_INSTALL_PACKAGES permission, unknown-source permission flow, and update FileProvider. It checks public GitHub release metadata and opens the official release page in the user's browser. School authentication uses KRÉTA's HTTPS OAuth page. The package and permanent release signing certificate are unchanged. Please assess the attached APK and advise on the reason for the detection. APK SHA-256 and signing-certificate SHA-256 are included in the release manifest.

Az űrlapot és az APK-t a tulajdonosnak kell beküldenie; a projektből nem történt automatikus beküldés vagy Google-jóváhagyás.
