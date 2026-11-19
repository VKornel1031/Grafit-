# Attribution

Grafit adapts the KRÉTA OAuth/PKCE flow, endpoint definitions, response field mappings, synchronization and cache-reconciliation behavior, navigation concept, and animation timings and curves from [Firka by QwIT Development and contributors](https://github.com/QwIT-Development/firka), revision `8645e7d4c74c8d0dc051e04d33ca1c8dd5f2ec0b`.

Firka is licensed under Creative Commons Attribution–NonCommercial–ShareAlike 4.0 International. Grafit is distributed under the same license in LICENSE.txt. Commercial use requires separate permission from the relevant rights holders. Grafit's Kotlin implementation and Material 3 interface are new adaptations. Firka artwork, logos, fonts, notification backend, and Firebase configuration are excluded.

Grafit is an independent client. KRÉTA and E-KRÉTA names belong to their respective owners. There is no affiliation or endorsement.


The native port follows `firka/lib/api/client/kreta_client.dart`, `firka/lib/api/token_grant.dart`, `firka/lib/ui/phone/widgets/login_webview.dart`, and `firka_common/lib/core/consts.dart`. Motion is adapted from the router and swipe container, home-feed insertion transitions, timetable transitions, settings toggles, and surprise-grade reveal. Grafit implements its cache with encrypted local snapshots and its motion with Jetpack Compose.
