# Áttérés az eredeti debug kulcsról az éles kulcsra

Az eszközből kapott hiba pontos oka:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE:
Existing package hu.grafit signatures do not match newer version
```

Ez aláírási ütközés: az Android egy korábbi `hu.grafit` csomagot még nyilvántart. Ez akkor is előfordulhat, ha a csomag a személyes profil alkalmazáslistájából már eltűnt, de más profilban, archiválva vagy megtartott adatokkal megmaradt. A csomagolási beállítások módosítása önmagában nem oldja fel a különböző kulcsok közti ütközést.

## Aláírási kulcsátmenet Android 9-től

Az eredeti felhőkörnyezet debug privát kulcsa és az állandó éles kulcs is rendelkezésre állt. Android APK Signature Scheme V3 formátumú, mindkét fél által hitelesített aláírási történet készült:

- Korábbi tanúsítvány SHA-256: `85d10f526e36f3ff08217e65c16b3777803acd8834f2751b9109b20f50432595`.
- Új éles tanúsítvány SHA-256: `aab2c2a885e1411941c536306af963580325c8f048e503b66d31b1838743bdd7`.
- Megtartott adatok átadása engedélyezett; az éles kulcsról a régi kulcsra történő visszalépés nem engedélyezett.

A [debug-to-release.lineage](debug-to-release.lineage) nyilvános tanúsítványokat és kriptográfiai bizonyítékot tartalmaz. Nem tartalmaz privát kulcsot vagy jelszót.

Az egyszeri `grafit-1.0.2-key-migration.apk` Android 9 vagy újabb rendszeren az eredeti debug aláírástól az éles aláírásig igazolja az átmenetet. A telefonon még meglévő adatok megtarthatók; a korábban törölt adatok nem állíthatók vissza. A funkciók változatlanok. Android 8-ra továbbra is a normál éles APK készül, amelyhez a régi debug változatot teljesen el kell távolítani.

## Előkészítés a tulajdonos saját gépén

A négy `GRAFIT_` kiadási aláírási változó és `ANDROID_HOME` beállítása után:

```bash
bash scripts/build-signing-migration.sh
```

A script külön buildet készít a `-PgrafitSigningMigration=true` kapcsolóval, minimum API 28-ra, majd a nyilvános aláírási történetet beillesztve kizárólag az éles kulccsal, V3 séma szerint aláírja. Ellenőrzi az éles tanúsítványt, a régi kulcs adatátadási jogosultságát és az APK alkalmazásadatainak változatlanságát az újraaláírás során. A `dist/` alá APK, ellenőrzőösszeg és `signing-migration.json` kerül. A `GRAFIT_MIGRATION_OUTPUT` változóval más célmappa választható.

Az eredeti debug privát kulcsra az átmenet bizonyítékának egyszeri elkészítéséhez volt szükség. A további reprodukáláshoz az éles privát kulcs és a nyilvános lineage fájl elegendő. A normál éles kiadási workflow továbbra is a megszokott kiadási kulcsot használja.

## Fontos korlát

Az átmenet csak a fent megadott eredeti debug tanúsítványhoz készülhetett el. Ha a telefonon más kulccsal – például egy másik CI-futtatás debug kulcsával – aláírt változat maradt, az Android ezt az átmenetet helyesen elutasítja. Ilyenkor a megfelelő régi kulcs vagy a korábbi csomag teljes, minden érintett profilra kiterjedő eltávolítása szükséges. A Play Protect maradjon bekapcsolva; ez a fájl az Android szabványos, hitelesített kulcsátmeneti mechanizmusát használja.
