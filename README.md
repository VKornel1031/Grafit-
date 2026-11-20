<div align="center">

# Grafit

**Natív Android KRÉTA-kliens, személyre szabható Material 3 felülettel.**

Órarend, jegyek, hiányzások és iskolai teendők egy helyen.

[![Android build](https://github.com/VKornel1031/Grafit-/actions/workflows/android.yml/badge.svg)](https://github.com/VKornel1031/Grafit-/actions/workflows/android.yml)

[Letöltés](https://github.com/VKornel1031/Grafit-/releases) · [Hibajelentés](https://github.com/VKornel1031/Grafit-/issues) · [Licenc](LICENSE.txt)

</div>

---

## A Grafitról

A Grafit egy független, magyar nyelvű Android-alkalmazás a KRÉTA iskolai adataid kezeléséhez. Kotlin és Jetpack Compose alapokon készül, a Firka navigációs felépítésére és KRÉTA-integrációjára támaszkodva.

A **Grafit 1.0.2** kompatibilitási csomagolást használ a korábbi éles APK telepítési hibájának kivizsgálásához. Kód- és erőforrás-zsugorítás nélkül, tömörítetlen DEX-fájlokkal, hagyományosan kibontott natív könyvtárakkal és V1/V2/V3 aláírással készül. Az alkalmazás összes funkciója és állandó kiadási kulcsa megmarad. A készülék telepítőjének hibakódja nem ismert, ezért a kompatibilitási csomag telefonos ellenőrzése szükséges.

## Funkciók

| Terület | Lehetőségek |
| --- | --- |
| **Kezdőlap** | Mai következő tanóra, óravégi és szabadnapi állapot, választható kezdőlapi elemek |
| **Órarend** | Heti navigáció, napválasztás, terem, tanár és tananyag megtekintése |
| **Jegyek** | Tantárgyi átlagok, lenyitható értékelések, éves átlag és technikumi ösztöndíjbecslés |
| **Hiányzások** | Igazolt és igazolatlan mulasztások, igazolásra váró tételek és késések |
| **Teendők és hírek** | Házi feladatok, bejelentett dolgozatok, faliújság és tanári feljegyzések |
| **Profil** | Egyéni megjelenített név, választható profilkép és animált profilmenü |
| **Megjelenés** | Teljes alkalmazástéma, CSS-változók, saját színek és jegyszínek, betűméret, betűcsalád, kapszula eszköztárak és kártyastílusok |
| **Frissítések** | Stabil GitHub-kiadások ellenőrzése és a hivatalos letöltési oldal megnyitása a böngészőben |
| **Mozgás** | Lapozható fülek, Firka-alapú oldal- és órarendátmenetek, jegyfelfedés, animált kapcsolók és 60 / 120 Hz-es beállítás |
| **Bemutató mód** | A felület kipróbálása kitalált adatokkal, saját KRÉTA-fiók nélkül |

## Kipróbálás és telepítés

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` telepítési hibánál:** az Android egy eltérő kulccsal aláírt korábbi Grafit-csomagot tart nyilván. Az eredeti felhős debug változathoz Android 9-től hitelesített [aláírási kulcsátmeneti APK](docs/SIGNING_MIGRATION.md) készíthető, alkalmazásfunkciók módosítása nélkül.

A stabil kiadások a [GitHub Releases](https://github.com/VKornel1031/Grafit-/releases) oldalon érhetők el a publikálást követően. Az 1.0.0 kiadási jegyzete: [releases/1.0.0.md](releases/1.0.0.md). A korábbi debug változatról az új éles kulcs miatt egyszeri újratelepítés szükséges, amely törli a helyi profilt és beállításokat. A későbbi éles frissítések megőrzik ezeket.

**Android 8.0 vagy újabb rendszer szükséges.** A Material You háttérképhez igazodó színei Android 12-től érhetők el.

1. Nyisd meg az [Android munkafolyamatot](https://github.com/VKornel1031/Grafit-/actions/workflows/android.yml).
2. Válaszd ki a `main` ág legutóbbi sikeres futását.
3. Töltsd le a **grafit-debug-apk** nevű csomagot az *Artifacts* részből, majd bontsd ki.
4. Telepítsd az `app-debug.apk` fájlt a telefonodra. Az Android kérheti az adott telepítési forrás engedélyezését.

Az első indításkor választhatsz a KRÉTA-fiókos bejelentkezés és a mintaadatokkal működő bemutató között. A bejelentkezési oldalon a KRÉTA saját felületén adhatod meg az intézményedet és a fiókadataidat.

A jelenlegi APK fejlesztői aláírással készül. Az éles KRÉTA-bejelentkezés és az intézményenként eltérő válaszok ellenőrzéséhez saját, jogosultan használt fiók szükséges.

## Bejelentkezés és szinkronizálás

A bejelentkezés a KRÉTA saját oldalán, külön natív Android WebView-képernyőn történik. Az oldal a betöltés közben is látható és használható. A képernyő tetején vékony folyamatjelző, visszalépési és újratöltési gomb segíti a navigációt. Lassú betöltés, hálózati hiba vagy hiányzó Android WebView esetén az alkalmazás hibaüzenetet jelenít meg. A HTTPS-alapú külső intézményi azonosítási oldalakra történő átirányítás is támogatott.

A KRÉTA-adatlekérdezések a Firka kliensének működését követik. Automatikus szinkron csak az alkalmazás indításakor és sikeres bejelentkezés után fut. A **Frissítés** gombbal bármikor kézzel indítható újabb frissítés; a háttérből visszatérés és a lapváltás nem indít hálózati adatlekérést.

- A tanulói adatlap és az osztályok, csoportok betöltése után frissülnek a jegyek, házi feladatok, dolgozatok és üzenetek.
- Az órarend legfeljebb 14 napos részletekben töltődik le. Az első teljes szinkron a tanévet is feldolgozza, a későbbi frissítések a gyorsítótár és az ismert tanévvégi esemény alapján szűkíthetők.
- A kézi frissítés az aktuális hetet, a kiválasztott órarendi időszakot és a kezdőlapon megjelenő adatokat is frissíti. Sikertelen teljes szinkron után az újrapróbálás a teljes folyamatot ismétli.
- Az órarendi napválasztás csak a helyben tárolt adatok között navigál; új adatok lekéréséhez használd a Frissítés gombot.
- A faliújság és a tanári feljegyzések frissítéskor, azonosító alapján egyesítve kerülnek a helyi tárba.
- A hiányzások az órarend után frissülnek. A digitális tantárgyak az indítási és kézi szinkron részeként töltődnek le. A kliens az osztálycsoportok átlagainak lekérdezését is tartalmazza.
- A sikeres részfrissítések folyamatosan mentődnek. Hiba esetén az érintett rész korábbi adatai megmaradnak, és az alkalmazás jelzi a sikertelen frissítéseket.

A tokenmegújítás párhuzamos kérések esetén is sorba rendezett. Az átmeneti tokenmegújítási hibák utáni várakozás a Firka 1, 3 és 5 másodperces ütemezését követi.

### Firka-alapú animációk

A natív felület a Firka mozgásainak időzítését és görbéit alkalmazza.

| Művelet | Átmenet |
| --- | --- |
| Alsó fülek közötti váltás | 175 ms-os lapozás, vízszintes ujjmozdulattal is |
| Oldalak megnyitása és bezárása | 300 / 250 ms-os áttűnés és finom függőleges elmozdulás |
| Kezdőlapi adatok megjelenése | 300 ms-os beúszás és áttűnés, meleg gyorsítótár esetén azonnali megjelenítés |
| Órarendi napváltás | Irányhelyes, 300 ms-os vízszintes átmenet |
| Elrejtett jegy felfedése | 400 ms-os átfordulás |
| Beállítási kapcsolók | 150 ms-os szín- és pozícióváltás |

## Tantárgyi átlagok és ösztöndíj

A **Jegyek** oldalon minden elérhető tantárgy mellett a súlyozott évközi átlaga látható. A több forrásból érkező azonos tantárgyak egyetlen sorban szerepelnek; a különböző névváltozatokhoz tartozó jegyek közös átlagba számítanak. A tantárgy megérintésével megnyithatók az egyes értékelések. A jegy nélküli tantárgyak átlaga „—”. Az oldal alján egymás mellett jelenik meg az **Éves átlag** és a **Meghatározott ösztöndíj átlag alapján** kártya. A jegyek elrejtése ezekre az összesítésekre is vonatkozik.

Az éves átlag az értékeléssel rendelkező tantárgyak súlyozott átlagainak számtani átlaga: minden tantárgy azonos súllyal számít. Ez évközi tájékoztatás, nem a bizonyítvány hivatalos átlaga.

A technikumi szakirányú oktatásra megjelenített havi becslés sávjai:

| Két tizedesre kerekített átlag | Havi becsült összeg |
| --- | --- |
| 2,00–2,99 | 8 000 Ft |
| 3,00–3,99 | 25 000 Ft |
| 4,00–4,49 | 42 000 Ft |
| 4,50–5,00 | 59 000 Ft |

A becslés a jelenlegi átlagot használja. A hivatalos ösztöndíj az előző tanév év végi osztályzataitól és a jogosultsági feltételektől függ. Technikumi ágazati alapoktatásban jogosultság esetén havi 8 000 Ft jár, az átlagtól függetlenül. Hiányzó vagy 2,00 alatti átlagnál az alkalmazás nem ad becslést. Forrás: [SZFSZC – Ösztöndíjak](https://szfszc.hu/kepzesek/osztondijak).

## Saját profil és beállítások

Érintsd meg a **jobb felső sarokban lévő profilbuborékot**. A felcsúszó menüben szerkesztheted a megjelenített nevedet, választhatsz vagy eltávolíthatsz profilképet, megnyithatod a beállításokat, illetve kijelentkezhetsz.

- A név a **Név mentése** gombbal menthető. A név és a profilkép mentése a KRÉTA-szinkrontól függetlenül, helyben történik; hálózati frissítés közben is használható.
- A kiválasztott kép középre vágott, legfeljebb 512 × 512 képpontos másolata helyben tárolódik.
- Az egyéni profil minden KRÉTA-fiókhoz külön tartozik. A bemutató mód saját profilt használ.
- A név, a profilkép és a megjelenési beállítások megmaradnak az alkalmazás újraindítása után.
- Kijelentkezéskor a munkamenet és az iskolai gyorsítótár törlődik. A helyi profil ugyanazzal a fiókkal történő következő bejelentkezéskor újra betöltődik.

A beállításokban módosíthatod a témát, a kiemelőszínt, a kártyák lekerekítését és sűrűségét, a jegyek láthatóságát, valamint az alsó navigáció elemeit. Az alkalmazás adatainak törlése vagy eltávolítása a helyben mentett személyre szabást is törli.

### Az egész alkalmazás személyre szabása

A **Beállítások** menüben minden változtatás azonnal megjelenik és helyben mentődik. A korábbi megjelenési beállításokat a frissítés automatikusan átveszi.

- Kapszula alakú felső eszköztár és lebegő alsó navigáció; az alsó feliratok állandó vagy csak kijelöléskori megjelenítése.
- Kitöltött, körvonalas vagy árnyékos kártyák, 0–40 egység közötti lekerekítéssel.
- 85–130%-os alkalmazásbeli betűméret, letisztult, talpas vagy monospace betűcsalád. A rendszer betűméret-beállítása továbbra is érvényes.
- Hét kiemelőpaletta, külön felületi és háttérszínek, egyenként állítható 1–5-ös jegyszínek és tiszta fekete sötét háttér.
- Választható kezdőlapi összesítők, legutóbbi jegyek, hírek és napi órarend; kapcsolható lapváltási rezgés.

Az **Egyéni CSS-téma szerkesztése** a Grafit saját CSS-változóit fordítja natív Compose-témává. Egyetlen `:root` blokk használható, `--grafit-` előtagú változókkal. A támogatott színek `#RGB` vagy `#RRGGBB` alakúak. A `radius` értékében a `px` natív, sűrűségfüggetlen felületi egységet jelent. A szerkesztő a hibás vagy nem támogatott értékeket mentés előtt jelzi. A saját színek a paletta és a Material You színeit felülírják; webes szelektorok és külső stíluslapok nem részei ennek a natív témanyelvnek.

```css
:root {
  --grafit-primary: #6986A8;
  --grafit-background: #17191C;
  --grafit-surface: #1D2024;
  --grafit-card: #25292E;
  --grafit-on-surface: #F1F3F5;
  --grafit-on-surface-variant: #BCC4CE;
  --grafit-grade-1: #F58B88;
  --grafit-grade-5: #9FD29A;
  --grafit-radius: 28px;
  --grafit-font-scale: 1.05;
  --grafit-toolbar: pill;
  --grafit-navigation: pill;
  --grafit-card-style: outlined;
}
```

A `font-family` értékei `sans`, `serif`, `mono`; a `toolbar` és `navigation` értékei `standard`, `pill`; a `card-style` értékei `filled`, `outlined`, `elevated`. A teljes színváltozó-lista az alkalmazás szerkesztőjében és a [ThemeCss.kt](app/src/main/java/hu/grafit/ui/ThemeCss.kt) fájlban olvasható. Az elrejtett jegyek saját színe is rejtve marad a felfedésig.

### Mai órák állapota

A kezdőlap csak az adott budapesti naptári nap tényleges, el nem maradt tanórái közül választ. Az utolsó óra után **Mára nincs több órád.**, tanóra nélküli napon **Ma nincsenek óráid.** jelenik meg. A következő napi órák nem fedik el ezeket az állapotokat. A jelzés a helyben tárolt órarendre támaszkodik, és a helyi óra frissítése nem indít KRÉTA-szinkront.

### 60 vagy 120 Hz

A **Beállítások → Mozgás és képfrissítés** részen választható ki a kívánt frissítési mód. A választás helyben mentődik.

A 120 Hz-es módhoz megfelelő kijelző szükséges. Ha az eszköz aktuális kijelzőmódjai között elérhető a 120 Hz, a Grafit ezt kéri az Androidtól. Egyébként 60 Hz-et kér, és jelzi a korlátozást. A tényleges képfrissítést a rendszer, az energiatakarékosság és a gyártói beállítások is befolyásolhatják.

## Alkalmazásfrissítés GitHubról

Indításkor a Grafit egyszer lekéri a tároló legújabb stabil GitHub-kiadásának adatait. Újabb, APK-t tartalmazó kiadásnál felugró ablakban megjelenik a verzió és a kiadási jegyzet. A **GitHub letöltési oldal** gomb a hivatalos GitHub Release oldalt nyitja meg a böngészőben. Az APK letöltését a böngésző, telepítését az Android kezeli. A Grafit nem kér alkalmazástelepítési engedélyt és nem tölt le APK-t a saját tárhelyére. Kézi keresés a **Beállítások → Alkalmazásfrissítések** részen indítható.

A szükséges alkalmazásjogosultság az internetelérés, valamint az AndroidX belső, signature-szintű fogadóvédelme. A kiadási script a kész APK jogosultságlistáját ellenőrzi. Az Android telepítője az alkalmazás frissítésekor ellenőrzi az aláírás kompatibilitását.

### Play Protect-jelzés

Az 1.0.0 telepítésénél felhasználói visszajelzés szerint „Harmful App Blocked” üzenet jelent meg. Az 1.0.1 eltávolítja a saját APK-letöltő és -telepítő funkciót és annak engedélyét; ez csökkenti az alkalmazás képességeit, de a Google minősítésének okát nem igazolja, és nem garantálja a tiltás megszűnését. Ha a jelzés fennmarad, a készítő a [Google Play Protect felülvizsgálati űrlapján](https://support.google.com/googleplay/android-developer/contact/protectappeals) kérhet ellenőrzést. A [felülvizsgálati útmutató](docs/PLAY_PROTECT_REVIEW.md) felsorolja a szükséges adatokat. A Play Protect maradjon bekapcsolva.

### Kiadás előkészítése

A frissítési csatornához nyilvános, stabil GitHub Release szükséges a `VKornel1031/Grafit-` tárolóban, például `v0.5.1` címkével. Csatolj hozzá egy legfeljebb 200 MiB méretű, aláírt APK-t, például `grafit-0.5.1.apk` néven. Minden kiadásban növeld a `versionCode` értékét. Az APK-t ugyanazzal a tartós kiadási kulccsal írd alá, mint a felhasználóknál telepített változatot. A GitHub Actions letölthető build-eredményei külön csatornát jelentenek; a frissítéskereső a Releases oldal APK-mellékleteit használja.

A projekt a privát kiadási kulcsot nem tárolja a forráskódban. Az 1.0.0-hoz saját, állandó kiadási kulcs készült; az éles csomag ellenőrzése a tárolóban szereplő nyilvános tanúsítványhoz történik. A Signed release workflow és a helyi kiadási script csak a megfelelő kulccsal aláírt APK-t készíti elő publikálásra. A teljes folyamat: [Kiadás és aláírás](docs/RELEASING.md).

## Helyi adattárolás

A bejelentkezés OAuth-kódcserét, S256 PKCE-t és a visszatérési munkamenet ellenőrzését használja. A tokenek, az iskolai gyorsítótár és a személyes profil Android Keystore-kulccsal titkosítva, az alkalmazás saját tárhelyén tárolódnak. A megjelenési beállítások szintén helyben mentődnek. Az automatikus felhőmentés és az eszközök közötti adatátvitel ki van kapcsolva.

Az egyéni név és profilkép a Grafit helyi profiljához tartozik. Az alkalmazás közvetlenül a KRÉTA szolgáltatásaival kommunikál. Hálózati hiba esetén az utoljára sikeresen letöltött iskolai adatok és a frissítés időpontja segítik a tájékozódást.

## Fejlesztési állapot

Az 1.0.0 az első stabil verzió; a további fejlesztés folytatódik. Jelenleg egy KRÉTA-fiók használható egyszerre. A házi feladatok beadása, a csatolmányok kezelése, az e-ügyintézési üzenetek, az értesítések és a több fiók közötti váltás további fejlesztést igényel. A projekt Android-telefonokra készül.

Az 1.0.2 ellenőrzései az alábbi területeket fedik le. Emulátort ebben a változatban nem futtattunk.

| Ellenőrzés | Állapot |
| --- | --- |
| Debug APK, kompatibilitási éles APK és AAB fordítása | Sikeres; az éles APK állandó kiadási kulccsal aláírva |
| Éles csomag ellenőrzése | Aláírás, csomagazonosító, nem hibakereshető build és indulási konstruktor ellenőrizve |
| JVM-tesztek | 65 sikeres teszt, napi óraállapottal, CSS-ellenőrzéssel, beállításmigrációval és frissítési hivatkozások ellenőrzésével |
| Android Lint | 0 hiba, stílusjavaslatokkal |
| Android-eszköztesztek | Teszt-APK fordítása sikeres; eszközön ebben a változatban nem futottak |
| Éles KRÉTA-bejelentkezés és szinkron | A korábbi változat felhasználói visszaigazolása sikeres; az 1.0.2 éles eszközös ellenőrzése még szükséges |

A frissítési tesztek helyi HTTP-kiszolgálóval ellenőrzik a kiadások felismerését, a hivatalos hivatkozásokat és az idegen vagy aláíratlan mellékletek kizárását. A böngészőből indított telepítést és a Play Protect visszajelzését fizikai eszközön kell ellenőrizni. A felhőkörnyezet a GitHub Releases API éles elérését is korlátozza.

A böngészőtesztek helyi HTML-űrlapon ellenőrzik a megjelenítést és a tényleges érintéses interakciót, valamint a PKCE-adatok megőrzését képernyő-újralétrehozáskor és a bejelentkezési kód visszaadását. Az éles KRÉTA oldal elérését a fejlesztői felhőkörnyezet hálózati szabályzata jelenleg korlátozza. A 120 Hz-es működés ellenőrzéséhez megfelelő fizikai kijelző szükséges.

## Fejlesztőknek

### Szükséges eszközök

- Android Studio vagy Android SDK parancssori eszközök
- JDK 17 vagy 21
- Android SDK Platform 35 és Build Tools 35.0.0
- A projektben rögzített Gradle 8.13 wrapper

### Fordítás és ellenőrzés

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Az elkészült APK útvonala `app/build/outputs/apk/debug/app-debug.apk`. Ez fejlesztői kulccsal aláírt tesztváltozat. A `:app:assembleRelease` feladat az aktuális csomagolási beállításokkal release APK-t készít; a négy `GRAFIT_` aláírási változóval éles kulcsot használ, ezek nélkül aláíratlan ellenőrzési csomagot fordít. Publikáláshoz használd a `scripts/build-release.sh` scriptet és a [kiadási útmutatót](docs/RELEASING.md).

Csatlakoztatott telefonra vagy emulátorra telepítéshez futtasd az alábbi parancsot.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Az eszköztesztek fordítása és futtatása külön is indítható.

```bash
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

A JVM-tesztek a PKCE-képzést, a Firka bejelentkezési URL-jét, a visszatérés ellenőrzését, a párhuzamos tokenmegújítást, a teljes tanévi szinkront, a részhibákat, a gyorsítótár egyeztetését, az adatfeldolgozást és a helyi profil formátumát ellenőrzik. Az eszköztesztek a bejelentkezési böngésző megnyitását, a navigációt, a témaváltást, a profil mentését, a képimportot és a fiókok elkülönítését fedik le.

A GitHub Actions minden `main` ágra küldött módosításnál és pull requestnél lefordítja az alkalmazást és az eszközteszteket, futtatja a JVM-teszteket és a lintet, majd letölthetővé teszi az APK-t. A natív böngésző eszköztesztjei külön, csatlakoztatott Android-eszközön indíthatók.

A projekt előkészített felhőkörnyezetében a parancsok előtt futtasd a `source scripts/cloud-env.sh` utasítást. A környezet eszközeinek újratelepítését és ellenőrzését a `bash scripts/cloud-setup.sh` végzi.

## Hibajelentés

Hibát vagy ötletet a [GitHub Issues](https://github.com/VKornel1031/Grafit-/issues) oldalon jelezhetsz. Add meg az alkalmazás és az Android verzióját, az eszköz típusát, valamint a hiba előidézésének lépéseit. A csatolt képernyőképekről és naplókból távolítsd el a személyes és bejelentkezési adatokat.

## Köszönet és licenc

A Grafit KRÉTA-integrációja és navigációs felépítése a **QwIT Development és a Firka közreműködőinek** munkájára épül. A Kotlin-megvalósítás, a Material 3 felület és a helyi profilkezelés a Grafit saját adaptációja.

A projekt **Creative Commons Attribution–NonCommercial–ShareAlike 4.0 International**, azaz **CC BY-NC-SA 4.0** licenc alatt érhető el. A felhasználásra a névfeltüntetés, a nem kereskedelmi célú használat és az azonos licenc alatti továbbadás feltételei vonatkoznak.

A Grafit független alkalmazás. A KRÉTA és E-KRÉTA megnevezések a megfelelő jogosultakhoz tartoznak.

[Firka forráskód](https://github.com/QwIT-Development/firka) · [Forrásmegjelölés](ATTRIBUTION.md) · [Teljes licenc](LICENSE.txt)
