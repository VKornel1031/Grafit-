# A Grafit használata

## Első indítás

Jelentkezz be a KRÉTA saját oldalán, vagy válaszd a bemutató módot. A bemutató kitalált adatokat használ, és nem igényel fiókot. Az alkalmazás magyar nyelvű és Android-telefonokra készül.

## Órarend és szinkron

Az órarend nyilai egy hetet léptetnek, a napok a kiválasztott héten belül válthatók. Középen a kiválasztott dátum jelenik meg; „Ma” csak a mai napnál látható. A középső gomb megérintése visszavisz a mai napra.

Indításkor az aktuális és következő hét órarendje frissül. A még le nem töltött további hetek megnyitáskor töltődnek be. A **Frissítés** az aktuális és a kiválasztott időszakot is frissíti. A szinkron folyamatjelzője nem mozdítja el a tartalmat. Hiba esetén a mentett adatok megmaradnak, de elavultak lehetnek.

A kezdőlap a budapesti naptári naphoz tartozó, el nem maradt tanórákból választ. Az utolsó tanóra után **Mára nincs több órád.**, tanóra nélküli napon **Ma nincsenek óráid.** jelenik meg. Ezek a jelzések az utoljára letöltött órarenden alapulnak.

## Jegyek és ösztöndíj

A **Jegyek** oldalon a tantárgy megérintésével nyithatók meg az értékelések. A tantárgy átlagába az elérhető, pozitív súlyú numerikus osztályzatok számítanak. A jegy nélküli tantárgynál „—” jelenik meg. Az alkalmazás az azonos tantárgyak különböző névváltozatait és azonosítóit közös sorba egyezteti.

Az **Éves átlag** az értékeléssel rendelkező tantárgyak súlyozott átlagainak számtani átlaga, minden tantárgy azonos súllyal számít. Ez évközi tájékoztatás, nem a bizonyítvány hivatalos átlaga.

A technikumi szakirányú oktatáshoz adott havi ösztöndíjbecslés sávjai:

| Két tizedesre kerekített átlag | Becsült havi összeg |
| --- | --- |
| 2,00–2,99 | 8 000 Ft |
| 3,00–3,99 | 25 000 Ft |
| 4,00–4,49 | 42 000 Ft |
| 4,50–5,00 | 59 000 Ft |

A becslés a jelenlegi átlagot használja. A hivatalos összeg az előző tanév év végi osztályzataitól és a jogosultsági feltételektől függ. Technikumi ágazati alapoktatásban jogosultság esetén havi 8 000 Ft jár, az átlagtól függetlenül. Hiányzó vagy 2,00 alatti átlagnál nincs becslés. [Az alkalmazásban hivatkozott ösztöndíjforrás](https://szfszc.hu/kepzesek/osztondijak).

## Saját profil

A jobb felső profilbuborék felcsúszó menüt nyit. Itt módosítható a megjelenített név és a profilkép, megnyithatók a beállítások, és kijelentkezhetsz.

A helyi profil nem módosítja a KRÉTA-ban tárolt nevedet. Mentése szinkron közben is használható, és fiókonként elkülönül. A kiválasztott képről az alkalmazás legfeljebb 512 × 512 képpontos, középre vágott másolatot tárol. Kijelentkezés után a helyi profil megmarad; az Android alkalmazásadat-törlése vagy az eltávolítás törli.

## Megjelenés

A **Beállítások** módosításai az egész alkalmazásra érvényesek, és helyben mentődnek. Beállítható többek között:

- világos, sötét vagy rendszerhez igazodó téma, Material You és saját színek;
- egyéni jegyszínek, felületek és tiszta fekete háttér;
- kapszula alakú felső eszköztár és alsó navigáció;
- kitöltött, körvonalas vagy árnyékos kártyák, lekerekítés és sűrűség;
- alkalmazásbeli betűméret és betűcsalád;
- látható kezdőlapi elemek, navigációs feliratok és jegyek elrejtése;
- 60 vagy 120 Hz, ha a kijelző támogatja. A tényleges képfrissítést az Android energiatakarékossága is befolyásolhatja.

A színszerkesztő kör alakú színválasztóval, fényerő-, árnyalat- és telítettségcsúszkával is használható. A pontos hexadecimális színkód továbbra is szerkeszthető. A változás csak az **Alkalmazás** gombbal mentődik.

Az **Összes saját szín visszaállítása** gomb a saját felületi és jegyszíneket egyszerre törli, az egyéb CSS-változókat és beállításokat megtartva. A kiválasztott paletta vagy Material You színei lesznek érvényesek. Egyetlen szín külön is visszaállítható a színszerkesztőben.

### Egyéni CSS-témaváltozók

A Grafit natív Compose-felületet használ. A témaszerkesztő a Grafit saját változóit értelmezi; nem általános böngészős CSS-t hajt végre. Egyetlen `:root` blokk használható, `--grafit-` előtagú változókkal. Külső stíluslapok és webes szelektorok nem támogatottak.

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

A színek `#RGB` vagy `#RRGGBB` alakúak. A `radius` mező `px` jelölése natív, sűrűségfüggetlen felületi egységet jelent. A `font-family` értékei `sans`, `serif`, `mono`; a `toolbar` és `navigation` értékei `standard`, `pill`; a `card-style` értékei `filled`, `outlined`, `elevated`. A szerkesztő mentés előtt jelzi a hibás vagy nem támogatott értékeket. A változók teljes listája a szerkesztőben és a [ThemeCss.kt](../app/src/main/java/hu/grafit/ui/ThemeCss.kt) fájlban található.

## Értesítések

Az új jegyek, házi feladatok és bejelentett dolgozatok értesítései, valamint az élő tanórai visszaszámlálás a **Beállítások → Értesítések** menüben kapcsolhatók be. A típusok, a gyengébbjegy-határ, a címek és az üzenetek külön szerkeszthetők. Android 13-tól rendszerengedély is szükséges.

A háttérellenőrzés és az órakezdési jelzés időzítését az Android korlátozhatja. A hang, rezgés és zárolt képernyős megjelenés a rendszerbeállításokban szabályozható. [Részletes útmutató](NOTIFICATIONS.md).

## Frissítés és hibák

A frissítéskereső az újabb stabil GitHub Release-eket figyeli. A letöltés a hivatalos kiadási oldalon, a böngészőből történik; a telepítést az Android erősíti meg.

Eltérő aláírás miatti telepítési hibánál nézd meg az [aláírási útmutatót](SIGNING_MIGRATION.md). Az alkalmazás törlése előtt számolj a helyi profil és beállítások elvesztésével. Más hibánál a [hibajelentésben](https://github.com/VKornel1031/Grafit-/issues) add meg a verziót és a reprodukció lépéseit, személyes adatok nélkül.
