# Grafit 2.0 – iskolai eszközök

Az új havi történet, órarend-változások, pótlás, terheléstérkép, igazoláskövető és teremkereső használata: [új iskolai eszközök](SCHOOL_EXTRAS.md).

A jegyszimulátor a **Jegyek / Tantárgyak → Mi lenne, ha?** kártyáról nyitható meg. A többi eszköz a jobb felső profilképre koppintva, az **Eszközök / Egyéb** menüpontban érhető el. Az eszközkártyák egységesen kitöltik a rendelkezésre álló szélességet. A tanulási adatok, próbajegyek, célátlagok és felkészülési jegyzetek fiókonként elkülönítve, Android Keystore-kulccsal titkosítva maradnak a készüléken.

## Tantárgyak és jegygrafikon

A Jegyek lapon egy tantárgyra koppintva külön részletes oldal nyílik. Felül a név és az átlag, alatta az 1–5 közötti számszerű jegyek időrendi grafikonja, a **Mi lenne, ha?** gomb és az összes jegy látható. A szöveges értékelések a listában maradnak, de a grafikonra nem kerülnek. Rejtett jegyeknél a grafikont külön kell felfedni. A tantárgyoldalról megnyitott szimulátor eleve az adott tárgyat választja ki; visszalépéskor a tantárgyoldalra jutsz.

## Mi lenne, ha? – jegyszimulátor

Válassz egy tantárgyat, próbajegyet és súlyt, majd add hozzá a számításhoz. Megjelenik az eredeti és a próbált tantárgyi átlag, az éves átlag és az ösztöndíjbecslés. A próbajegyek külön törölhetők, vagy egyszerre üríthetők. Nem kerülnek a KRÉTA-ba, és nem módosítják az iskolai jegyeket.

A célátlag csúszkájával kiszámítható, hány további, a kiválasztott értékű és súlyú jegy kellene. Az elérhetetlen célokat külön jelzi. A tantárgyi célok és próbajegyek helyben megmaradnak. A becsült ösztöndíj továbbra sem hivatalos jogosultsági döntés: a szabályok és az előző tanév eredménye számítanak.

## Tanulási terv

Heti nézetben saját feladatokat, tanulási blokkokat és eseményeket hozhatsz létre. Cím, tantárgy, dátum, opcionális időpont, időtartam és jegyzet adható meg. A tételek szerkeszthetők, törölhetők és készre jelölhetők.

A heti KRÉTA-határidőknél a **Tanulást tervezek hozzá** gomb a feladat szövegével előkészített saját tanulási blokkot nyit. Ez nem jelenti az iskolai házi feladat beküldését vagy a KRÉTA teljesítési állapotának módosítását.

Az emlékeztető kikapcsolható, illetve kezdésre, 15 perccel vagy 1 órával korábbra állítható. Időpont nélküli tételnél 08:00 az alap. Értesítési engedély szükséges. Az Android energiatakarékossága késleltetheti a jelzést; a Grafit nem kér pontos-ébresztési engedélyt. Teljesítés és törlés után az emlékeztető újraütemezésekor megszűnik.

## Statisztikák

Havi súlyozott átlagok, tantárgyi átlagok, erősebb és fejlesztendő tárgyak, jegyeloszlás, havi hiányzások és összesített késés látható. A számítások a mentett adatokon alapulnak. Rejtett jegyek esetén a jegystatisztika és a szimulátor külön érintéssel fedhető fel.

## Naptár

Nap-, hét- és hónapnézet kapcsolható. A hónaprács jelöli a mentett eseményeket. Az adott időszak órái, házi feladatai, dolgozatai és saját tervei közös időrendben látszanak. A **Saját esemény** gombbal új helyi tétel adható hozzá; az esemény részleteiből a saját tétel szerkeszthető.

A naptár nem tölti le egyszerre a teljes tanévet. A **Kijelölt hét letöltése** gomb az órarend meglévő heti lekérését használja. Üres nézet azt jelenti, hogy az időszakra nincs helyben mentett esemény, nem feltétlenül azt, hogy nincs tanítás.

### Szinkron Google Naptárba

1. A telefon Android-fiókbeállításaiban legyen hozzáadva a Google-fiók, bekapcsolt naptárszinkronnal.
2. A Grafit naptárában kérj naptárengedélyt. A lista az írható készüléknaptárakat mutatja; a Google-naptárak külön jelöltek.
3. Válaszd ki a célként használt naptárat és az exportálandó kategóriákat.
4. Kapcsold be az **Automatikus export engedélyezése** lehetőséget, vagy utána használd a **Szinkronizálás most** gombot.

A Grafit az Android Calendar Providerbe ír. Google-fiókhoz tartozó naptár esetén a Google rendszerbeli szinkronizálója továbbítja az eseményeket a felhőbe. Nem kell külön Grafit–Google jelszó vagy fejlesztői OAuth-kulcs. Ha nincs írható naptár, előbb a rendszerben állítsd be a Google Naptárt. Helyi vagy más szolgáltatóhoz tartozó naptár választása önmagában nem továbbít adatot a Google-nek.

Az export **egyirányú**, a következő 90 nap elérhető, mentett adataira vonatkozik. A Google Naptárban végzett szerkesztések nem írnak vissza a KRÉTA-ba vagy a Grafit saját terveibe. Az iskolai címek, határidők, tananyag, tanár/terem és a kiválasztott saját események jegyzetei a választott naptárba kerülnek. Csak az engedélyezett kategóriák exportálódnak.

A Grafit fiókonként saját eseményazonosítókat használ, ezért az ismételt frissítés nem hoz létre újabb másolatot. A változatlan eseményeket nem írja újra. Más alkalmazás vagy a felhasználó saját eseményeit nem módosítja. A letöltetlen órarendi heteket nem kezeli törölt órákként. Az iskolai feladatok háttérbeli frissítése körülbelül 15 percenként ütemezhető; az órarend továbbra is indításkor, kézi frissítéskor vagy hétbetöltéskor frissül.

Az automatikus export kikapcsolása a már exportált eseményeket megtartja. A **Grafit-események eltávolítása ebből a naptárból** gomb külön megerősítés után törli az adott Grafit-fiók exportjait, és kikapcsolja az exportot. Naptárváltás előtt szükség esetén így távolítsd el a korábbi cél eseményeit. Az alkalmazás eltávolítása önmagában nem törli a Google Naptárba már továbbított eseményeket.

Bemutató módból valódi naptárexport nem indul. A cloud továbbítás sebességét a Google-fiók szinkronbeállítása, a hálózat és az Android energiatakarékossága befolyásolja.

## Több fiók

A **Fiókok** oldalon új KRÉTA-fiók adható hozzá, illetve a mentett fiókok közt új bejelentkezés nélkül lehet váltani. A külön munkamenetek, gyorsítótárak és értesítési alapállapotok titkosítva tárolódnak. A saját profil, téma, értesítési beállítások, tervek és jegyzetek fiókonként elkülönülnek.

A váltás a mentett adatokat azonnal, hálózat nélkül is visszaállítja; a **Frissítés** gombbal kérhetők új iskolai adatok. A háttérértesítés, a widget és az automatikus naptárexport az aktív fiókot használja. A bejelentkezési képernyőn a további mentett fiókok is kiválaszthatók.

A **Kijelentkezés** az aktív fiók munkamenetét és iskolai gyorsítótárát eltávolítja, a többi mentett fiókot megtartja. Inaktív fiók az **Elfelejtés** gombbal távolítható el a fióklistából. A helyi profil és tanulási tervek a későbbi visszalépéshez megmaradnak; az összes helyi adat az Android alkalmazásadat-törlésével távolítható el.

## Keresés

Tantárgyak, tanárok, órák tananyaga, jegyek, üzenetek, feladatok, hiányzások, saját tervek és dolgozatjegyzetek kereshetők. A keresés ékezet nélkül is működik, csak helyben fut, és legfeljebb 300 találatot jelenít meg. Pontosítsd a keresőkifejezést, ha túl sok a találat. A találatokból a részletes üzenet, tanóra vagy saját terv megnyitható.

Rejtett jegyeknél a jegyérték nem szerepel a találati címben és nem használható a jegyre történő szűréshez. A feladatok és órák eredeti szövegében előforduló számok természetesen kereshetők.

## Dolgozatfelkészülés

Válassz egy letöltött bejelentett dolgozatot. Saját jegyzet menthető hozzá, tanulási blokk ütemezhető, és készre jelölhető ellenőrzőlista adható hozzá. A kapcsolódó tárgy utolsó 30 napjának letöltött tananyaga külön listában segíti az ismétlést. Ez tárgy- és dátumalapú gyűjtés, nem a dolgozat hivatalos témakörének automatikus meghatározása.

## Android-widgetek

Négy külön widget érhető el: **Mai órarend**, **Következő tanóra**, **Feladatok**, **Legutóbbi jegyek**. Az **Eszközök → Widgetek** oldalon a támogatott indítókon közvetlen hozzáadási gombok is megjelennek. Egyébként tartsd nyomva az Android kezdőképernyőjét, és a rendszer Widgetek menüjében keresd a Grafitot.

A widgetek a mentett, aktív fiók adatait jelenítik meg. Az órarendi határok és az időszakos rendszerfrissítés újrarajzolhatják őket; a rendszer ezt késleltetheti. A widget frissítője csak a helyi adatokat olvassa újra. Új hálózati adatokhoz nyisd meg a Grafitot vagy használd annak Frissítés gombját.

A jegyek alapból rejtve vannak. Megjelenítésük külön engedélyezhető a Widgetek oldalon, de az alkalmazás általános jegyelrejtése ezt is felülírja. A widget a kezdőképernyőn mások számára is látható lehet. A megjelenés alapból monokróm, és a saját elsődleges témaszínt használja kiemelésként.

## Ellenőrzés

A számításokat, a tárolási formátumot, a keresést, az események stabil azonosítását, a törlési jogosultsági szabályokat és a fiókváltást JVM-tesztek ellenőrzik. A natív UI- és titkosított tárolási tesztek fordíthatók. A Google-fiókhoz továbbítást, a rendszerengedélyeket, a kezdőképernyős widgeteket és az időzített emlékeztetőket fizikai Android-eszközön is ellenőrizni kell. Emulátor ebben a feladatban nem futott.
