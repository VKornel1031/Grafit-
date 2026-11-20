# Grafit – kiadás és aláírás

A Grafit első éles verziója **1.0.0**, `versionCode = 9`, címkéje `v1.0.0`. A csomagazonosító `hu.grafit`, a minimális Android-verzió 8.0 (API 26).

## Állandó kiadási kulcs

A privát kulcs a forráskódon kívül készült. A kiadási csomagok és a nyilvános tanúsítvány megoszthatók; a privát kulcs és jelszava bizalmas.

A tanúsítvány SHA-256 ujjlenyomata:

```
aab2c2a885e1411941c536306af963580325c8f048e503b66d31b1838743bdd7
```

A nyilvános tanúsítvány: [release-signing-certificate.pem](release-signing-certificate.pem). A gépi ellenőrzéshez használt ujjlenyomat: [release-signing.sha256](release-signing.sha256).

A tulajdonos privát kulcsmentésében található a `grafit-release.p12` kulcstár és a `keystore-password.txt` jelszófájl; az alias `grafit-release`. Őrizd meg biztonságosan, a felhőkörnyezeten kívül is. Ezeket a fájlokat ne csatold GitHub Release-hez és ne add Git-verziókezeléshez. Minden későbbi kiadás ezt a kulcsot használja.

Az aktuális kompatibilitási build az **1.0.2**, `versionCode = 11`, címkéje `v1.0.2`. Kód- és erőforrás-zsugorítást nem használ; a funkciók változatlanok. A további példák ezt készítik elő.

## Helyi, ellenőrzött kiadás

JDK 21, Android SDK 35 / Build Tools 35.0.0, Python 3 és Git szükséges. A munkafának tisztának kell lennie. A titkos értékeket a folyamat környezeti változóiból olvassa a build:

- `GRAFIT_KEYSTORE_PATH`: a privát `.p12` fájl abszolút útvonala.
- `GRAFIT_STORE_PASSWORD`: a kulcstár jelszava.
- `GRAFIT_KEY_ALIAS`: `grafit-release`.
- `GRAFIT_KEY_PASSWORD`: a kulcs jelszava; a létrehozott PKCS12 kulcsnál megegyezik a kulcstáréval.

A jelszavakat helyi, nem verziókezelt környezeti fájlból töltsd be. Ezután:

```bash
GRAFIT_RELEASE_TAG=v1.0.2 bash scripts/build-release.sh
```

A script az aktuális release-beállításokkal aláírt APK-t és AAB-t fordít, futtatja a JVM-teszteket és a release lintet, és lefordítja az eszközteszteket. Ellenőrzi az APK aláírását, csomagnevét, minimális jogosultságlistáját, kiadási jelzőit, igazítását és az induláshoz szükséges konstruktort. A `dist/` könyvtárba csak nyilvános fájlok kerülnek:

- `grafit-1.0.2.apk`
- `SHA256SUMS.txt`
- `release-manifest.json` a verzióval, Git-committal és nyilvános aláírási ujjlenyomattal.

A `GRAFIT_RELEASE_OUTPUT` változóval más célmappa választható. Zsugorítás használatakor az R8-térkép, valamint a lintjelentés az `app/build/` alatt marad.

## GitHub Actions beállítása

A privát kulcsmentést a saját gépeden bontsd ki, jelentkezz be a GitHub CLI-ba, és futtasd:

```bash
bash scripts/configure-release-secrets.sh /sajat/biztonsagos/kulcsmentes
```

A script a GitHub CLI titkos tárolójába feltölti a `GRAFIT_KEYSTORE_BASE64`, `GRAFIT_STORE_PASSWORD`, `GRAFIT_KEY_ALIAS`, `GRAFIT_KEY_PASSWORD` repository secret értékeket. A parancs nem írja ki a kulcsot vagy a jelszót. A kulcsot sem a workflow, sem a build nem tölti fel nyilvános mellékletként.

A `v1.0.2` Git-címke után a **Actions → Signed release → Run workflow** felületen válaszd a `v1.0.2` címkét, vagy futtasd:

```bash
gh workflow run release.yml --repo VKornel1031/Grafit- -f tag=v1.0.2
```

A workflow ellenőrzi a forráshoz tartozó verziót és az állandó aláírást, majd a megfelelő APK-val, ellenőrzőösszeggel és kiadási jegyzettel létrehozza a stabil GitHub Release-t. Aláírási adatok nélkül nem publikál. A futtató privát kulcsfájlját a végén törli.

Az általános Android CI aláírókulcs nélkül is ellenőrzi a debug buildet, az optimalizált release buildet, a JVM-teszteket és a lintet; az így készült aláíratlan release ellenőrzési eredmény, nem telepítési csomag.

## Kézi közzététel

Az ellenőrzött, aláírt csomag saját gépről is publikálható:

```bash
gh release create v1.0.2 dist/grafit-1.0.2.apk dist/SHA256SUMS.txt dist/release-manifest.json \
  --repo VKornel1031/Grafit- --verify-tag --title 'Grafit 1.0.2' --notes-file releases/1.0.2.md
```

## Következő verzió

Növeld a `versionName` és `versionCode` értékét, adj hozzá `releases/<verzió>.md` kiadási jegyzetet, és készíts az adott commithoz `v<verzió>` címkét. A Signed release workflow ezt a címkét fordítsa. A stabil Release-hez csatolt, azonos kulccsal aláírt APK-t az alkalmazás frissítője felismeri.

## Telepítési ellenőrzés

Az 1.0.0 az új éles kulcs miatt a korábbi debug APK helyett egyszeri újratelepítést igényel. Ez a helyi profil és beállítások elvesztésével jár; az iskolai adatok új bejelentkezéssel visszaszinkronizálhatók. Azonos éles kulccsal aláírt későbbi verziók megőrzik az alkalmazás adatait.

Az eszköztesztek fordítása és a statikus APK-ellenőrzés nem helyettesíti a telefonos ellenőrzést: telepítés, bejelentkezés, szinkron, profil, témák és az Android frissítési megerősítése. A felhasználó kérésére a felhős ellenőrzés nem indít emulátort.
