# Grafit értesítések

Az értesítések alapértelmezetten kikapcsoltak. A **Beállítások → Értesítések** szakaszban engedélyezhetők; Android 13-tól az operációs rendszer engedélye is szükséges. Külön választható az új jegy, a gyengébb jegy, a házi feladat és a bejelentett dolgozat. A cím és az üzenet típusonként módosítható, helyben megmarad. A gyengébb jegy határa 1–4 közül állítható, és egy jegy egyetlen értesítést eredményez.

## Iskolai újdonságok

Az alkalmazás az indítási és kézi szinkron mellett az Android JobScheduler segítségével körülbelül 15 percenként kér frissítést. A háttérfeladat kizárólag a jegyeket, a házi feladatokat és a bejelentett számonkéréseket tölti le; nem indít teljes szinkront vagy órarendletöltést. A munkát az Android újraindítás és alkalmazásfrissítés után is ütemezheti. Kikapcsoláskor a feladat törlődik.

Ez időszakos lekérdezés, nem a KRÉTA hivatalos push szolgáltatása. Hálózat nélkül, energiatakarékos módban és az Android háttérkorlátozásai mellett a kézbesítés késhet. Kényszerített leállítás után az alkalmazást újra meg kell nyitni. A rendszerbeállításokban a hang, rezgés és zárolt képernyős megjelenés külön szabályozható.

Az első sikeres válasz típusonként csendes alapállapotot hoz létre. A már ismert rekordok azonosítói a fiókhoz tartozó, Android Keystore-kulccsal titkosított tárolóban maradnak. Ismételt szinkron vagy alkalmazásújraindítás nem küldi ki újra ugyanazt a rekordot. Kikapcsolt kategóriák sem halmoznak fel értesítési várólistát a későbbi bekapcsoláshoz. Kijelentkezés törli a fiók értesítési alapállapotát és a megjelenített értesítéseket.

A háttérfeladat és az előtér ugyanazt a sorosított KRÉTA-klienst használja. A frissítési token forgatása, a bejelentkezés és kijelentkezés nem versenyez egy másik klienspéldánnyal. Egy sikertelen adatcsoport nem törli a gyorsítótárat és nem akadályozza meg a többi adatcsoport feldolgozását.

Üzenetváltozók: `{tantargy}`, `{datum}`, `{tema}`; jegyeknél `{jegy}` és `{tanar}`; házi feladatnál és dolgozatnál `{feladat}`. A változók egyszer, szövegként helyettesítődnek be. A **Próbaértesítés** valódi iskolai rekord létrehozása nélkül ellenőrzi az aktuális új-jegy-sablont.

## Élő tanóra

A választható, alapból monokróm élő értesítés az elmentett órarend aktuális tanórájának végéig számol vissza. A visszaszámlálást az Android chronometer rajzolja; nem igényel másodpercenkénti adatlekérést vagy állandó foreground service-t. A cím és az üzenet sablonja, a szín, az előrehaladási sáv és az élő állapotsávjelzés kérése külön állítható. Az előrehaladási sáv az értesítés legutóbbi frissítésének állapotát jelzi, a hátralévő idő folyamatosan frissül.

Android 16-tól az alkalmazás kéri az élő értesítés kiemelését, ha ezt a felhasználó engedélyezi. A pillát, a színezés végső megjelenését és a kiemelés jogosultságát az Android szabályozza; a kiemelés nem garantálható. Más esetben normál, csendes folyamatos értesítés jelenik meg.

A tanórák kezdetén és végén helyi, nem pontos AlarmManager-jelzések értékelik újra az órarendet. A kezdési jelzés energiatakarékosság miatt késhet; az értesítés számlálója mindig a tényleges órarendi végidőt használja, és a lejárati idő automatikusan eltávolítja a befejezett órát. Az alkalmazás nem kér pontos-ébresztési engedélyt. Szünetben, ünnepnapon és elmaradt tanóránál nincs aktív tanórai visszaszámlálás. A működéshez korábban letöltött órarend szükséges.

Az élő sablonok változói: `{tantargy}`, `{tanar}`, `{terem}`, `{tema}`, `{veg}`. A zárolt képernyő nyilvános értesítése alapértelmezésben nem tartalmazza a tantárgyat, a jegyet vagy a feladat szövegét.

## Ellenőrzések

JVM-tesztek ellenőrzik az alapállapotot, a duplikációk elkerülését, a fiókváltást, a kategóriákat, a küszöbértéket, a sablonokat, a háttérben használt végpontokat, a részleges hibákat és a tanóra időhatárait. A tényleges háttérütemezés, engedélykérés, értesítésmegjelenés és állapotsávjelzés fizikai Android-eszközön ellenőrizendő; ebben a feladatban emulátor nem futott.
