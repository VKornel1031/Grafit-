# Adatkezelési tájékoztató

Utolsó frissítés: 2026. november 21.

Ez a dokumentum a Grafit jelenlegi Android-alkalmazásának adatkezelését írja le. A Grafit független kliens, nem a KRÉTA üzemeltetőjének alkalmazása.

## Bejelentkezés és hálózati kapcsolatok

A bejelentkezés a KRÉTA HTTPS-oldalán, Android WebView-ban történik. Az alkalmazás OAuth-kódcserét és PKCE-t használ. Nem készít saját mentést a KRÉTA-jelszóról; a visszakapott hozzáférési és frissítési tokeneket a munkamenet fenntartására használja. Intézményi azonosításnál az iskola külső bejelentkezési szolgáltatása is megnyílhat.

Az iskolai adatok közvetlenül a KRÉTA szolgáltatásaitól érkeznek. Nincs külön Grafit-fiókrendszer vagy az iskolai adatokat fogadó Grafit-szerver. A KRÉTA és az intézményi azonosítószolgáltató adatkezelésére a saját tájékoztatóik vonatkoznak.

A frissítéskereső indításkor és kézi ellenőrzéskor a GitHub nyilvános kiadási API-ját használja. Nem küld iskolai adatokat vagy KRÉTA-tokent a GitHubnak. A hálózati szolgáltatások a kapcsolat részeként megkapják az IP-címet és a szokásos kérési adatokat. Frissítés letöltésekor a GitHub oldala a böngészőben nyílik meg. [GitHub adatkezelési tájékoztató](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

A névjegyben lévő Grafit-weboldal hivatkozása csak érintésre, a böngészőben nyílik meg. Az alkalmazás nem küld hozzá KRÉTA-adatot; a weboldal látogatására annak saját adatkezelése vonatkozik.

## Mi marad a készüléken?

| Adat | Tárolás és cél |
| --- | --- |
| Munkamenet-tokenek és fiókazonosító | Android Keystore-kulccsal titkosított alkalmazástár; bejelentkezés és tokenmegújítás |
| Iskolai gyorsítótár | Titkosított alkalmazástár; megjelenítés és korábban letöltött adatok elérése hálózati hiba esetén |
| Saját név és profilkép | Fiókonként elkülönített, titkosított helyi profil; a kép legfeljebb 512 × 512 képpontos másolata |
| Értesítésekhez már feldolgozott rekordazonosítók | Titkosított fióktár; az ismételt értesítések elkerülése |
| Téma, CSS-változók, értesítési kapcsolók és sablonok | Az alkalmazás saját helyi beállítástára; nem külön titkosított beállítások |

Az automatikus Android-felhőmentés és az alkalmazásadatok automatikus eszközátvitele ki van kapcsolva. A Grafit nem tartalmaz reklám- vagy analitikai SDK-t, és nem küld automatikus hibajelentéseket saját szerverre.

## Értesítések és hozzáférések

Az értesítések választhatók; Android 13-tól rendszerengedély is szükséges. Bekapcsoláskor az Android ütemezett háttérfeladata lekéri az új jegyeket, házi feladatokat és bejelentett dolgozatokat. Az élő tanóra a helyi órarendet használja.

A részletes iskolai tartalom az értesítési felületen megjelenhet. Az alkalmazás zárolt képernyőre alapból általános nyilvános szöveget ad; a tényleges láthatóságot az Android és a felhasználó értesítési beállításai is befolyásolják.

A profilképet a rendszer képválasztójával választod ki. Az alkalmazás a kiválasztott képről helyi másolatot készít, nem tölt fel profilképet a KRÉTA-hoz. A hálózatállapot és az újraindítási jelzések az engedélyezett háttérfeladatok működését szolgálják. Nincs hely-, névjegy-, mikrofon- vagy kameraengedély.

## Törlés

Kijelentkezéskor törlődik a munkamenet, az iskolai gyorsítótár és a fiók értesítési alapállapota; a megjelenített értesítések megszűnnek. A helyi név és profilkép megmarad, hogy ugyanazzal a fiókkal később visszatölthető legyen. Az összes helyi adat, profil és beállítás törléséhez használd az Android **Alkalmazások → Grafit → Tárhely → Adatok törlése** lehetőségét, vagy távolítsd el az alkalmazást.

A Grafit helyi adatainak törlése nem törli az iskola KRÉTA-rendszerében tárolt adatokat.

## Hibajelentések

A GitHub hibajegyei nyilvános repository esetén nyilvánosan olvashatók. Képernyőkép vagy napló beküldése előtt távolítsd el a neveket, iskolai azonosítókat, üzeneteket és tokeneket. Biztonsági kérdésnél kövesd a [biztonsági bejelentés útmutatóját](SECURITY.md).
