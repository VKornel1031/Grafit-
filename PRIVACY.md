# Adatkezelési tájékoztató

A 2.0.0-s alkalmazásverzióhoz frissítve.

Ez a dokumentum a Grafit jelenlegi Android-alkalmazásának adatkezelését írja le. A Grafit független kliens, nem a KRÉTA üzemeltetőjének alkalmazása.

## Bejelentkezés és hálózati kapcsolatok

A bejelentkezés a KRÉTA HTTPS-oldalán, Android WebView-ban történik. Az alkalmazás OAuth-kódcserét és PKCE-t használ. Nem készít saját mentést a KRÉTA-jelszóról; a visszakapott hozzáférési és frissítési tokeneket a munkamenet fenntartására használja. Intézményi azonosításnál az iskola külső bejelentkezési szolgáltatása is megnyílhat.

Az iskolai adatok közvetlenül a KRÉTA szolgáltatásaitól érkeznek. Nincs külön Grafit-fiókrendszer vagy az iskolai adatokat fogadó Grafit-szerver. A KRÉTA és az intézményi azonosítószolgáltató adatkezelésére a saját tájékoztatóik vonatkoznak.

A frissítéskereső indításkor és kézi ellenőrzéskor a GitHub nyilvános kiadási API-ját használja. Nem küld iskolai adatokat vagy KRÉTA-tokent a GitHubnak. A hálózati szolgáltatások a kapcsolat részeként megkapják az IP-címet és a szokásos kérési adatokat. Frissítés letöltésekor a GitHub oldala a böngészőben nyílik meg. [GitHub adatkezelési tájékoztató](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

A névjegyben lévő Grafit-weboldal hivatkozása csak érintésre, a böngészőben nyílik meg. Az alkalmazás nem küld hozzá KRÉTA-adatot; a weboldal látogatására annak saját adatkezelése vonatkozik.

## Google Naptár

A naptárengedély opcionális, az Eszközök naptárnézetében kérhető. A READ_CALENDAR az írható naptárak azonosításához és a Grafit saját exportjainak egyeztetéséhez, a WRITE_CALENDAR ezek létrehozásához, frissítéséhez és külön kérésre törléséhez szükséges.

Bekapcsolt exportnál a kiválasztott kategóriák iskolai eseményeinek címe, dátuma, tananyaga, tanára/terme, illetve a kiválasztott saját események jegyzetei a kijelölt készüléknaptárba kerülnek. Google-naptár esetén az Android Google-szinkronja továbbíthatja ezeket a Google-fiókba. Az export alapból kikapcsolt, a cél és a kategóriák külön választhatók. Bemutató módból nincs valódi export.

A Grafit nem kér Google-jelszót, és nem használ külön Grafit-szervert a naptárszinkronhoz. Más alkalmazás eseményeit nem írja át. A saját exportok azonosításához fiókonként külön, hash-elt azonosítót használ. A Google-fiókba már továbbított adatokra a Google adatkezelése vonatkozik; az alkalmazás törlése nem törli ezeket. A saját exportok külön megerősítéssel távolíthatók el a kijelölt naptárból.

## Mi marad a készüléken?

| Adat | Tárolás és cél |
| --- | --- |
| Munkamenet-tokenek és fiókazonosító | Android Keystore-kulccsal titkosított alkalmazástár; bejelentkezés és tokenmegújítás |
| Iskolai gyorsítótár | Titkosított alkalmazástár; megjelenítés és korábban letöltött adatok elérése hálózati hiba esetén |
| Saját név és profilkép | Fiókonként elkülönített, titkosított helyi profil; a kép legfeljebb 512 × 512 képpontos másolata |
| Értesítésekhez már feldolgozott rekordazonosítók | Titkosított fióktár; az ismételt értesítések elkerülése |
| Saját tervek, próbajegyek, célátlagok, dolgozatjegyzetek és fiókonkénti beállításmentések | Titkosított, fiókonként elkülönített helyi tár; nem kerülnek a KRÉTA-ba |
| Több fiók mentett munkamenete és iskolai gyorsítótára | Titkosított helyi fióktár; új bejelentkezés nélküli váltáshoz |
| Téma, CSS-változók, értesítési kapcsolók és sablonok | Az alkalmazás saját helyi beállítástára; nem külön titkosított beállítások |

Az automatikus Android-felhőmentés és az alkalmazásadatok automatikus eszközátvitele ki van kapcsolva. A Grafit nem tartalmaz reklám- vagy analitikai SDK-t, és nem küld automatikus hibajelentéseket saját szerverre.

## Értesítések és hozzáférések

Az értesítések választhatók; Android 13-tól rendszerengedély is szükséges. Bekapcsoláskor az Android ütemezett háttérfeladata lekéri az új jegyeket, házi feladatokat és bejelentett dolgozatokat. Az élő tanóra a helyi órarendet használja.

A részletes iskolai tartalom az értesítési felületen megjelenhet. Az alkalmazás zárolt képernyőre alapból általános nyilvános szöveget ad; a tényleges láthatóságot az Android és a felhasználó értesítési beállításai is befolyásolják.

A profilképet a rendszer képválasztójával választod ki. Az alkalmazás a kiválasztott képről helyi másolatot készít, nem tölt fel profilképet a KRÉTA-hoz. A hálózatállapot és az újraindítási jelzések az engedélyezett háttérfeladatok működését szolgálják. Nincs hely-, névjegy-, mikrofon- vagy kameraengedély.

A kezdőképernyős widgetek a mentett aktív fiók adatait jelenítik meg. A jegyek widgetbeli megjelenítése külön engedélyezendő, és az általános jegyelrejtés is érvényes. A saját tanulási emlékeztetők értesítési engedélyt használnak; időzítésük az Android energiatakarékosságától függ. A keresés és a jegyszimulátor helyben fut.

## Új iskolai eszközök és dokumentumok

Az órarend-változások a már letöltött napok összehasonlításából készülnek; az első letöltés nem riasztás. A havi összefoglaló, terheléstérkép és pótlási lista helyi számítás, saját szerver nélkül. A pótlási jelölések, igazolási határidők/jegyzetek és teremjelölések külön, fiókonként titkosítva tárolódnak.

Az igazolásképeket/PDF-eket és alaprajzokat a rendszerválasztón keresztül adod hozzá, legfeljebb 8 MB-os fájlonként. A helyi másolat Android Keystore-kulccsal titkosított. Nincs új általános tárhely-, hely- vagy kameraengedély. Ezek a fájlok nem kerülnek a KRÉTA-ba vagy a Google Naptárba. A saját igazolási állapot nem hivatalos beküldés.

Melléklet megnyitásakor a választott külső alkalmazás ideiglenes, csak olvasási hozzáférést kap egy visszafejtett gyorsítótárpéldányhoz. Havi kártyát csak külön kérésre, a kiválasztott adatkategóriákkal generálunk és az Android megosztópaneljén keresztül adhatsz át más alkalmazásnak. Jegyek és hiányzások megosztása alapból kikapcsolt; nincs név, iskola vagy fiókazonosító a kártyán. A fogadó alkalmazásba átadott adatokra annak saját adatkezelése vonatkozik. Az egy óránál régebbi exportpéldányokat a következő exportnál töröljük a privát gyorsítótárból.

A helyi névhez és tanulási adatokhoz hasonlóan a saját dokumentumok és alaprajzok kijelentkezés után is megmaradnak a későbbi visszalépéshez. Melléklet és alaprajz a saját szerkesztőjében törölhető; az összes helyi adat az Android alkalmazásadat-törlésével vagy eltávolítással távolítható el.

## Törlés

Kijelentkezéskor az aktív fiók munkamenete, iskolai gyorsítótára és értesítési alapállapota törlődik; más mentett fiókok megmaradnak. Inaktív fiók a fióklistából elfelejthető; a megjelenített értesítések megszűnnek. A helyi név, profilkép és saját tanulási adatok megmaradnak, hogy ugyanazzal a fiókkal később visszatölthető legyen. Az összes helyi adat, profil és beállítás törléséhez használd az Android **Alkalmazások → Grafit → Tárhely → Adatok törlése** lehetőségét, vagy távolítsd el az alkalmazást.

A Grafit helyi adatainak törlése nem törli az iskola KRÉTA-rendszerében tárolt adatokat.

## Hibajelentések

A GitHub hibajegyei nyilvános repository esetén nyilvánosan olvashatók. Képernyőkép vagy napló beküldése előtt távolítsd el a neveket, iskolai azonosítókat, üzeneteket és tokeneket. Biztonsági kérdésnél kövesd a [biztonsági bejelentés útmutatóját](SECURITY.md).
