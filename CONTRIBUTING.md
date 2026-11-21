# Közreműködés

Köszönjük, ha hibajelentéssel, ötlettel vagy kóddal segíted a Grafit fejlesztését.

## Hibák és ötletek

Használd a [GitHub Issues](https://github.com/VKornel1031/Grafit-/issues) sablonjait. Hibánál add meg az alkalmazás és az Android verzióját, az eszköz típusát és az előidézés lépéseit. Képernyőképet vagy rövid naplórészletet csak személyes adatok nélkül csatolj. Biztonsági hibához a [SECURITY.md](SECURITY.md) útmutatását kövesd.

## Fejlesztői környezet

JDK 17 vagy 21, Android SDK Platform 35 és Build Tools 35.0.0 szükséges. Android Studióval nyisd meg a projekt gyökerét, vagy használd a projekt Gradle wrapperét. Az SDK helyét az `ANDROID_HOME` változóban vagy egy helyi, nem verziókezelt `local.properties` fájlban adhatod meg.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:lintRelease :app:assembleDebugAndroidTest
```

Aláírási változók nélkül a release fordítása aláíratlan ellenőrzési csomagot készít. A hivatalos kiadási kulcsra fejlesztéshez nincs szükség. Debug változatot a saját környezeted debug kulcsa ír alá; ez nem kompatibilis a hivatalos kiadás aláírásával.

A bemutató módhoz nem kell KRÉTA-fiók. A JVM-hálózati tesztek helyi MockWebServert használnak, és nem igényelnek valódi tanulói adatokat.

Csatlakoztatott Android-eszközzel az eszköztesztek is futtathatók:

```bash
./gradlew :app:connectedDebugAndroidTest
```

A normál CI ezeket a teszteket csak lefordítja, nem indít automatikus emulátort. Ha a változás értesítést, képfeldolgozást vagy készülékspecifikus működést érint, a fizikai eszközös ellenőrzés eredményét is írd le.

## A forrás felépítése

| Könyvtár | Tartalom |
| --- | --- |
| `app/src/main/java/hu/grafit/data` | KRÉTA-protokoll, feldolgozás, helyi tárolás, értesítési szabályok |
| `app/src/main/java/hu/grafit/ui` | Compose-felület, témák és állapotkezelés |
| `app/src/main/java/hu/grafit/notifications` | Android-értesítések, háttérfeladat és tanórai visszaszámlálás |
| `app/src/test` | JVM-tesztek |
| `app/src/androidTest` | Android-eszköztesztek |
| `scripts` | Környezet-előkészítés és ellenőrzött kiadás |
| `docs`, `releases` | Útmutatók és kiadási jegyzetek |

## Pull request

Tartsd a módosítást egy jól körülírható cél körül. Írd le a problémát, a javítás utáni viselkedést és az elvégzett ellenőrzéseket. Viselkedési hibajavításhoz adj célzott regressziós tesztet, ahol annak van értelme. A felület magyar szövegeit és a megváltozott használati útmutatót is frissítsd.

Ne adj a forráshoz valódi tanulói adatot, tokeneket, jelszót, privát kulcsot, teljes hibajelentést vagy telepítőcsomagot. Az APK-k a kiadások mellékletei közé tartoznak. Új függőségnél ellenőrizd a licencet, és az átvett munkát tüntesd fel az [ATTRIBUTION.md](ATTRIBUTION.md) fájlban.

A közreműködés a projekt meglévő [CC BY-NC-SA 4.0](LICENSE.txt) licence alatt történik. A hivatalos verziószámok és aláírt kiadások kezelését hagyd a karbantartóra.
